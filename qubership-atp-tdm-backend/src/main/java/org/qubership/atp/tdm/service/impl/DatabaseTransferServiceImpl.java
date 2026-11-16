/*
 *  Copyright 2024-2025 NetCracker Technology Corporation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.qubership.atp.tdm.service.impl;

import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipException;

import javax.sql.DataSource;

import org.qubership.atp.tdm.exceptions.transfer.DatabaseTransferException;
import org.qubership.atp.tdm.model.transfer.TransferState;
import org.qubership.atp.tdm.model.transfer.TransferStatus;
import org.qubership.atp.tdm.service.ApplicationRestarter;
import org.qubership.atp.tdm.service.DatabaseExport;
import org.qubership.atp.tdm.service.DatabaseTransferService;
import org.qubership.atp.tdm.utils.H2Url;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.unit.DataSize;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class DatabaseTransferServiceImpl implements DatabaseTransferService {

    private static final DateTimeFormatter FILE_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    private static final String CONFIRMATION = "REPLACE_ALL_DATA";
    /** The restored database is about this many times the size of the dump, with a margin over the measured 4.6. */
    private static final int RESTORED_SIZE_FACTOR = 5;

    private final DataSource dataSource;
    private final String jdbcUrl;
    private final String serviceVersion;
    private final TransferLayout layout;
    private final TransferStatusStore statusStore;
    private final long maxUploadBytes;
    private final ApplicationRestarter restarter;
    private final Semaphore exportSlot = new Semaphore(1);
    private final ReentrantLock importLock = new ReentrantLock();

    /**
     * Creates the service.
     *
     * @param dataSource     the pool of the database to export
     * @param jdbcUrl        the URL of that database, which decides whether the transfer is possible
     * @param serviceVersion the version that the dump header records
     * @param stagingDir     the configured staging directory, empty for {@code transfer} next to the database
     * @param maxUpload      the largest compressed dump that an upload may carry, as a Spring data size
     * @param restarter      ends the process after an approval that asks for a restart
     */
    @Autowired
    public DatabaseTransferServiceImpl(DataSource dataSource,
                                       @Value("${jdbc.Url}") String jdbcUrl,
                                       @Value("${project.version}") String serviceVersion,
                                       @Value("${tdm.db.transfer.staging-dir:}") String stagingDir,
                                       @Value("${tdm.db.transfer.max-size:100MB}") String maxUpload,
                                       ApplicationRestarter restarter) {
        this.dataSource = dataSource;
        this.jdbcUrl = jdbcUrl;
        this.serviceVersion = serviceVersion;
        this.maxUploadBytes = DataSize.parse(maxUpload).toBytes();
        this.restarter = restarter;
        if (H2Url.isFileDatabase(jdbcUrl)) {
            this.layout = TransferLayout.of(jdbcUrl, stagingDir);
            this.statusStore = new TransferStatusStore(layout.statusFile());
        } else {
            this.layout = null;
            this.statusStore = null;
        }
    }

    private void requireFileDatabase() {
        if (layout == null) {
            throw new DatabaseTransferException(HttpStatus.CONFLICT,
                    "The database transfer needs an H2 file database, and jdbc.Url does not point to one.");
        }
    }

    @Override
    public TransferStatus stageImport(InputStream dump, long contentLength) {
        requireFileDatabase();
        long declared = contentLength >= 0 ? contentLength : maxUploadBytes;
        if (declared > maxUploadBytes) {
            throw new DatabaseTransferException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "The dump is larger than the limit of " + maxUploadBytes + " bytes (tdm.db.transfer.max-size).");
        }
        if (!importLock.tryLock()) {
            throw new DatabaseTransferException(HttpStatus.CONFLICT, "Another import operation is running.");
        }
        try {
            checkFreeSpace(declared);
            return stage(dump);
        } finally {
            importLock.unlock();
        }
    }

    private void checkFreeSpace(long uploadBytes) {
        try {
            Files.createDirectories(layout.stagingDir());
            long usable = Files.getFileStore(layout.stagingDir()).getUsableSpace();
            long needed = uploadBytes + RESTORED_SIZE_FACTOR * uploadBytes;
            if (usable < needed) {
                throw new DatabaseTransferException(HttpStatus.INSUFFICIENT_STORAGE,
                        "The volume of the staging directory has " + usable + " bytes free, and the import needs "
                                + needed + " bytes.");
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot prepare the staging directory " + layout.stagingDir(), e);
        }
    }

    private TransferStatus stage(InputStream dump) {
        TransferStatus current = statusStore.read();
        if (current.getState() == TransferState.APPLYING) {
            throw new DatabaseTransferException(HttpStatus.CONFLICT, "A restore is running.");
        }
        Path part = layout.incoming();
        MessageDigest digest = sha256();
        DumpValidator.DumpHeader header;
        try {
            header = writeValidated(dump, part, digest);
        } catch (DumpValidationException e) {
            deleteQuietly(part);
            throw new DatabaseTransferException(HttpStatus.BAD_REQUEST, "Invalid dump: " + e.getMessage());
        } catch (LimitExceededException e) {
            deleteQuietly(part);
            throw new DatabaseTransferException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "The dump is larger than the limit of " + maxUploadBytes + " bytes (tdm.db.transfer.max-size).");
        } catch (ZipException | EOFException | CharacterCodingException e) {
            deleteQuietly(part);
            throw new DatabaseTransferException(HttpStatus.BAD_REQUEST,
                    "The upload is not a complete gzip-compressed UTF-8 text: " + e.getMessage());
        } catch (IOException e) {
            deleteQuietly(part);
            throw new UncheckedIOException("Cannot store the uploaded dump", e);
        }
        List<String> warnings;
        try {
            warnings = DumpVersions.check(header.serviceVersion(), serviceVersion);
        } catch (DatabaseTransferException e) {
            deleteQuietly(part);
            throw e;
        }
        try {
            long size = Files.size(part);
            Files.move(part, layout.pending(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            TransferStatus status = new TransferStatus();
            status.setState(TransferState.STAGED);
            status.setUploadedAt(Instant.now().truncatedTo(ChronoUnit.SECONDS));
            status.setSizeBytes(size);
            status.setSha256(HexFormat.of().formatHex(digest.digest()));
            status.setDumpServiceVersion(header.serviceVersion());
            status.setDumpH2Version(header.h2Version());
            status.setWarnings(new ArrayList<>(warnings));
            statusStore.write(status);
            log.info("Staged a database dump from service {} with H2 {}: {} bytes, SHA-256 {}",
                    header.serviceVersion(), header.h2Version(), size, status.getSha256());
            return status;
        } catch (IOException e) {
            deleteQuietly(part);
            throw new UncheckedIOException("Cannot store the uploaded dump", e);
        }
    }

    /**
     * Reads the upload once: counts its bytes against the limit, decompresses it, validates it, and writes the
     * accepted statements as a new gzip file whose SHA-256 the digest receives.
     */
    private DumpValidator.DumpHeader writeValidated(InputStream upload, Path target, MessageDigest digest)
            throws IOException {
        try (InputStream limited = new LimitedInputStream(upload, maxUploadBytes);
             Reader reader = new InputStreamReader(new GZIPInputStream(limited, 64 * 1024),
                     StandardCharsets.UTF_8.newDecoder());
             OutputStream file = new DigestOutputStream(new BufferedOutputStream(Files.newOutputStream(target)),
                     digest);
             Writer writer = new OutputStreamWriter(new GZIPOutputStream(file, 64 * 1024), StandardCharsets.UTF_8)) {
            return DumpValidator.validate(reader, writer, true);
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("The JVM has no SHA-256", e);
        }
    }

    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("Cannot delete {}: {}", file, e.getMessage());
        }
    }

    @Override
    public TransferStatus importStatus() {
        requireFileDatabase();
        return statusStore.read();
    }

    @Override
    public void cancelImport() {
        requireFileDatabase();
        if (!importLock.tryLock()) {
            throw new DatabaseTransferException(HttpStatus.CONFLICT, "Another import operation is running.");
        }
        try {
            TransferState state = statusStore.read().getState();
            if (state == TransferState.STAGED || state == TransferState.APPROVED) {
                Files.deleteIfExists(layout.pending());
                statusStore.write(new TransferStatus());
                log.info("Cancelled the {} database dump", state);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot delete the staged dump", e);
        } finally {
            importLock.unlock();
        }
    }

    @Override
    public TransferStatus approveImport(String confirm, boolean restart) {
        requireFileDatabase();
        if (!CONFIRMATION.equals(confirm)) {
            throw new DatabaseTransferException(HttpStatus.BAD_REQUEST,
                    "Pass confirm=" + CONFIRMATION + " to replace all data of this service.");
        }
        if (!importLock.tryLock()) {
            throw new DatabaseTransferException(HttpStatus.CONFLICT, "Another import operation is running.");
        }
        try {
            TransferStatus status = statusStore.read();
            boolean waiting = status.getState() == TransferState.STAGED
                    || status.getState() == TransferState.APPROVED;
            if (!waiting || !Files.exists(layout.pending())) {
                throw new DatabaseTransferException(HttpStatus.CONFLICT, "No dump is staged.");
            }
            status.setState(TransferState.APPROVED);
            status.setApprovedAt(Instant.now().truncatedTo(ChronoUnit.SECONDS));
            statusStore.write(status);
            log.warn("The database dump with SHA-256 {} is approved. The next start of the service replaces all data.",
                    status.getSha256());
            if (restart) {
                restarter.restartSoon();
            }
            return status;
        } finally {
            importLock.unlock();
        }
    }

    @Override
    public DatabaseExport beginExport() {
        if (!H2Url.isFileDatabase(jdbcUrl)) {
            throw new DatabaseTransferException(HttpStatus.CONFLICT,
                    "The database transfer needs an H2 file database, and jdbc.Url does not point to one.");
        }
        if (!exportSlot.tryAcquire()) {
            throw new DatabaseTransferException(HttpStatus.CONFLICT, "Another export is already running.");
        }
        return new Export(H2Url.databaseName(jdbcUrl), Instant.now().truncatedTo(ChronoUnit.SECONDS));
    }

    /** Signals that an upload carries more bytes than the limit. */
    private static final class LimitExceededException extends IOException {
        LimitExceededException() {
            super("The upload is over the size limit.");
        }
    }

    /** Fails the read that passes the limit, so that a larger upload is never read to its end. */
    private static final class LimitedInputStream extends FilterInputStream {

        private final long limit;
        private long count;

        LimitedInputStream(InputStream in, long limit) {
            super(in);
            this.limit = limit;
        }

        @Override
        public int read() throws IOException {
            int value = super.read();
            if (value >= 0) {
                add(1);
            }
            return value;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            int read = super.read(bytes, offset, length);
            if (read > 0) {
                add(read);
            }
            return read;
        }

        private void add(long bytes) throws IOException {
            count += bytes;
            if (count > limit) {
                throw new LimitExceededException();
            }
        }

        @Override
        public void close() {
            // The servlet container owns the request stream.
        }
    }

    private final class Export implements DatabaseExport {

        private final String databaseName;
        private final Instant createdAt;
        private final AtomicBoolean released = new AtomicBoolean();

        private Export(String databaseName, Instant createdAt) {
            this.databaseName = databaseName;
            this.createdAt = createdAt;
        }

        @Override
        public String fileName() {
            return "tdm-" + databaseName + "-" + FILE_TIMESTAMP.format(createdAt) + ".sql.gz";
        }

        @Override
        public void writeTo(OutputStream target) throws IOException {
            try (Connection connection = dataSource.getConnection()) {
                // SCRIPT reads each table at its own point in time under the default isolation level. A
                // repeatable-read transaction gives every table the same snapshot and does not block writers.
                connection.setAutoCommit(false);
                connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
                try {
                    writeDump(connection, target);
                } finally {
                    connection.rollback();
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Cannot export the database: " + e.getMessage(), e);
            }
            log.info("Exported database '{}' as {}", databaseName, fileName());
        }

        private void writeDump(Connection connection, OutputStream target) throws SQLException, IOException {
            OutputStream keepOpen = new FilterOutputStream(target) {
                @Override
                public void write(byte[] bytes, int offset, int length) throws IOException {
                    out.write(bytes, offset, length);
                }

                @Override
                public void close() throws IOException {
                    flush();
                }
            };
            try (Writer writer = new OutputStreamWriter(new GZIPOutputStream(keepOpen), StandardCharsets.UTF_8);
                 Statement statement = connection.createStatement();
                 ResultSet rows = statement.executeQuery("SCRIPT NOPASSWORDS")) {
                writer.write(header(connection));
                while (rows.next()) {
                    String statementText = rows.getString(1);
                    writer.write(statementText);
                    if (!statementText.endsWith(";")) {
                        writer.write(';');
                    }
                    writer.write('\n');
                }
            }
        }

        private String header(Connection connection) throws SQLException {
            String h2Version = connection.getMetaData().getDatabaseProductVersion().split(" ")[0];
            return "-- TDM3-DUMP v1 service=" + serviceVersion + " h2=" + h2Version + " created=" + createdAt
                    + " database=" + databaseName + "\n";
        }

        @Override
        public void close() {
            if (released.compareAndSet(false, true)) {
                exportSlot.release();
            }
        }
    }
}

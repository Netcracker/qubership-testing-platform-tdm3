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

package org.qubership.atp.tdm.configuration;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

import org.qubership.atp.tdm.model.transfer.TransferState;
import org.qubership.atp.tdm.model.transfer.TransferStatus;
import org.qubership.atp.tdm.service.impl.DumpValidator;
import org.qubership.atp.tdm.service.impl.TransferLayout;
import org.qubership.atp.tdm.service.impl.TransferStatusStore;
import org.qubership.atp.tdm.utils.H2Url;

import lombok.extern.slf4j.Slf4j;

/**
 * Replaces the database files with an approved dump while the service starts.
 *
 * <p>{@link DbConfiguration#dataSource} calls it before it creates the connection pool, so nothing else has opened
 * the database. It acts on an {@code APPROVED} dump, and on an {@code APPLYING} status, which a crashed restore
 * leaves behind. A failed restore puts the previous database back, records {@code FAILED}, and lets the service
 * start with the old data.</p>
 */
@Slf4j
public class DatabaseRestoreInitializer {

    /** The file that tells a rollback that every database file has already moved to the backup directory. */
    private static final String MOVED_MARKER = "MOVED";
    private static final DateTimeFormatter DIRECTORY_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);
    private static final List<String> REQUIRED_TABLES = List.of("DATABASECHANGELOG", "TEST_DATA_TABLE_CATALOG");

    private final String jdbcUrl;
    private final String user;
    private final String password;
    private final int backupsToKeep;
    private final TransferLayout layout;
    private final TransferStatusStore store;

    /**
     * Creates the initializer.
     *
     * @param jdbcUrl       the URL of the service database, which the restore opens to create the new database
     * @param user          the database user of the service, who owns the new database
     * @param password      the password of that user
     * @param stagingDir    the configured staging directory, empty for {@code transfer} next to the database
     * @param backupsToKeep the number of backup directories that stay after a successful restore
     */
    public DatabaseRestoreInitializer(String jdbcUrl, String user, String password, String stagingDir,
                                      int backupsToKeep) {
        this.jdbcUrl = jdbcUrl;
        this.user = user;
        this.password = password;
        this.backupsToKeep = Math.max(backupsToKeep, 0);
        if (H2Url.isFileDatabase(jdbcUrl)) {
            this.layout = TransferLayout.of(jdbcUrl, stagingDir);
            this.store = new TransferStatusStore(layout.statusFile());
        } else {
            this.layout = null;
            this.store = null;
        }
    }

    /**
     * Restores the database when a dump is approved. Does nothing for a database that is not an H2 file database.
     *
     * @throws IllegalStateException when a failed restore cannot put the previous database back, because the service
     *                               must not start on files in an unknown state
     */
    public void restoreIfApproved() {
        if (layout == null) {
            return;
        }
        TransferStatus status;
        try {
            Files.deleteIfExists(layout.incoming());
            status = store.read();
        } catch (IOException | RuntimeException e) {
            log.error("Cannot read the database transfer status, so no import runs: {}", e.getMessage());
            return;
        }
        if (status.getState() == TransferState.APPLYING) {
            recoverFromCrash(status);
        } else if (status.getState() == TransferState.APPROVED) {
            apply(status);
        }
    }

    private void recoverFromCrash(TransferStatus status) {
        log.error("The previous database restore did not finish. Restoring the previous database.");
        rollBack(status.getBackup());
        fail(status, "The restore was interrupted, and the previous database was put back.");
    }

    private void apply(TransferStatus status) {
        Path pending = layout.pending();
        try {
            verify(pending, status.getSha256());
        } catch (IOException | RuntimeException e) {
            log.error("The approved dump is not usable, so the import does not run: {}", e.getMessage());
            fail(status, "The approved dump is not usable: " + e.getMessage());
            return;
        }
        String backupName = DIRECTORY_TIMESTAMP.format(Instant.now());
        status.setState(TransferState.APPLYING);
        status.setBackup(backupName);
        store.write(status);
        log.warn("Replacing the database with the approved dump. The previous files go to {}",
                layout.backupRoot().resolve(backupName));
        try {
            moveDatabaseAside(layout.backupRoot().resolve(backupName));
            restore(pending);
        } catch (IOException | SQLException | RuntimeException e) {
            log.error("The database restore failed: {}", e.getMessage(), e);
            rollBack(backupName);
            fail(status, "The restore failed and the previous database was put back: " + e.getMessage());
            return;
        }
        succeed(status);
    }

    private void verify(Path pending, String expectedHash) throws IOException {
        MessageDigest digest = sha256();
        try (InputStream hashing = new DigestInputStream(Files.newInputStream(pending), digest)) {
            hashing.transferTo(OutputStream.nullOutputStream());
        }
        if (!HexFormat.of().formatHex(digest.digest()).equals(expectedHash)) {
            throw new IOException("The staged dump changed after it was validated.");
        }
        try (Reader reader = new InputStreamReader(new GZIPInputStream(Files.newInputStream(pending), 64 * 1024),
                StandardCharsets.UTF_8.newDecoder())) {
            DumpValidator.validate(reader, null, false);
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("The JVM has no SHA-256", e);
        }
    }

    private void moveDatabaseAside(Path backup) throws IOException {
        Files.createDirectories(backup);
        for (Path file : layout.databaseFiles()) {
            Files.move(file, backup.resolve(file.getFileName()));
        }
        Files.writeString(backup.resolve(MOVED_MARKER), "");
    }

    private void restore(Path pending) throws SQLException {
        try (Connection connection = DriverManager.getConnection(jdbcUrl, user, password)) {
            try {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("RUNSCRIPT FROM '" + pending.toAbsolutePath().toString().replace("'", "''")
                            + "' COMPRESSION GZIP CHARSET 'UTF-8'");
                }
                connection.setAutoCommit(false);
                clearSchedulerState(connection);
                requireTables(connection);
                connection.commit();
            } finally {
                shutDown(connection);
            }
        }
    }

    /**
     * Removes what the process that wrote the dump was doing, and keeps what it was scheduled to do. See the
     * design page, section "Scheduler state".
     */
    private void clearSchedulerState(Connection connection) throws SQLException {
        deleteAll(connection, "QRTZ_FIRED_TRIGGERS");
        deleteAll(connection, "QRTZ_SCHEDULER_STATE");
        String triggers = tableName(connection, "QRTZ_TRIGGERS");
        if (triggers != null) {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("UPDATE " + triggers + " SET TRIGGER_STATE = 'WAITING' "
                        + "WHERE TRIGGER_STATE IN ('ACQUIRED', 'BLOCKED')");
                statement.executeUpdate("UPDATE " + triggers + " SET TRIGGER_STATE = 'PAUSED' "
                        + "WHERE TRIGGER_STATE = 'PAUSED_BLOCKED'");
            }
        }
        deleteAll(connection, "SHEDLOCK");
    }

    private void deleteAll(Connection connection, String table) throws SQLException {
        String name = tableName(connection, table);
        if (name != null) {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("DELETE FROM " + name);
            }
        }
    }

    private void requireTables(Connection connection) throws SQLException {
        for (String table : REQUIRED_TABLES) {
            if (tableName(connection, table) == null) {
                throw new SQLException("The restored database has no table " + table + ".");
            }
        }
    }

    /**
     * Finds a table of the current schema by a case-insensitive name.
     *
     * @return the name quoted as the database spells it, or {@code null} when the table does not exist, as in a dump
     *         from a version that predates the table
     */
    private String tableName(Connection connection, String table) throws SQLException {
        String sql = "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES "
                + "WHERE TABLE_SCHEMA = SCHEMA() AND UPPER(TABLE_NAME) = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, table);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? '"' + rows.getString(1).replace("\"", "\"\"") + '"' : null;
            }
        }
    }

    /** Closes the database for good, even where the URL keeps it open after the last connection. */
    private void shutDown(Connection connection) {
        try (Statement statement = connection.createStatement()) {
            statement.execute("SHUTDOWN");
        } catch (SQLException e) {
            log.warn("Cannot shut down the database after the restore: {}", e.getMessage());
        }
    }

    private void rollBack(String backupName) {
        try {
            Path backup = backupName == null ? null : layout.backupRoot().resolve(backupName);
            if (backup == null || !Files.isDirectory(backup)) {
                // The database files never moved, so the files in place are the previous database.
                return;
            }
            if (Files.exists(backup.resolve(MOVED_MARKER))) {
                for (Path file : layout.databaseFiles()) {
                    Files.delete(file);
                }
            }
            try (Stream<Path> files = Files.list(backup)) {
                for (Path file : files.toList()) {
                    if (!file.getFileName().toString().equals(MOVED_MARKER)) {
                        Files.move(file, layout.databaseDir().resolve(file.getFileName()),
                                StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
            deleteTree(backup);
            log.info("Put the previous database back from {}", backup);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot put the previous database back. The service does not start "
                    + "on files in an unknown state. See " + layout.backupRoot() + " and " + layout.databaseDir()
                    + ": " + e.getMessage(), e);
        }
    }

    private void succeed(TransferStatus status) {
        try {
            status.setState(TransferState.APPLIED);
            status.setFinishedAt(Instant.now().truncatedTo(ChronoUnit.SECONDS));
            status.setError(null);
            store.write(status);
            // The dump holds the data of the source service in plain text, so no copy of it stays on the disk.
            Files.deleteIfExists(layout.pending());
            pruneBackups();
            log.warn("The database was replaced with the imported dump. The previous database is in {}",
                    layout.backupRoot().resolve(status.getBackup()));
        } catch (IOException | RuntimeException e) {
            log.error("The restore succeeded, but the bookkeeping did not: {}", e.getMessage(), e);
        }
    }

    /** Records {@code FAILED}. The caller has already put the previous database back, so no backup remains. */
    private void fail(TransferStatus status, String error) {
        status.setBackup(null);
        status.setState(TransferState.FAILED);
        status.setFinishedAt(Instant.now().truncatedTo(ChronoUnit.SECONDS));
        status.setError(error);
        try {
            replaceDirectoryContent(layout.failedDir(), layout.pending());
        } catch (IOException e) {
            log.warn("Cannot keep the failed dump: {}", e.getMessage());
        }
        store.write(status);
    }

    /** Empties {@code directory}, then moves {@code file} into it under a timestamped name. */
    private void replaceDirectoryContent(Path directory, Path file) throws IOException {
        Files.createDirectories(directory);
        try (Stream<Path> old = Files.list(directory)) {
            for (Path path : old.toList()) {
                Files.deleteIfExists(path);
            }
        }
        if (Files.exists(file)) {
            Files.move(file, directory.resolve(DIRECTORY_TIMESTAMP.format(Instant.now()) + ".sql.gz"));
        }
    }

    private void pruneBackups() throws IOException {
        if (!Files.isDirectory(layout.backupRoot())) {
            return;
        }
        List<Path> backups;
        try (Stream<Path> directories = Files.list(layout.backupRoot())) {
            backups = directories.filter(Files::isDirectory)
                    .sorted(Comparator.comparing((Path path) -> path.getFileName().toString()).reversed())
                    .toList();
        }
        for (Path old : backups.stream().skip(backupsToKeep).toList()) {
            deleteTree(old);
        }
    }

    private static void deleteTree(Path root) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }
}

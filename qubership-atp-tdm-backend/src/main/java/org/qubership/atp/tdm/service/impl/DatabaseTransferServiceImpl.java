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

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.GZIPOutputStream;

import javax.sql.DataSource;

import org.qubership.atp.tdm.exceptions.transfer.DatabaseTransferException;
import org.qubership.atp.tdm.service.DatabaseExport;
import org.qubership.atp.tdm.service.DatabaseTransferService;
import org.qubership.atp.tdm.utils.H2Url;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class DatabaseTransferServiceImpl implements DatabaseTransferService {

    private static final DateTimeFormatter FILE_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    private final DataSource dataSource;
    private final String jdbcUrl;
    private final String serviceVersion;
    private final Semaphore exportSlot = new Semaphore(1);

    /**
     * Creates the service.
     *
     * @param dataSource     the pool of the database to export
     * @param jdbcUrl        the URL of that database, which decides whether the transfer is possible
     * @param serviceVersion the version that the dump header records
     */
    @Autowired
    public DatabaseTransferServiceImpl(DataSource dataSource,
                                       @Value("${jdbc.Url}") String jdbcUrl,
                                       @Value("${project.version}") String serviceVersion) {
        this.dataSource = dataSource;
        this.jdbcUrl = jdbcUrl;
        this.serviceVersion = serviceVersion;
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

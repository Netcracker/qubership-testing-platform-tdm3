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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.zip.GZIPInputStream;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.qubership.atp.tdm.exceptions.transfer.DatabaseTransferException;
import org.qubership.atp.tdm.service.DatabaseExport;

/**
 * Fails when the export writes a dump that another TDM3 cannot load, or lets two exports run at once.
 */
class DatabaseTransferServiceImplTest {

    private static final String PASSWORD = "very-secret-password";

    @TempDir
    Path tempDir;

    private String url;
    private JdbcDataSource dataSource;
    private DatabaseTransferServiceImpl service;

    @BeforeEach
    void setUp() throws SQLException {
        url = "jdbc:h2:file:" + tempDir.resolve("atptdm").toAbsolutePath() + ";MODE=PostgreSQL";
        dataSource = new JdbcDataSource();
        dataSource.setURL(url);
        dataSource.setUser("tdmadmin");
        dataSource.setPassword(PASSWORD);
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE CATALOG (ID UUID PRIMARY KEY, NAME VARCHAR(100), NOTE VARCHAR(100))");
            statement.execute("INSERT INTO CATALOG VALUES ('11111111-1111-1111-1111-111111111111', 'first', "
                    + "'it''s; a note\nwith a line break')");
            statement.execute("INSERT INTO CATALOG VALUES ('22222222-2222-2222-2222-222222222222', 'second', NULL)");
        }
        service = new DatabaseTransferServiceImpl(dataSource, url, "1.0.13");
    }

    @Test
    void writeTo_firstLine_isTheDumpHeaderWithServiceAndH2Versions() throws Exception {
        String dump = exportToString();

        String header = dump.lines().findFirst().orElseThrow();
        assertTrue(header.matches("-- TDM3-DUMP v1 service=1\\.0\\.13 h2=2\\.\\S+ "
                + "created=\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z database=atptdm"), header);
    }

    @Test
    void writeTo_dump_loadsIntoAnotherDatabaseWithTheSameRows() throws Exception {
        Path dumpFile = tempDir.resolve("dump.sql.gz");
        try (DatabaseExport export = service.beginExport(); OutputStream out = Files.newOutputStream(dumpFile)) {
            export.writeTo(out);
        }

        JdbcDataSource target = new JdbcDataSource();
        target.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL");
        target.setUser("tdmadmin");
        target.setPassword("another-password");
        try (Connection connection = target.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("RUNSCRIPT FROM '" + dumpFile.toAbsolutePath().toString().replace('\\', '/')
                    + "' COMPRESSION GZIP CHARSET 'UTF-8'");
            try (ResultSet rows = statement.executeQuery("SELECT NAME, NOTE FROM CATALOG ORDER BY NAME")) {
                assertTrue(rows.next());
                assertEquals("first", rows.getString(1));
                assertEquals("it's; a note\nwith a line break", rows.getString(2));
                assertTrue(rows.next());
                assertEquals("second", rows.getString(1));
                assertEquals(null, rows.getString(2));
                assertFalse(rows.next());
            }
        }
    }

    @Test
    void writeTo_dump_doesNotCarryThePasswordOfTheDatabaseUser() throws Exception {
        String dump = exportToString();

        assertFalse(dump.contains(PASSWORD));
        assertTrue(dump.contains("CREATE USER IF NOT EXISTS \"TDMADMIN\" PASSWORD ''"), dump);
    }

    @Test
    void writeTo_targetStaysOpenAfterTheExport() throws Exception {
        boolean[] closed = new boolean[1];
        OutputStream target = new ByteArrayOutputStream() {
            @Override
            public void close() {
                closed[0] = true;
            }
        };

        try (DatabaseExport export = service.beginExport()) {
            export.writeTo(target);
        }

        assertFalse(closed[0]);
    }

    @Test
    void beginExport_secondExportWhileFirstIsOpen_isRefusedWith409() {
        try (DatabaseExport ignored = service.beginExport()) {
            DatabaseTransferException exception =
                    assertThrows(DatabaseTransferException.class, () -> service.beginExport());

            assertEquals(409, exception.getStatus().value());
        }
    }

    @Test
    void beginExport_afterTheFirstExportIsClosed_isAccepted() {
        service.beginExport().close();

        try (DatabaseExport export = service.beginExport()) {
            assertTrue(export.fileName().startsWith("tdm-atptdm-"), export.fileName());
        }
    }

    @Test
    void beginExport_closedTwice_releasesTheSlotOnce() {
        DatabaseExport first = service.beginExport();
        first.close();
        DatabaseExport second = service.beginExport();

        first.close();

        assertThrows(DatabaseTransferException.class, () -> service.beginExport());
        second.close();
    }

    @Test
    void beginExport_inMemoryDatabase_isRefusedWith409() {
        DatabaseTransferServiceImpl inMemory =
                new DatabaseTransferServiceImpl(dataSource, "jdbc:h2:mem:test", "1.0.13");

        DatabaseTransferException exception =
                assertThrows(DatabaseTransferException.class, inMemory::beginExport);

        assertEquals(409, exception.getStatus().value());
    }

    @Test
    void fileName_hasTheDatabaseNameAndAUtcTimestamp() {
        try (DatabaseExport export = service.beginExport()) {
            assertTrue(export.fileName().matches("tdm-atptdm-\\d{8}-\\d{6}\\.sql\\.gz"), export.fileName());
        }
    }

    private String exportToString() throws IOException {
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (DatabaseExport export = service.beginExport()) {
            export.writeTo(compressed);
        }
        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(compressed.toByteArray()))) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}

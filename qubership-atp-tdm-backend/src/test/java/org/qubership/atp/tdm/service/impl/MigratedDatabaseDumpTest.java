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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.zip.GZIPInputStream;

import org.h2.jdbcx.JdbcDataSource;
import org.h2.tools.RunScript;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.qubership.atp.tdm.configuration.DatabaseRestoreInitializer;
import org.qubership.atp.tdm.model.transfer.TransferState;
import org.qubership.atp.tdm.service.DatabaseExport;

import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;

/**
 * Fails when a migration or the Quartz schema adds a statement kind to the database that the dump validator does not
 * accept. The migration then has to add the kind to the allowlist of {@link DumpValidator} in the same change.
 */
class MigratedDatabaseDumpTest {

    @TempDir
    Path tempDir;

    @Test
    void dumpOfAFreshlyMigratedDatabase_passesTheValidator() throws Exception {
        String url = "jdbc:h2:file:" + tempDir.resolve("atptdm").toAbsolutePath() + ";MODE=PostgreSQL";
        try (Connection connection = DriverManager.getConnection(url, "tdmadmin", "secret")) {
            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            new Liquibase("changelog-master.xml", new ClassLoaderResourceAccessor(), database).update("");
            try (var quartz = new InputStreamReader(getClass().getResourceAsStream("/quartz/schema.sql"),
                    StandardCharsets.UTF_8)) {
                RunScript.execute(connection, quartz);
            }
        }
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL(url);
        dataSource.setUser("tdmadmin");
        dataSource.setPassword("secret");
        DatabaseTransferServiceImpl service =
                new DatabaseTransferServiceImpl(dataSource, url, "1.0.13", "", "100MB", () -> { });
        ByteArrayOutputStream dump = new ByteArrayOutputStream();
        try (DatabaseExport export = service.beginExport()) {
            export.writeTo(dump);
        }

        String text = new String(new GZIPInputStream(new ByteArrayInputStream(dump.toByteArray())).readAllBytes(),
                StandardCharsets.UTF_8);
        assertTrue(text.contains("\"TEST_DATA_TABLE_CATALOG\""), "The migration did not create the catalog table.");
        assertTrue(text.contains("\"QRTZ_TRIGGERS\""), "The Quartz schema was not loaded.");
        assertTrue(text.contains("\"SHEDLOCK\"") || text.contains("\"shedlock\""), "The lock table is missing.");

        try (Reader reader = new StringReader(text)) {
            DumpValidator.DumpHeader header = DumpValidator.validate(reader, null, true);
            assertTrue(header.serviceVersion().equals("1.0.13"));
        }

        // The restore into an empty target must load the real schema, including the alias and the identity columns.
        String targetUrl = "jdbc:h2:file:" + tempDir.resolve("target").resolve("atptdm").toAbsolutePath()
                + ";MODE=PostgreSQL";
        JdbcDataSource targetSource = new JdbcDataSource();
        targetSource.setURL(targetUrl);
        targetSource.setUser("tdmadmin");
        targetSource.setPassword("other");
        DatabaseTransferServiceImpl target =
                new DatabaseTransferServiceImpl(targetSource, targetUrl, "1.0.13", "", "100MB", () -> { });
        target.stageImport(new ByteArrayInputStream(dump.toByteArray()), dump.size());
        target.approveImport("REPLACE_ALL_DATA", false);

        new DatabaseRestoreInitializer(targetUrl, "tdmadmin", "other", "", 2).restoreIfApproved();

        assertEquals(TransferState.APPLIED, target.importStatus().getState(), target.importStatus().getError());
        try (Connection connection = DriverManager.getConnection(targetUrl, "tdmadmin", "other");
             var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES "
                     + "WHERE TABLE_NAME = 'TEST_DATA_TABLE_CATALOG'")) {
            rows.next();
            assertEquals(1, rows.getInt(1));
        }
    }
}

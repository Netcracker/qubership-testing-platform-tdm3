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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPOutputStream;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.qubership.atp.tdm.configuration.DatabaseRestoreInitializer;
import org.qubership.atp.tdm.exceptions.transfer.DatabaseTransferException;
import org.qubership.atp.tdm.model.transfer.TransferState;
import org.qubership.atp.tdm.model.transfer.TransferStatus;
import org.qubership.atp.tdm.service.DatabaseExport;

/**
 * Fails when an import loses data, runs without an approval, leaves the previous database lost after a failure, or
 * lets the dump change the login of the database.
 */
class DatabaseImportRestoreTest {

    private static final String USER = "tdmadmin";
    private static final String TARGET_PASSWORD = "target-password";
    private static final String CONFIRM = "REPLACE_ALL_DATA";

    @TempDir
    Path tempDir;

    private String sourceUrl;
    private String targetUrl;
    private Path targetFile;
    private DatabaseTransferServiceImpl target;
    private final AtomicInteger restarts = new AtomicInteger();

    @BeforeEach
    void setUp() throws SQLException {
        sourceUrl = "jdbc:h2:file:" + tempDir.resolve("source").resolve("atptdm").toAbsolutePath() + ";MODE=PostgreSQL";
        targetUrl = "jdbc:h2:file:" + tempDir.resolve("target").resolve("atptdm").toAbsolutePath() + ";MODE=PostgreSQL";
        targetFile = tempDir.resolve("target").resolve("atptdm.mv.db");
        createDatabase(sourceUrl, "source-password", "from the source");
        createDatabase(targetUrl, TARGET_PASSWORD, "from the target");
        target = new DatabaseTransferServiceImpl(dataSource(targetUrl, TARGET_PASSWORD), targetUrl, "1.0.13", "",
                "100MB", restarts::incrementAndGet);
    }

    private static JdbcDataSource dataSource(String url, String password) {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL(url);
        dataSource.setUser(USER);
        dataSource.setPassword(password);
        return dataSource;
    }

    private void createDatabase(String url, String password, String note) throws SQLException {
        try (Connection connection = DriverManager.getConnection(url, USER, password);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE DATABASECHANGELOG (ID VARCHAR(50))");
            statement.execute("CREATE TABLE TEST_DATA_TABLE_CATALOG (ID INT PRIMARY KEY, NOTE VARCHAR(100))");
            statement.execute("INSERT INTO TEST_DATA_TABLE_CATALOG VALUES (1, '" + note + "')");
            statement.execute("CREATE TABLE QRTZ_TRIGGERS (NAME VARCHAR(50), TRIGGER_STATE VARCHAR(16))");
            statement.execute("INSERT INTO QRTZ_TRIGGERS VALUES ('acquired', 'ACQUIRED'), ('blocked', 'BLOCKED'), "
                    + "('pausedblocked', 'PAUSED_BLOCKED'), ('waiting', 'WAITING'), ('error', 'ERROR')");
            statement.execute("CREATE TABLE QRTZ_FIRED_TRIGGERS (NAME VARCHAR(50))");
            statement.execute("INSERT INTO QRTZ_FIRED_TRIGGERS VALUES ('running')");
            statement.execute("CREATE TABLE QRTZ_SCHEDULER_STATE (NAME VARCHAR(50))");
            statement.execute("INSERT INTO QRTZ_SCHEDULER_STATE VALUES ('old-instance')");
            statement.execute("CREATE TABLE QRTZ_LOCKS (NAME VARCHAR(50))");
            statement.execute("INSERT INTO QRTZ_LOCKS VALUES ('TRIGGER_ACCESS')");
            statement.execute("CREATE TABLE SHEDLOCK (NAME VARCHAR(50))");
            statement.execute("INSERT INTO SHEDLOCK VALUES ('bulk-action')");
        }
    }

    private byte[] exportOf(String url, String password) throws IOException {
        DatabaseTransferServiceImpl source =
                new DatabaseTransferServiceImpl(dataSource(url, password), url, "1.0.13", "", "100MB", () -> { });
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (DatabaseExport export = source.beginExport()) {
            export.writeTo(out);
        }
        return out.toByteArray();
    }

    private TransferStatus stage(byte[] dump) {
        return target.stageImport(new ByteArrayInputStream(dump), dump.length);
    }

    private static byte[] gzip(String text) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(text.getBytes(StandardCharsets.UTF_8));
        }
        return out.toByteArray();
    }

    private void restart() {
        new DatabaseRestoreInitializer(targetUrl, USER, TARGET_PASSWORD, "", 2).restoreIfApproved();
    }

    private String targetNote() throws SQLException {
        try (Connection connection = DriverManager.getConnection(targetUrl, USER, TARGET_PASSWORD);
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT NOTE FROM TEST_DATA_TABLE_CATALOG")) {
            assertTrue(rows.next());
            return rows.getString(1);
        }
    }

    private int count(String table) throws SQLException {
        try (Connection connection = DriverManager.getConnection(targetUrl, USER, TARGET_PASSWORD);
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rows.next();
            return rows.getInt(1);
        }
    }

    @Test
    void import_approvedDump_replacesTheDataAndKeepsTheTargetLogin() throws Exception {
        TransferStatus staged = stage(exportOf(sourceUrl, "source-password"));
        target.approveImport(CONFIRM, false);

        restart();

        assertEquals("from the source", targetNote());
        TransferStatus status = target.importStatus();
        assertEquals(TransferState.APPLIED, status.getState());
        assertEquals(staged.getSha256(), status.getSha256());
        assertTrue(Files.isDirectory(tempDir.resolve("target").resolve("transfer").resolve("backup")
                .resolve(status.getBackup())));
        assertFalse(Files.exists(tempDir.resolve("target").resolve("transfer").resolve("pending.sql.gz")));
        assertThrows(SQLException.class, () -> DriverManager.getConnection(targetUrl, USER, ""));
        assertThrows(SQLException.class, () -> DriverManager.getConnection(targetUrl, USER, "source-password"));
    }

    /** The dump holds the data of the source service in plain text, so no copy of an applied dump stays behind. */
    @Test
    void import_approvedDump_leavesNoCopyOfTheDumpInTheTransferDirectory() throws Exception {
        stage(exportOf(sourceUrl, "source-password"));
        target.approveImport(CONFIRM, false);

        restart();

        Path transfer = tempDir.resolve("target").resolve("transfer");
        try (var files = Files.walk(transfer)) {
            assertEquals(java.util.List.of(), files.filter(path -> path.getFileName().toString().endsWith(".sql.gz"))
                    .toList());
        }
    }

    @Test
    void import_approvedDump_clearsTheRuntimeStateOfTheSchedulerAndKeepsTheRest() throws Exception {
        stage(exportOf(sourceUrl, "source-password"));
        target.approveImport(CONFIRM, false);

        restart();

        assertEquals(0, count("QRTZ_FIRED_TRIGGERS"));
        assertEquals(0, count("QRTZ_SCHEDULER_STATE"));
        assertEquals(0, count("SHEDLOCK"));
        assertEquals(1, count("QRTZ_LOCKS"));
        try (Connection connection = DriverManager.getConnection(targetUrl, USER, TARGET_PASSWORD);
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT NAME, TRIGGER_STATE FROM QRTZ_TRIGGERS ORDER BY NAME")) {
            StringBuilder states = new StringBuilder();
            while (rows.next()) {
                states.append(rows.getString(1)).append('=').append(rows.getString(2)).append(' ');
            }
            assertEquals("acquired=WAITING blocked=WAITING error=ERROR pausedblocked=PAUSED waiting=WAITING ",
                    states.toString());
        }
    }

    @Test
    void restart_stagedButNotApprovedDump_changesNothing() throws Exception {
        stage(exportOf(sourceUrl, "source-password"));

        restart();

        assertEquals("from the target", targetNote());
        assertEquals(TransferState.STAGED, target.importStatus().getState());
    }

    @Test
    void restart_dumpThatFailsInTheMiddle_putsThePreviousDatabaseBack() throws Exception {
        String header = "-- TDM3-DUMP v1 service=1.0.13 h2=2.2.224 created=2026-11-11T10:15:30Z database=atptdm\n";
        stage(gzip(header
                + "CREATE CACHED TABLE \"PUBLIC\".\"T\"(\"ID\" INT);\n"
                + "INSERT INTO \"PUBLIC\".\"MISSING_TABLE\" VALUES (1);\n"));
        target.approveImport(CONFIRM, false);

        restart();

        assertEquals("from the target", targetNote());
        TransferStatus status = target.importStatus();
        assertEquals(TransferState.FAILED, status.getState());
        assertTrue(status.getError().contains("previous database was put back"), status.getError());
        assertNull(status.getBackup(), "The backup moved back, so the status must not name a directory that is gone.");
        Path transfer = tempDir.resolve("target").resolve("transfer");
        assertFalse(Files.exists(transfer.resolve("pending.sql.gz")));
        try (var failed = Files.list(transfer.resolve("failed"))) {
            assertEquals(1, failed.count());
        }
        assertTrue(Files.exists(targetFile));
    }

    @Test
    void restart_dumpWithoutTheRequiredTables_putsThePreviousDatabaseBack() throws Exception {
        String header = "-- TDM3-DUMP v1 service=1.0.13 h2=2.2.224 created=2026-11-11T10:15:30Z database=atptdm\n";
        stage(gzip(header + "CREATE CACHED TABLE \"PUBLIC\".\"T\"(\"ID\" INT);\n"));
        target.approveImport(CONFIRM, false);

        restart();

        assertEquals("from the target", targetNote());
        assertEquals(TransferState.FAILED, target.importStatus().getState());
        assertTrue(target.importStatus().getError().contains("DATABASECHANGELOG"));
    }

    @Test
    void restart_afterACrashDuringTheRestore_putsThePreviousDatabaseBack() throws Exception {
        stage(exportOf(sourceUrl, "source-password"));
        target.approveImport(CONFIRM, false);
        // The state a crash leaves: the old files in backup/, a marker, and a partly written new database.
        Path transfer = tempDir.resolve("target").resolve("transfer");
        Path backup = transfer.resolve("backup").resolve("20261113-101530");
        Files.createDirectories(backup);
        Files.move(targetFile, backup.resolve("atptdm.mv.db"));
        Files.writeString(backup.resolve("MOVED"), "");
        Files.writeString(targetFile, "half-written new database");
        TransferStatus status = target.importStatus();
        status.setState(TransferState.APPLYING);
        status.setBackup("20261113-101530");
        new TransferStatusStore(transfer.resolve("status.json")).write(status);

        restart();

        assertEquals("from the target", targetNote());
        assertEquals(TransferState.FAILED, target.importStatus().getState());
        assertTrue(target.importStatus().getError().contains("interrupted"));
        assertFalse(Files.exists(backup));
    }

    @Test
    void restart_afterACrashBeforeTheMarker_leavesTheDatabaseInPlace() throws Exception {
        stage(exportOf(sourceUrl, "source-password"));
        target.approveImport(CONFIRM, false);
        Path transfer = tempDir.resolve("target").resolve("transfer");
        Files.createDirectories(transfer.resolve("backup").resolve("20261113-101530"));
        TransferStatus status = target.importStatus();
        status.setState(TransferState.APPLYING);
        status.setBackup("20261113-101530");
        new TransferStatusStore(transfer.resolve("status.json")).write(status);

        restart();

        assertEquals("from the target", targetNote());
        assertEquals(TransferState.FAILED, target.importStatus().getState());
    }

    @Test
    void restart_dumpChangedAfterStaging_isNotApplied() throws Exception {
        stage(exportOf(sourceUrl, "source-password"));
        target.approveImport(CONFIRM, false);
        Files.write(tempDir.resolve("target").resolve("transfer").resolve("pending.sql.gz"),
                gzip("-- TDM3-DUMP v1 service=1.0.13 h2=2.2.224 created=x database=y\nCREATE INDEX \"A\".\"B\" ON T(X);"));

        restart();

        assertEquals("from the target", targetNote());
        TransferStatus status = target.importStatus();
        assertEquals(TransferState.FAILED, status.getState());
        assertTrue(status.getError().contains("changed after it was validated"), status.getError());
    }

    @Test
    void restart_oldBackups_arePrunedToTheConfiguredNumber() throws Exception {
        Path backupRoot = tempDir.resolve("target").resolve("transfer").resolve("backup");
        for (String old : new String[] {"20260101-000000", "20260102-000000", "20260103-000000"}) {
            Files.createDirectories(backupRoot.resolve(old));
        }
        stage(exportOf(sourceUrl, "source-password"));
        target.approveImport(CONFIRM, false);

        restart();

        try (var backups = Files.list(backupRoot)) {
            assertEquals(2, backups.count());
        }
        assertFalse(Files.exists(backupRoot.resolve("20260101-000000")));
    }

    @Test
    void stageImport_dumpWithATrigger_isRejectedWith400AndNothingIsStored() throws Exception {
        String header = "-- TDM3-DUMP v1 service=1.0.13 h2=2.2.224 created=x database=y\n";

        DatabaseTransferException exception = assertThrows(DatabaseTransferException.class,
                () -> stage(gzip(header + "CREATE TRIGGER T BEFORE INSERT ON X CALL 'y';\n")));

        assertEquals(400, exception.getStatus().value());
        assertEquals(TransferState.NONE, target.importStatus().getState());
        assertFalse(Files.exists(tempDir.resolve("target").resolve("transfer").resolve("incoming.sql.gz.part")));
        assertFalse(Files.exists(tempDir.resolve("target").resolve("transfer").resolve("pending.sql.gz")));
    }

    @Test
    void stageImport_notGzip_isRejectedWith400() {
        DatabaseTransferException exception = assertThrows(DatabaseTransferException.class,
                () -> stage("plain text".getBytes(StandardCharsets.UTF_8)));

        assertEquals(400, exception.getStatus().value());
    }

    @Test
    void stageImport_truncatedGzip_isRejectedWith400() throws Exception {
        byte[] dump = exportOf(sourceUrl, "source-password");
        byte[] cut = java.util.Arrays.copyOf(dump, dump.length - 10);

        DatabaseTransferException exception = assertThrows(DatabaseTransferException.class, () -> stage(cut));

        assertEquals(400, exception.getStatus().value());
    }

    @Test
    void stageImport_newerDump_isRejectedWith400() throws Exception {
        String header = "-- TDM3-DUMP v1 service=9.0.0 h2=2.2.224 created=x database=y\n";

        DatabaseTransferException exception =
                assertThrows(DatabaseTransferException.class, () -> stage(gzip(header)));

        assertEquals(400, exception.getStatus().value());
        assertFalse(Files.exists(tempDir.resolve("target").resolve("transfer").resolve("incoming.sql.gz.part")));
    }

    @Test
    void stageImport_declaredLengthOverTheLimit_isRejectedWith413() {
        DatabaseTransferServiceImpl small = new DatabaseTransferServiceImpl(dataSource(targetUrl, TARGET_PASSWORD),
                targetUrl, "1.0.13", "", "1KB", () -> { });

        DatabaseTransferException exception = assertThrows(DatabaseTransferException.class,
                () -> small.stageImport(new ByteArrayInputStream(new byte[0]), 5000));

        assertEquals(413, exception.getStatus().value());
    }

    @Test
    void stageImport_streamOverTheLimitWithoutADeclaredLength_isRejectedWith413AndThePartFileIsRemoved()
            throws Exception {
        DatabaseTransferServiceImpl small = new DatabaseTransferServiceImpl(dataSource(targetUrl, TARGET_PASSWORD),
                targetUrl, "1.0.13", "", "1KB", () -> { });
        byte[] noise = new byte[8000];
        new Random(1).nextBytes(noise);
        String header = "-- TDM3-DUMP v1 service=1.0.13 h2=2.2.224 created=x database=y\n";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(header.getBytes(StandardCharsets.UTF_8));
            gzip.write(java.util.Base64.getEncoder().encode(noise));
        }

        DatabaseTransferException exception = assertThrows(DatabaseTransferException.class,
                () -> small.stageImport(new ByteArrayInputStream(out.toByteArray()), -1));

        assertEquals(413, exception.getStatus().value());
        assertFalse(Files.exists(tempDir.resolve("target").resolve("transfer").resolve("incoming.sql.gz.part")));
    }

    @Test
    void stageImport_newUpload_replacesAnApprovedDumpAndSetsStagedAgain() throws Exception {
        stage(exportOf(sourceUrl, "source-password"));
        target.approveImport(CONFIRM, false);

        TransferStatus status = stage(exportOf(sourceUrl, "source-password"));

        assertEquals(TransferState.STAGED, status.getState());
        assertNull(status.getApprovedAt());
    }

    @Test
    void stageImport_olderMajorDump_isAcceptedWithAWarning() throws Exception {
        DatabaseTransferServiceImpl newer = new DatabaseTransferServiceImpl(dataSource(targetUrl, TARGET_PASSWORD),
                targetUrl, "5.10.20", "", "100MB", () -> { });
        byte[] dump = exportOf(sourceUrl, "source-password");

        TransferStatus status = newer.stageImport(new ByteArrayInputStream(dump), dump.length);

        assertEquals(1, status.getWarnings().size());
        assertEquals("1.0.13", status.getDumpServiceVersion());
    }

    @Test
    void approveImport_wrongOrMissingConfirmation_isRejectedWith400() throws Exception {
        stage(exportOf(sourceUrl, "source-password"));

        assertEquals(400, assertThrows(DatabaseTransferException.class,
                () -> target.approveImport("yes", true)).getStatus().value());
        assertEquals(400, assertThrows(DatabaseTransferException.class,
                () -> target.approveImport(null, true)).getStatus().value());
        assertEquals(TransferState.STAGED, target.importStatus().getState());
        assertEquals(0, restarts.get());
    }

    @Test
    void approveImport_nothingStaged_isRejectedWith409() {
        assertEquals(409, assertThrows(DatabaseTransferException.class,
                () -> target.approveImport(CONFIRM, true)).getStatus().value());
        assertEquals(0, restarts.get());
    }

    @Test
    void approveImport_withRestart_asksForARestartOnce() throws Exception {
        stage(exportOf(sourceUrl, "source-password"));

        TransferStatus status = target.approveImport(CONFIRM, true);

        assertEquals(TransferState.APPROVED, status.getState());
        assertEquals(1, restarts.get());
    }

    @Test
    void approveImport_withoutRestart_doesNotRestart() throws Exception {
        stage(exportOf(sourceUrl, "source-password"));

        target.approveImport(CONFIRM, false);

        assertEquals(0, restarts.get());
    }

    @Test
    void cancelImport_stagedDump_isDeletedAndTheStateIsNone() throws Exception {
        stage(exportOf(sourceUrl, "source-password"));

        target.cancelImport();

        assertEquals(TransferState.NONE, target.importStatus().getState());
        assertFalse(Files.exists(tempDir.resolve("target").resolve("transfer").resolve("pending.sql.gz")));
    }

    @Test
    void cancelImport_afterARestore_keepsTheResultOfTheLastRestore() throws Exception {
        stage(exportOf(sourceUrl, "source-password"));
        target.approveImport(CONFIRM, false);
        restart();

        target.cancelImport();

        assertEquals(TransferState.APPLIED, target.importStatus().getState());
    }

    @Test
    void importOperations_inMemoryDatabase_areRefusedWith409() {
        DatabaseTransferServiceImpl inMemory = new DatabaseTransferServiceImpl(dataSource(targetUrl, TARGET_PASSWORD),
                "jdbc:h2:mem:test", "1.0.13", "", "100MB", () -> { });

        assertEquals(409, assertThrows(DatabaseTransferException.class,
                () -> inMemory.stageImport(new ByteArrayInputStream(new byte[0]), 0)).getStatus().value());
        assertEquals(409, assertThrows(DatabaseTransferException.class,
                inMemory::importStatus).getStatus().value());
        assertEquals(409, assertThrows(DatabaseTransferException.class,
                inMemory::cancelImport).getStatus().value());
        assertEquals(409, assertThrows(DatabaseTransferException.class,
                () -> inMemory.approveImport(CONFIRM, false)).getStatus().value());
    }

    @Test
    void restart_inMemoryDatabase_doesNothing() {
        new DatabaseRestoreInitializer("jdbc:h2:mem:test", USER, "x", "", 2).restoreIfApproved();
    }
}

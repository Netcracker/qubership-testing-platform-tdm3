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

package org.qubership.atp.tdm.websocket.bulkaction.cleanup;

import org.qubership.atp.tdm.AbstractTestDataTest;
import org.qubership.atp.tdm.model.TestDataTableCatalog;
import org.qubership.atp.tdm.model.bulkaction.BulkActionConfig;
import org.qubership.atp.tdm.model.bulkaction.BulkActionResult;
import org.qubership.atp.tdm.model.mail.bulkaction.BulkCleanupMailSender;
import org.qubership.atp.tdm.model.cleanup.CleanupResults;
import org.qubership.atp.tdm.repo.CleanupConfigRepository;
import org.qubership.atp.tdm.env.configurator.model.LazyEnvironment;
import org.assertj.core.util.Lists;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.socket.WebSocketSession;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class BulkDataCleanupHandlerTest extends AbstractTestDataTest {

    private final List<LazyEnvironment> lazyEnvironments = Lists.newArrayList(
            LazyEnvironment.builder().id(environmentId).name(environmentName).build()
    );

    @Autowired
    ExecutorService executorService;

    @Autowired
    CleanupConfigRepository cleanupConfigRepository;

    @Autowired
    BulkCleanupMailSender bulkCleanupMailSender;

    WebSocketSession session;

    BulkDataCleanupHandler bulkDataCleanupHandler;

    @BeforeEach
    public void setUp() throws Exception {
        bulkDataCleanupHandler = new BulkDataCleanupHandler(executorService, catalogRepository, environmentsService,
                cleanupService, cleanupConfigRepository, bulkCleanupMailSender, currentTime, lockManager, tdmMdcHelper);

        when(environmentsService.getConnectionsSystemById(any(), any())).thenReturn(connections);
    }


    @Test
    public void runBulkActionTest_saveDataConfigAndCleanupTable_returnBulkActionIsCorrect() throws Exception {
        final UUID projectId = UUID.randomUUID();
        final long processId = currentTime.getCurrentTimeMillis();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        String tableName = "tdm_cleanup_date_config_cleanup_results";
        String tableTitle = "TDM Cleanup Date Config Cleanup Results";
        BulkActionConfig bulkActionConfig = new BulkActionConfig(){{
            setProjectId(projectId);
            setSystemId(systemId);
            setSaveOccupiedData(false);
            setExecuteInParallel(false);
            setSendResult(false);
            setRecipients("example@example.com");
            setTableTitle(tableTitle);
        }};
        createTestDataTable(tableName);
        TestDataTableCatalog catalog = createTestDataTableCatalog(projectId, systemId, environmentId, tableTitle,
                tableName);
        mockEnvironmentService(Collections.singletonList(environmentId),systemId,systemId);
        createDateCleanupConfig(catalog);
        BulkActionResult expectedBulkActionResult = new BulkActionResult(tableTitle, tableName,
                environmentName, new CleanupResults(tableName, 6, 0));

        List<Future<BulkActionResult>> futures = bulkDataCleanupHandler
                .runBulkAction(session, executor, lazyEnvironments, bulkActionConfig, processId);

        BulkActionResult actualBulkActionResult = futures.get(0).get();

        Assertions.assertEquals(expectedBulkActionResult, actualBulkActionResult);

        deleteTestDataTableIfExists(tableName);
        catalogRepository.deleteByTableName(tableName);
    }

    @Test
    public void runBulkActionTest_saveDataConfigAndCleanupTable_testDataTableHasNotCleanup() throws Exception {
        final UUID projectId = UUID.randomUUID();
        long processId = currentTime.getCurrentTimeMillis();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        String tableName = "tdm_cleanup_date_config_not_empty_table";
        String tableTitle = "TDM Cleanup Date Config Not Empty Table";
        BulkActionConfig bulkActionConfig = new BulkActionConfig(){{
            setProjectId(projectId);
            setSystemId(systemId);
            setSaveOccupiedData(false);
            setExecuteInParallel(false);
            setSendResult(false);
            setRecipients("example@example.com");
            setTableTitle(tableTitle);
        }};
        createTestDataTable(tableName);
        TestDataTableCatalog tableCatalog =
                createTestDataTableCatalog(projectId, systemId, environmentId, tableTitle, tableName);
        mockEnvironmentService(Collections.singletonList(environmentId),systemId,systemId);
        createDateCleanupConfigToDay(tableCatalog);

        Assertions.assertFalse(testDataService.getTestData(tableName).getData().isEmpty());
        List<Future<BulkActionResult>> futures =
                bulkDataCleanupHandler.runBulkAction(session, executor, lazyEnvironments, bulkActionConfig, processId);
        BulkActionResult bulkActionResult = futures.get(0).get();
        boolean result = testDataService.getTestData(tableName).getData().isEmpty();

        Assertions.assertTrue(result);

        deleteTestDataTableIfExists(tableName);
        catalogRepository.deleteByTableName(tableName);
    }

    @Test
    public void runBulkActionTest_saveSqlConfigAndCleanupTable_bulkActionResultIsCorrect() throws Exception {
        final UUID projectId = UUID.randomUUID();
        long processId = currentTime.getCurrentTimeMillis();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        String tableName = "tdm_cleanup_sql_config_cleanup_results";
        String tableTitle = "TDM Cleanup SQL Config Cleanup Results";
        BulkActionConfig bulkActionConfig = new BulkActionConfig(){{
            setProjectId(projectId);
            setSystemId(systemId);
            setSaveOccupiedData(false);
            setExecuteInParallel(false);
            setSendResult(false);
            setRecipients("example@example.com");
            setTableTitle(tableTitle);
        }};
        createTestDataTable(tableName);
        TestDataTableCatalog tableCatalog =
                createTestDataTableCatalog(projectId, systemId, environmentId, tableTitle, tableName);
        mockEnvironmentService(Collections.singletonList(environmentId),systemId,systemId);
        createSqlCleanupConfig(tableCatalog, true);

        BulkActionResult expectedBulkActionResult = new BulkActionResult(tableTitle, tableName,
                environmentName, new CleanupResults(tableName, 6, 6));

        List<Future<BulkActionResult>> futures = bulkDataCleanupHandler
                .runBulkAction(session, executor, lazyEnvironments, bulkActionConfig, processId);

        BulkActionResult actualBulkActionResult = futures.get(0).get();

        Assertions.assertEquals(expectedBulkActionResult, actualBulkActionResult);

        deleteTestDataTableIfExists(tableName);
        catalogRepository.deleteByTableName(tableName);
    }

    @Test
    public void runBulkActionTest_saveSqlConfigAndCleanupTable_cleanupTestDataTable() throws Exception {
        final UUID projectId = UUID.randomUUID();
        long processId = currentTime.getCurrentTimeMillis();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        String tableName = "tdm_cleanup_sql_config_empty_table";
        String tableTitle = "TDM Cleanup SQL Config Empty Table";
        BulkActionConfig bulkActionConfig = new BulkActionConfig(){{
            setProjectId(projectId);
            setSystemId(systemId);
            setSaveOccupiedData(false);
            setExecuteInParallel(false);
            setSendResult(false);
            setRecipients("example@example.com");
            setTableTitle(tableTitle);
        }};
        createTestDataTable(tableName);
        TestDataTableCatalog tableCatalog =
                createTestDataTableCatalog(projectId, systemId, environmentId, tableTitle, tableName);
        mockEnvironmentService(Collections.singletonList(environmentId),systemId,systemId);
        createSqlCleanupConfig(tableCatalog, true);
        Assertions.assertFalse(testDataService.getTestData(tableName).getData().isEmpty());
        List<Future<BulkActionResult>> futures =
                bulkDataCleanupHandler.runBulkAction(session, executor, lazyEnvironments, bulkActionConfig, processId);
        BulkActionResult bulkActionResult = futures.get(0).get();
        boolean result = testDataService.getTestData(tableName).getData().isEmpty();

        Assertions.assertTrue(result);

        deleteTestDataTableIfExists(tableName);
        catalogRepository.deleteByTableName(tableName);
    }


    @Test
    public void getEnvName_findEnvironmentNameByExistEnvironmentId_returnEnvironmentNameIsCorrect() {
        String actualEnvironmentName = bulkDataCleanupHandler.getEnvName(lazyEnvironments, environmentId);
        Assertions.assertEquals(environmentName, actualEnvironmentName);
    }

    @Test
    public void getEnvName_findEnvironmentNameByNotExistEnvironmentId_returnRuntimeException() {
        Assertions.assertThrows(RuntimeException.class, () -> {
            UUID errorId = UUID.randomUUID();
            bulkDataCleanupHandler.getEnvName(lazyEnvironments, errorId);
        });
    }

}




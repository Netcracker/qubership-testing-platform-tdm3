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

package org.qubership.atp.tdm.websocket.bulkaction.dataload;

import org.qubership.atp.tdm.AbstractTestDataTest;
import org.qubership.atp.tdm.model.TestDataTableCatalog;
import org.qubership.atp.tdm.model.bulkaction.BulkActionConfig;
import org.qubership.atp.tdm.model.bulkaction.BulkActionResult;
import org.qubership.atp.tdm.model.mail.bulkaction.BulkCleanupMailSender;
import org.qubership.atp.tdm.model.refresh.RefreshResults;
import org.qubership.atp.tdm.model.table.TestDataTable;
import org.qubership.atp.tdm.service.DataRefreshService;

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

public class BulkDataImportHandlerTest extends AbstractTestDataTest {

    @Autowired
    ExecutorService executorService;

    @Autowired
    BulkCleanupMailSender bulkCleanupMailSender;

    @Autowired
    DataRefreshService dataRefreshService;

    WebSocketSession session;

    BulkDataImportHandler bulkDataImportHandler;


    @BeforeEach
    public void setUp() throws Exception {
        bulkDataImportHandler = new BulkDataImportHandler(executorService, catalogRepository, environmentsService,
                bulkCleanupMailSender, dataRefreshService, importInfoRepository, currentTime, lockManager, tdmMdcHelper);

        when(environmentsService.getConnectionsSystemById(any(), any())).thenReturn(Collections.singletonList(dbConnection));
    }


    @Test
    public void runBulkAction_saveQueryAndUpdateTable_bulkActionIsCorrect() throws Exception {
        final UUID projectId = UUID.randomUUID();
        long processId = java.lang.System.currentTimeMillis();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        String tableName = "tdm_run_balk_import_refresh_results";
        String tableTitle = "TDM Run Balk Import Refresh Result";
        BulkActionConfig bulkActionConfig = new BulkActionConfig(){{
            setProjectId(projectId);
            setSystemId(systemId);
            setSaveOccupiedData(false);
            setExecuteInParallel(false);
            setSendResult(false);
            setRecipients("example@example.com");
            setTableTitle(tableTitle);
        }};

        TestDataTable table = createTestDataTable(tableName);
        String importQuery = "select \"sim\" from " + tableName;
        TestDataTableCatalog testTable = createTestDataTableCatalog(projectId, systemId, environmentId, tableTitle, tableName, importQuery);

        BulkActionResult expectedBulkActionResult =
                new BulkActionResult(tableTitle, tableName, environmentName, new RefreshResults(6));

        List<Future<BulkActionResult>> futures = bulkDataImportHandler
                .runBulkAction(session, executor, lazyEnvironments, bulkActionConfig, processId);

        BulkActionResult actualBulkActionResult = futures.get(0).get();

        Assertions.assertEquals(expectedBulkActionResult, actualBulkActionResult);

        deleteTestDataTableIfExists(tableName);
        catalogRepository.deleteByTableName(tableName);
    }

    @Test
    public void runBulkAction_saveQueryAndUpdateTable_testDataTableUpdated() throws Exception {
        final UUID projectId = UUID.randomUUID();
        long processId = java.lang.System.currentTimeMillis();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        String tableName = "tdm_run_balk_import_refresh_table";
        String tableTitle = "TDM Run Balk Import Refresh Table";
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
        String importQuery = "select \"sim\" from " + tableName;
        createTestDataTableCatalog(projectId, systemId, environmentId, tableTitle, tableName, importQuery);

        TestDataTable expected = testDataService.getTestData(tableName);

        List<Future<BulkActionResult>> futures =
                bulkDataImportHandler.runBulkAction(session, executor, lazyEnvironments, bulkActionConfig, processId);

        BulkActionResult actualBulkActionResult = futures.get(0).get();

        TestDataTable actual = testDataService.getTestData(tableName);

        Assertions.assertNotEquals(expected, actual);

        deleteTestDataTableIfExists(tableName);
        catalogRepository.deleteByTableName(tableName);
    }

}

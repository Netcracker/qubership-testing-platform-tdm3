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

package org.qubership.atp.tdm.websocket.bulkaction.drop;

import org.qubership.atp.tdm.AbstractTestDataTest;
import org.qubership.atp.tdm.model.CommonResults;
import org.qubership.atp.tdm.model.DropResults;
import org.qubership.atp.tdm.model.bulkaction.BulkActionConfig;
import org.qubership.atp.tdm.model.bulkaction.BulkActionResult;
import org.qubership.atp.tdm.model.mail.bulkaction.BulkDropMailSender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.socket.WebSocketSession;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class BulkDataDropHandlerTest  extends AbstractTestDataTest {

    @Autowired
    ExecutorService executorService;

    @Autowired
    BulkDropMailSender bulkDropMailSender;

    WebSocketSession session;

    BulkDataDropHandler bulkDataDropHandler;


    @BeforeEach
    public void setUp() throws Exception {
        bulkDataDropHandler = new BulkDataDropHandler(executorService, catalogRepository, environmentsService,
                testDataService, bulkDropMailSender, currentTime, lockManager, tdmMdcHelper);
    }


    @Test
    public void runBulkAction_dropTable_bulkActionIsCorrect() throws Exception {
        final UUID projectId = UUID.randomUUID();
        final UUID systemId = UUID.randomUUID();
        long processId = java.lang.System.currentTimeMillis();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        String tableName = "tdm_run_balk_drop_table";
        String tableTitle = "TDM Run Balk Drop Table";
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
        createTestDataTableCatalog(projectId, systemId, environmentId, tableTitle, tableName);

        CommonResults executeResults = new DropResults() {{setTableName(tableName);}};
        BulkActionResult expectedBulkActionResult = new BulkActionResult(tableTitle, tableName,
                environmentName, executeResults);

        List<Future<BulkActionResult>> futures = bulkDataDropHandler
                .runBulkAction(session, executor, lazyEnvironments, bulkActionConfig, processId);
        BulkActionResult actualBulkActionResultFuture = futures.get(0).get();

        Assertions.assertEquals(expectedBulkActionResult, actualBulkActionResultFuture);

        catalogRepository.deleteByTableName(tableName);
        deleteTestDataTableIfExists(tableName);
    }

    @Test
    public void runBulkAction_dropTable_cleanupTestDataTableCatalog() throws Exception {
        final UUID projectId = UUID.randomUUID();
        long processId = java.lang.System.currentTimeMillis();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        String tableName = "tdm_run_balk_drop_clean_catalog";
        String tableTitle = "TDM Run Balk Drop Clean Catalog";
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
        createTestDataTableCatalog(projectId, systemId, environmentId, tableTitle, tableName);

        CommonResults executeResults = new DropResults() {{setTableName(tableName);}};
        BulkActionResult expectedBulkActionResult = new BulkActionResult(tableTitle, tableName,
                environmentName, executeResults);

        List<Future<BulkActionResult>> futures =
                bulkDataDropHandler.runBulkAction(session, executor, lazyEnvironments, bulkActionConfig, processId);
        BulkActionResult bulkActionResult = futures.get(0).get();

        Assertions.assertNull(catalogRepository.findByTableName(tableName));

        catalogRepository.deleteByTableName(tableName);
        deleteTestDataTableIfExists(tableName);
    }

    @Test
    public void runBulkAction_dropTable_returnException() throws Exception {
        final UUID projectId = UUID.randomUUID();
        long processId = java.lang.System.currentTimeMillis();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        String tableName = "tdm_run_balk_exception";
        String tableTitle = "TDM Run Balk Exception";
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
        createTestDataTableCatalog(projectId, systemId, environmentId, tableTitle, tableName);

        List<Future<BulkActionResult>> futures =
                bulkDataDropHandler.runBulkAction(session, executor, lazyEnvironments, bulkActionConfig, processId);
        BulkActionResult bulkActionResult = futures.get(0).get();
        Assertions.assertEquals(tableName, bulkActionResult.getTableName());
        deleteTestDataTableIfExists(tableName);
        catalogRepository.deleteByTableName(tableName);
    }

}

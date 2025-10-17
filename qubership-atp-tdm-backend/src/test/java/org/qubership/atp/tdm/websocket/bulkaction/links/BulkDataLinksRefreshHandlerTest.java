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

package org.qubership.atp.tdm.websocket.bulkaction.links;

import org.qubership.atp.tdm.AbstractTestDataTest;
import org.qubership.atp.tdm.model.LinkSetupResult;
import org.qubership.atp.tdm.model.bulkaction.BulkActionConfig;
import org.qubership.atp.tdm.model.bulkaction.BulkActionResult;
import org.qubership.atp.tdm.model.mail.bulkaction.BulkLinksRefreshMailSender;
import org.qubership.atp.tdm.model.table.TestDataTable;
import org.qubership.atp.tdm.model.table.column.TestDataTableColumn;
import org.qubership.atp.tdm.service.ColumnService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;

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

public class BulkDataLinksRefreshHandlerTest  extends AbstractTestDataTest {

    @Autowired
    ExecutorService executorService;

    @Autowired
    ColumnService columnService;

    @Autowired
    BulkLinksRefreshMailSender bulkLinksRefreshMailSender;

    WebSocketSession session;

    BulkDataLinksRefreshHandler bulkDataLinksRefreshHandler;

    @BeforeEach
    public void setUp() throws Exception {
        bulkDataLinksRefreshHandler = new BulkDataLinksRefreshHandler(executorService, catalogRepository,
                environmentsService, columnService, bulkLinksRefreshMailSender, currentTime, lockManager, tdmMdcHelper);

        when(environmentsService.getConnectionsSystemById(any(), any())).thenReturn(Collections.singletonList(httpConnection));
    }

    @AfterEach
    public void after() {
        deleteTestDataTableIfExists("tdm_run_balk_link_setup");
        catalogRepository.deleteAll();
    }

    @Test
    public void runBulkAction_saveRowNameInColumnRepository_bulkActionIsCorrect() throws Exception {
        final UUID projectId = UUID.randomUUID();
        long processId = java.lang.System.currentTimeMillis();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        String tableName = "tdm_run_balk_link_setup";
        String tableTitle = "TDM Run Balk Link Setup";
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

        TestDataTable testDataTable = testDataService.getTestData(tableName);
        List<TestDataTableColumn> columns = testDataTable.getColumns();

        for (TestDataTableColumn column: columns) {
            if(column.getIdentity().getColumnName().equals("sim")) {
                columnService.setupColumnLinks(false, projectId, systemId, tableName,
                        column.getIdentity().getColumnName(), "?objectId=", false);
            }
        }

        String row = "sim";

        BulkActionResult expectedBulkActionResult = new BulkActionResult(tableTitle, tableName, environmentName,
                new LinkSetupResult(row));

        List<Future<BulkActionResult>> futures = bulkDataLinksRefreshHandler
                .runBulkAction(session, executor, lazyEnvironments, bulkActionConfig, processId);

        BulkActionResult actualBulkActionResult = futures.get(0).get();

        Assertions.assertEquals(expectedBulkActionResult, actualBulkActionResult);

        deleteTestDataTableIfExists(tableName);
        catalogRepository.deleteByTableName(tableName);
    }

}

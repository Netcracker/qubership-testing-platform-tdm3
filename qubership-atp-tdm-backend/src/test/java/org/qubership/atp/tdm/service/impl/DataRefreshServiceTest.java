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

import org.qubership.atp.tdm.AbstractTestDataTest;
import org.qubership.atp.tdm.env.configurator.model.Project;
import org.qubership.atp.tdm.model.TestDataTableCatalog;
import org.qubership.atp.tdm.model.refresh.RefreshResults;
import org.qubership.atp.tdm.model.refresh.TestDataRefreshConfig;
import org.qubership.atp.tdm.model.table.TestDataTable;
import org.qubership.atp.tdm.service.DataRefreshService;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class DataRefreshServiceTest extends AbstractTestDataTest {

    private static final UUID projectId = UUID.randomUUID();

    private static final Project project = new Project() {{
        setName("Test Data Refresh Project");
        setId(projectId);
        setEnvironments(Collections.singletonList(environment));
    }};

    @MockBean
    private MetricService metricServiceMock;

    @Autowired
    private DataRefreshService dataRefreshService;

    @BeforeEach
    public void setUp() throws Exception {
        when(environmentsService.getLazyProjectById(any())).thenReturn(lazyProject);
        when(environmentsService.getLazyEnvironment(any())).thenReturn(lazyEnvironment);
        when(environmentsService.getConnectionsSystemById(any(), any())).thenReturn(connections);

        when(gitEnvironmentsService.getLazyProjectById(any())).thenReturn(lazyProject);
        when(gitEnvironmentsService.getLazyEnvironment(any())).thenReturn(lazyEnvironment);
        when(gitEnvironmentsService.getConnectionsSystemById(any(), any())).thenReturn(connections);

        when(gitEnvironmentsService.getEnvNameById(any())).thenReturn(environmentName);
        when(gitEnvironmentsService.getFullSystemByName(any(), any())).thenReturn(system);
    }


    @Test
    public void saveRefreshConfig_saveConfig_dataRefreshConfigigSaved() throws Exception {
        Integer queryTimeout = 30;
        String query = "select sim from tdm_test_data_refresh_unoccupied_table";
        String tableTitle = "TDM Test Data Refresh Save Config";
        String tableName = "tdm_test_data_refresh_save_config";
        createTestDataTableCatalog(projectId, systemId, environmentId, tableTitle, tableName, query);
        TestDataRefreshConfig config =
                new TestDataRefreshConfig(UUID.randomUUID(), false, "0 0/5 * * * ?", false, queryTimeout);
        dataRefreshService.saveRefreshConfig(tableName, queryTimeout, config);
        TestDataTableCatalog catalog = catalogRepository.findByTableName(tableName);
        Assertions.assertEquals(config.getId(), catalog.getRefreshConfigId());
        TestDataRefreshConfig actualConfig = dataRefreshService.getRefreshConfig(config.getId());
        catalogRepository.deleteByTableName(tableName);
        Assertions.assertEquals(config, actualConfig);
    }

    @Test
    public void runRefresh_runEnabledRefreshConfig_returnsRefreshResults() throws Exception {
        Integer queryTimeout = 30;
        createTestDataTable("tdm_test_data_refresh_source_table");
        String tableTitle = "TDM Test Data Run Refresh";
        String tableName = "tdm_test_data_run_refresh";
        createTestDataTableCatalog(projectId, systemId, environmentId, tableTitle, tableName);
        createTestDataTable(tableName);
        when(gitEnvironmentsService.getFullProject(any())).thenReturn(project);
        when(environmentsService.getFullProject(any())).thenReturn(project);
        testDataService.importSqlTestData(projectId,
                Collections.singletonList(environmentId), system.getName(),
                tableTitle, "select \"sim\" from tdm_test_data_refresh_source_table", queryTimeout);
        createTestDataTableCatalog(projectId, systemId, environmentId, tableTitle, tableName);
        TestDataRefreshConfig config = new TestDataRefreshConfig(UUID.randomUUID(), true, "0 0/5 * * * ?", false, queryTimeout);
        dataRefreshService.saveRefreshConfig(tableName, queryTimeout, config);
        RefreshResults refreshResults = dataRefreshService.runRefresh(config.getId());
        deleteTestDataTableIfExists("tdm_test_data_refresh_source_table");
        deleteTestDataTableIfExists(tableName);
        catalogRepository.deleteByTableName(tableName);
        Assertions.assertEquals(new RefreshResults(6), refreshResults);
    }

    @Test
    public void runRefresh_runDisabledRefreshConfig_returnsEmptyRefreshResults() throws Exception {
        Integer queryTimeout = 30;
        String query = "select sim from tdm_test_data_refresh_unoccupied_table";
        String tableName = "tdm_test_data_run_refresh_disabled";
        createTestDataTableCatalog(projectId, systemId, environmentId,
                "TDM Test Data Run Refresh Disabled", tableName, query);
        when(gitEnvironmentsService.getFullProject(any())).thenReturn(project);
        TestDataRefreshConfig config = new TestDataRefreshConfig(UUID.randomUUID(), false, "0 0/5 * * * ?", false, queryTimeout);
        dataRefreshService.saveRefreshConfig(tableName, queryTimeout, config);
        RefreshResults refreshResults = dataRefreshService.runRefresh(config.getId());
        catalogRepository.deleteByTableName(tableName);
        Assertions.assertEquals(new RefreshResults(0), refreshResults);
    }

    @Test
    public void runRefresh_runUnoccupiedRefresh_returnsRefreshResults() throws Exception {
        TestDataTable sourceTable = createTestDataTable("tdm_test_data_refresh_unoccupied_table");
        String query = "select sim from tdm_test_data_refresh_unoccupied_table";
        String tableName = "tdm_test_data_unoccupied_refresh";

        createTestDataTable(tableName);
        createTestDataTableCatalog(projectId, systemId, environmentId,
                "TDM Test Data Run Refresh Disabled", tableName, query);
        TestDataTable table = testDataService.getTestData(tableName);
        int countOccupiedRows = 2;

        List<UUID> rowIdsToOccupy = extractRowIds(table.getData().subList(0, 2));
        testDataService.occupyTestData(tableName, "TestUser1DRST", rowIdsToOccupy);
        when(gitEnvironmentsService.getFullProject(any())).thenReturn(project);
        int actualOccupiedRows = testDataService.
                getTestData(tableName, null, null, null, null, true).getData().size();
        Assertions.assertEquals(countOccupiedRows, actualOccupiedRows);
        int actualRows = testDataService.getTestData(tableName).getRecords() + countOccupiedRows;
        deleteTestDataTableIfExists("tdm_test_data_refresh_unoccupied_table");
        deleteTestDataTableIfExists(tableName);
        catalogRepository.deleteByTableName(tableName);
        Assertions.assertEquals(sourceTable.getData().size(), actualRows);
    }

    @Test
    public void refreshConfig_saveConfigWithOutTableNams_returnError() {
        String tableName = "";
        String cron = "0 0 9 ? * *";
        try {
            TestDataRefreshConfig config = new TestDataRefreshConfig(UUID.randomUUID(), true,
                    cron, false, 30);
            dataRefreshService.saveRefreshConfig(tableName, 30, config);
        } catch (Exception e) {
            Assertions.assertEquals("Table Name is null", e.getMessage());
        }
    }

}

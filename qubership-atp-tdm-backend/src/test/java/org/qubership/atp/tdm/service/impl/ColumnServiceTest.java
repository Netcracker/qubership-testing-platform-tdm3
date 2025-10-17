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
import org.qubership.atp.tdm.model.ColumnType;
import org.qubership.atp.tdm.model.LinkSetupResult;
import org.qubership.atp.tdm.model.table.column.TestDataTableColumn;
import org.qubership.atp.tdm.model.table.column.TestDataTableColumnIdentity;
import org.qubership.atp.tdm.service.ColumnService;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class ColumnServiceTest extends AbstractTestDataTest {

    @Autowired
    protected ColumnService columnService;

    private static final UUID projectId = UUID.randomUUID();

    private static final Project project = new Project() {{
        setName("Test Cleanup Project");
        setId(projectId);
        setEnvironments(Collections.singletonList(environment));
    }};

    @BeforeEach
    public void setUp() {
        when(environmentsService.getLazySystems(any())).thenReturn(Collections.singletonList(lazySystem));
        when(environmentsService.getConnectionsSystemById(any(), any())).thenReturn(connections);
    }


    @Test
    public void columnService_getColumnLink_returnLink() {
        String expectedLink = "http://localhost:8080//?objectId=";
        String actualLink = columnService.getColumnLink(projectId, systemId, "?objectId=");
        Assertions.assertEquals(expectedLink, actualLink);
    }

    @Test
    public void columnService_setupColumnLinks_linksSet() {
        String tableName = "tdm_test_setup_column_link";
        String tableTitle = "Tdm Test Setup Column Link";
        createTestDataTable(tableName);
        createTestDataTableCatalog(projectId, systemId, environmentId, tableTitle, tableName);
        List<TestDataTableColumn> expectedTestDataTableColumns = new ArrayList<>();
        TestDataTableColumn testDataTableColumn = new TestDataTableColumn(new TestDataTableColumnIdentity(tableName,
                "sim"), ColumnType.LINK, null,
                "http://localhost:8080//?objectId=", false);
        expectedTestDataTableColumns.add(testDataTableColumn);

        columnService.setupColumnLinks(false, projectId, systemId, tableName,
                "sim", "?objectId=", false);

        List<TestDataTableColumn> actualTestDataTableColumns = columnRepository.findAllByIdentityTableName(tableName);
        deleteTestDataTableIfExists(tableName);
        catalogRepository.deleteByTableName(tableName);
        Assertions.assertEquals(expectedTestDataTableColumns, actualTestDataTableColumns);
    }

    @Test
    public void columnService_setupBulkColumnLinks_linksSet() {
        String tableName = "tdm_test_setup_column_link_bulk";
        String tableTitle = "Tdm Test Setup Column Link Bulk";
        createTestDataTable(tableName);
        createTestDataTableCatalog(projectId, systemId, environmentId, tableTitle, tableName);
        List<TestDataTableColumn> expectedTestDataTableColumns = new ArrayList<>();
        TestDataTableColumn testDataTableColumn = new TestDataTableColumn(new TestDataTableColumnIdentity(tableName,
                "sim"), ColumnType.LINK, null,
                "http://localhost:8080//?objectId=", true);
        expectedTestDataTableColumns.add(testDataTableColumn);

        columnService.setupColumnLinks(true, projectId, systemId, tableName,
                "sim", "?objectId=", false);

        List<TestDataTableColumn> actualTestDataTableColumns = columnRepository.findAllByIdentityTableName(tableName);
        deleteTestDataTableIfExists(tableName);
        catalogRepository.deleteByTableName(tableName);
        Assertions.assertEquals(expectedTestDataTableColumns, actualTestDataTableColumns);
    }

    @Test
    public void columnService_setupColumnLinksWithValueFromTableCell_linksSet() {
        String tableName = "tdm_test_setup_column_link_with_value_from_table_cell";
        String tableTitle = "Tdm Test Setup Column Link With Value From Table Cell";
        createTestDataTable(tableName);
        createTestDataTableCatalog(projectId, systemId, environmentId, tableTitle, tableName);
        List<TestDataTableColumn> expectedTestDataTableColumns = new ArrayList<>();
        TestDataTableColumn testDataTableColumn = new TestDataTableColumn(new TestDataTableColumnIdentity(tableName,
                "sim"), ColumnType.LINK, null,
                "", false);
        expectedTestDataTableColumns.add(testDataTableColumn);

        columnService.setupColumnLinks(false, projectId, systemId, tableName,
                "sim", "", true);

        List<TestDataTableColumn> actualTestDataTableColumns = columnRepository.findAllByIdentityTableName(tableName);
        deleteTestDataTableIfExists(tableName);
        catalogRepository.deleteByTableName(tableName);
        Assertions.assertEquals(expectedTestDataTableColumns, actualTestDataTableColumns);
    }

    @Test
    public void columnService_setupColumnLink_linkSetupResult() {
        String tableName = "tdm_test_linkSetupResult";
        String tableTitle = "TDM Test Link Setup Result";
        createTestDataTable(tableName);
        createTestDataTableCatalog(projectId, systemId, environmentId, tableTitle, tableName);

        columnService.setupColumnLinks(true, projectId, systemId, tableName,
                "sim", "?objectId=", false);

        LinkSetupResult expected = columnService.setUpLinks(projectId, systemId, tableName);

        LinkSetupResult actual = new LinkSetupResult();
        actual.setColumnNames("sim");
        deleteTestDataTableIfExists(tableName);
        catalogRepository.deleteByTableName(tableName);
        Assertions.assertEquals(expected, actual);
    }
}

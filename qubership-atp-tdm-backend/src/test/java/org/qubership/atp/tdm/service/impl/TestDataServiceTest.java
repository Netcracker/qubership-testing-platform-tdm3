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

import static java.lang.String.format;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Disabled;
import org.qubership.atp.tdm.AbstractTestDataTest;
import org.qubership.atp.tdm.ExcelRowsReader;
import org.qubership.atp.tdm.env.configurator.exceptions.internal.TdmEnvDbConnectionException;
import org.qubership.atp.tdm.exceptions.db.TdmDbJdbsTemplateException;
import org.qubership.atp.tdm.exceptions.internal.TdmEnvironmentSystemException;
import org.qubership.atp.tdm.model.DropResults;
import org.qubership.atp.tdm.exceptions.db.TdmDbRowNotFoundException;
import org.qubership.atp.tdm.model.EnvsList;
import org.qubership.atp.tdm.model.ImportTestDataStatistic;
import org.qubership.atp.tdm.model.ProjectInformation;
import org.qubership.atp.tdm.model.TestDataTableCatalog;
import org.qubership.atp.tdm.model.table.TableColumnValues;
import org.qubership.atp.tdm.model.table.TestDataTable;
import org.qubership.atp.tdm.model.table.TestDataTableFilter;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.InvalidDataAccessApiUsageException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

public class TestDataServiceTest extends AbstractTestDataTest {

    private static final String TEST_DATASET = "DataSet.xlsx";
    private static final String TEST_DATASET_SMALL = "DataSetSmall.xlsx";
    private static final String TABLE_TO_EXCEL_FILE = "TableToExcel.xlsx";
    private static final String TABLE_TO_CSV_FILE = "TableToCsv.csv";
    private static final int CREATED_WHEN_COLUMN_INDEX = 3;
    private static final int OCCUPIED_DATE_COLUMN_INDEX = 1;

    @BeforeEach
    public void setUp() throws Exception {
        when(environmentsService.getLazyProjectById(any())).thenReturn(lazyProject);
        when(environmentsService.getLazyEnvironment(any())).thenReturn(lazyEnvironment);

        when(environmentsService.getEnvNameById(any())).thenReturn("Test Environment");
        when(environmentsService.getLazyEnvironments(any())).thenReturn(Collections.singletonList(lazyEnvironment));
        when(environmentsService.getConnectionsSystemById(any(), any())).thenReturn(connections);

        when(gitEnvironmentsService.getEnvNameById(any())).thenReturn(environmentName);
        when(gitEnvironmentsService.getFullSystemByName(any(), any())).thenReturn(system);
    }


    @Test
    public void testDataTablesCatalog_getCatalogOnlyProjectSelected_returnNormalCatalog() {
        TestDataTableCatalog catalog = createTestDataTableCatalog(UUID.randomUUID(), null, null,
                "Test Table Only Project", "table_name_project");
        List<TestDataTableCatalog> catalogList = testDataService.getTestDataTablesCatalog(catalog.getProjectId(),
                catalog.getSystemId());
        catalogRepository.deleteByTableName("table_name_project");

        Assertions.assertEquals(catalogList.get(0).getTableName(), catalog.getTableName());
    }

    @Test
    public void testDataTablesCatalog_getCatalogSystemAndProjectSelected_returnNormalCatalog() {
        TestDataTableCatalog catalog = createTestDataTableCatalog(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "Test Table, Project and System",
                "table_name_project_system");
        List<TestDataTableCatalog> catalogList = testDataService.getTestDataTablesCatalog(catalog.getProjectId(),
                catalog.getSystemId());
        catalogRepository.deleteByTableName("table_name_project_system");

        Assertions.assertEquals(catalogList.get(0).getTableName(), catalog.getTableName());
    }

    @Test
    public void testDataTablesCatalog_getCatalogNotExistSystemAndProjectSelected_returnEmptyArray() {
        String tableName = "table_name_project_and_not_exist_system";
        String tableTitle = "Test Table Project and Not Exist System";
        UUID notExistSystemId = UUID.randomUUID();
        TestDataTableCatalog catalog = createTestDataTableCatalog(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), tableTitle, tableName);
        List<TestDataTableCatalog> tablesCatalog = testDataService
                .getTestDataTablesCatalog(catalog.getProjectId(), notExistSystemId);
        catalogRepository.deleteByTableName(tableName);
        Assertions.assertEquals(tablesCatalog.size(), 0);
    }

    @Test
    public void testDataTablesCatalog_getCatalogNotExistProjectSelected_returnEmptyArray() {
        String tableName = "table_name_not_exist_project";
        String tableTitle = "Test Table Not Exist Project";
        UUID notExistProjectId = UUID.randomUUID();
        createTestDataTableCatalog(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                tableTitle, tableName);
        List<TestDataTableCatalog> tablesCatalog = testDataService
                .getTestDataTablesCatalog(notExistProjectId, null);
        catalogRepository.deleteByTableName(tableName);
        Assertions.assertEquals(tablesCatalog.size(), 0);
    }

    @Test
    public void testDataTable_getTableByTableName_returnNormalTestDataTable() {
        String tableName = "tdm_test_get_test_data";
        TestDataTable expectedTable = createTestDataTable(tableName);
        TestDataTable actualTable = testDataService.getTestData(tableName);
        for (int rowNum = 0; rowNum < expectedTable.getData().size() - 1; rowNum++) {
            Map<String, Object> expectedRow = expectedTable.getData().get(rowNum);
            Map<String, Object> actualRow = actualTable.getData().get(rowNum);
            for (String key : expectedRow.keySet()) {
                Object expectedValue = expectedRow.get(key);
                Object actualValue = actualRow.get(key);
                Assertions.assertEquals(expectedValue, actualValue);
            }
        }

        deleteTestDataTableIfExists(tableName);
    }

    @Test
    public void testDataTable_getTableWithAppliedFilterTypeContains_returnSelectedWithFilterTable() {
        String tableName = "tdm_test_get_test_data_filter_contains";
        createTestDataTable(tableName, true);
        List<TestDataTableFilter> filters = new ArrayList<>();
        TestDataTableFilter simColumnFilter = new TestDataTableFilter("sim", "contains",
                Collections.singletonList("0126072004014082"), false);
        filters.add(simColumnFilter);
        TestDataTable actualTable = testDataService.getTestData(tableName, null, null,
                filters, null, false);

        deleteTestDataTableIfExists(tableName);

        Assertions.assertEquals("8901260720040140822", actualTable.getData().get(0).get("sim"));
    }

    @Test
    public void testDataTable_getTableWithAppliedFilterTypeEquals_returnSelectedWithFilterTable() {
        String tableName = "tdm_test_get_test_data_filter_equals";
        createTestDataTable(tableName, true);
        List<TestDataTableFilter> filters = new ArrayList<>();
        TestDataTableFilter simColumnFilter = new TestDataTableFilter("sim", "equals",
                Collections.singletonList("8901260720040140973"), false);
        filters.add(simColumnFilter);
        TestDataTable actualTable = testDataService.getTestData(tableName, null, null,
                filters, null, false);

        deleteTestDataTableIfExists(tableName);

        Assertions.assertEquals("8901260720040140973", actualTable.getData().get(0).get("sim"));
    }

    @Test
    public void testDataTable_getTableWithAppliedFilterTypeDate_returnSelectedDataWithFilterTable() {
        String tableName = "tdm_test_get_test_data_filter_date_positive";
        createTestDataTable(tableName, true);
        LocalDate currentDate = LocalDate.now();

        List<TestDataTableFilter> filters = new ArrayList<>();
        List<String> filterValues = Collections.singletonList(String.valueOf(currentDate));
        TestDataTableFilter fromColumnFilter = new TestDataTableFilter("CREATED_WHEN", "From",
                filterValues, false);
        filters.add(fromColumnFilter);
        TestDataTableFilter toColumnFilter = new TestDataTableFilter("CREATED_WHEN", "To",
                filterValues, false);
        filters.add(toColumnFilter);

        TestDataTable actualTable = testDataService.getTestData(tableName, null, null,
                filters, null, false);

        deleteTestDataTableIfExists(tableName);

        Assertions.assertEquals(6, actualTable.getData().size());
    }

    @Test
    public void testDataTable_getTableWithAppliedFilterTypeDate_returnSelectedNothingWithFilterTable() {
        String tableName = "tdm_test_get_test_data_filter_date_negative";
        createTestDataTable(tableName, true);
        LocalDate nextDate = LocalDate.now().plusDays(1);

        List<TestDataTableFilter> filters = new ArrayList<>();
        List<String> filterValues = Collections.singletonList(String.valueOf(nextDate));

        TestDataTableFilter fromColumnFilter = new TestDataTableFilter("CREATED_WHEN", "From",
                filterValues, false);
        filters.add(fromColumnFilter);
        TestDataTableFilter toColumnFilter = new TestDataTableFilter("CREATED_WHEN", "To",
                filterValues, false);
        filters.add(toColumnFilter);

        TestDataTable actualTable = testDataService.getTestData(tableName, null,
                null, filters, null, false);

        deleteTestDataTableIfExists(tableName);

        Assertions.assertTrue(actualTable.getData().isEmpty());
    }

    @Test
    public void testDataTable_getTableWithAppliedFilterTypeStartWith_returnSelectedWithFilterTable() {
        String tableName = "tdm_test_get_test_data_filter_start_with";
        createTestDataTable(tableName, true);
        List<TestDataTableFilter> filters = new ArrayList<>();
        TestDataTableFilter simColumnFilter = new TestDataTableFilter("sim", "startWith",
                Collections.singletonList("890126072004014082"), false);
        filters.add(simColumnFilter);
        TestDataTable actualTable = testDataService.getTestData(tableName, null, null,
                filters, null, false);

        deleteTestDataTableIfExists(tableName);

        Assertions.assertEquals("8901260720040140822", actualTable.getData().get(0).get("sim"));
    }

    @Test
    public void testDataTable_getTableWithFilterTypeCaseSensitive_returnSelectedWithFilterTable() {
        String tableName = "tdm_test_get_test_data_filter_case_sensitive";
        createTestDataTable(tableName, true);
        List<TestDataTableFilter> filters = new ArrayList<>();
        TestDataTableFilter simColumnFilter = new TestDataTableFilter("Assignment", "contains",
                Collections.singletonList("TeSt AutoMatioN"), true);
        filters.add(simColumnFilter);

        TestDataTable actualTable = testDataService.getTestData(tableName, null, null,
                filters, null, false);

        deleteTestDataTableIfExists(tableName);

        Assertions.assertEquals("TeSt AutoMatioN 2", actualTable.getData().get(0).get("Assignment"));
    }


    @Test
    public void testDataTable_getTableWithWrongFilterType_throwsException() {
        String tableName = "tdm_test_get_test_data_wrong_filter";
        createTestDataTable(tableName, true);
        List<TestDataTableFilter> filters = new ArrayList<>();
        TestDataTableFilter simColumnFilter = new TestDataTableFilter("sim", "end_with",
                Collections.singletonList("0126072004014082"), false);
        filters.add(simColumnFilter);
        try {
            testDataService.getTestData(tableName, null, null, filters, null, false);
        } catch (InvalidDataAccessApiUsageException e) {
            String message = "Unknown search condition type: end_with";
            Assertions.assertEquals(message, e.getMessage());
        } finally {
            deleteTestDataTableIfExists(tableName);
        }
    }

    @Test
    public void testDataTable_getTableUsingOffsetAndLimitMethod_valuesAreNotEqual() {
        String tableName = "tdm_test_get_test_data_offset_and_limit";
        TestDataTable expectedTable = createTestDataTable(tableName, true);
        TestDataTable actualTable = testDataService.getTestData(tableName, 3, 1, new ArrayList<>(),
                null, false);

        Map<String, Object> expectedRow = expectedTable.getData().get(3);
        Map<String, Object> actualRow = actualTable.getData().get(0);
        for (String key : expectedRow.keySet()) {
            Object expectedValue = expectedRow.get(key);
            Object actualValue = actualRow.get(key);
            assertThat("Values are not equal.", expectedValue, is(actualValue));
        }

        deleteTestDataTableIfExists(tableName);
    }

    @Test
    public void testDataService_getOccupiedRows_returnOnlyOccupiedRows() {
        String tableName = "tdm_test_get_occupied_rows_test_data_one";
        try {
            createTestDataTable(tableName);
            TestDataTable table = testDataService.getTestData(tableName);
            List<UUID> rowIdsToOccupy = extractRowIds(table.getData().subList(0, 2));
            createTestDataTableCatalog(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                    tableName, tableName);
            testDataService.occupyTestData(tableName, "TestUser1", rowIdsToOccupy);
            table = testDataService.getTestData(tableName, null, null, new ArrayList<>(), null, true);
            List<UUID> actualRowIds = extractRowIds(table.getData());
            Assertions.assertEquals(actualRowIds, rowIdsToOccupy);
        } catch (Exception e) {
            throw e;
        } finally {
            deleteTestDataTableIfExists(tableName);
            catalogRepository.deleteByTableName(tableName);
        }
    }

    @Test
    public void testDataService_getOccupiedRows_rowsAlreadyOccupied() {
        String tableName = "tdm_test_get_occupied_test_data";
        createTestDataTable(tableName);
        createTestDataTableCatalog(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                tableName, tableName);
        TestDataTable table = testDataService.getTestData(tableName);
        List<UUID> rowIdsToOccupy = extractRowIds(table.getData().subList(0, 2));
        testDataService.occupyTestData(tableName, "TestUser1", rowIdsToOccupy);
        try {
            testDataService.occupyTestData(tableName, "TestUser1", rowIdsToOccupy);
        } catch (RuntimeException re) {
            String message = "Error: object(s) already occupied. Please refresh table and try again.";
            Assertions.assertEquals(message, re.getMessage());
        } finally {
            deleteTestDataTableIfExists(tableName);
            catalogRepository.deleteByTableName(tableName);
        }
    }

    @Test
    public void importExcelTestData_addNewColumnsToTable_newColumnsCreationAndFill() throws IOException {
        UUID projectId = UUID.randomUUID();
        String tableTitle = "tdm_test_import_excel_new_columns_test_data";
        try {
            testDataService.importExcelTestData(projectId, null, null, tableTitle, false,
                    toMultipartFile(getResourcesFile(TEST_DATASET_SMALL)));
        } catch (Exception e) {
            Assertions.assertEquals(TdmEnvironmentSystemException.DEFAULT_MESSAGE, e.getMessage());
        }
    }

    @Test
    public void testDataService_deleteProjectFromCatalogue_catalogsDeleted() {
        TestDataTableCatalog catalog = createTestDataTableCatalog(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "Test Table, Project and System",
                "table_name_project_system");
        createTestDataTable(catalog.getTableName());
        projectInformationService.saveProjectInformation(new ProjectInformation(catalog.getProjectId(), "GMT+03:00",
                "d MMM yyyy", "hh:mm:ss a", 1));

        testDataService.deleteProjectFromCatalogue(catalog.getProjectId());

        deleteTestDataTableIfExists("table_name_project_system");
        catalogRepository.deleteByTableName("table_name_project_system");

        Assertions.assertTrue(catalogRepository.findAllByProjectId(catalog.getProjectId()).isEmpty());
    }

    private void validateImportTestDataResult(UUID projectId, UUID systemId, int rowCount, String tableTitle) {
        List<TestDataTableCatalog> tableCatalogs = testDataService.getTestDataTablesCatalog(projectId, systemId);

        TestDataTableCatalog result = tableCatalogs.stream()
                .filter(t -> tableTitle.equals(t.getTableTitle()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException(String.format("Table with title [%s] was not found.",
                        tableTitle)));

        Assertions.assertNotNull(result);
        TestDataTable testDataTable = testDataService.getTestData(result.getTableName());
        Assertions.assertEquals(rowCount, testDataTable.getRecords());
    }

    @Test
    public void testDataService_occupyTestData_successfullyOccupyOnlySelectedRows() {
        String tableName = "tdm_test_occupy_test_data";
        createTestDataTable(tableName);
        createTestDataTableCatalog(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                tableName, tableName);
        TestDataTable table = testDataService.getTestData(tableName);
        List<UUID> rowIdsToOccupy = extractRowIds(table.getData().subList(0, 2));

        testDataService.occupyTestData(tableName, "TestUser2", rowIdsToOccupy);
        table = testDataService.getTestData(tableName);
        List<UUID> actualRowIds = extractRowIds(table.getData());

        deleteTestDataTableIfExists(tableName);
        catalogRepository.deleteByTableName(tableName);

        Assertions.assertFalse(actualRowIds.containsAll(rowIdsToOccupy));
    }


    @Test
    public void occupyTestData_testDataAlreadyOccupied_returnErrorMessage() {
        String tableName = "tdm_test_occupy_test_data_error";
        createTestDataTable(tableName);
        createTestDataTableCatalog(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                tableName, tableName);
        TestDataTable table = testDataService.getTestData(tableName);
        List<UUID> rowIdsToOccupy = extractRowIds(table.getData().subList(0, 2));
        testDataService.occupyTestData(tableName, "TestUser2", rowIdsToOccupy);
        table = testDataService.getTestData(tableName);
        extractRowIds(table.getData());
        try {
            testDataService.occupyTestData(tableName, "TestUser3", rowIdsToOccupy);
        } catch (RuntimeException re) {
            String message = "Error: object(s) already occupied. Please refresh table and try again.";
            Assertions.assertEquals(message, re.getMessage());
        } finally {
            deleteTestDataTableIfExists(tableName);
            catalogRepository.deleteByTableName(tableName);
        }
    }


    @Test
    public void testDataService_releaseOccupiedData_successfulReleaseOnlySelectedRows() {
        String tableName = "tdm_test_release_test_data";
        String tableTitle = "TDM Test Release Test Data";
        createTestDataTable(tableName);
        createTestDataTableCatalog(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                tableTitle, tableName);

        TestDataTable table = testDataService.getTestData(tableName);
        List<UUID> rowIdsToOccupy = extractRowIds(table.getData().subList(0, 2));
        testDataService.occupyTestData(tableName, "TestUser3", rowIdsToOccupy);
        table = testDataService.getTestData(tableName);
        List<UUID> actualRowIds = extractRowIds(table.getData());

        Assertions.assertFalse(actualRowIds.containsAll(rowIdsToOccupy));

        testDataService.releaseTestData(tableName, rowIdsToOccupy);
        table = testDataService.getTestData(tableName);
        actualRowIds = extractRowIds(table.getData());

        Assertions.assertTrue(actualRowIds.containsAll(rowIdsToOccupy));

        deleteTestDataTableIfExists(tableName);
        catalogRepository.deleteByTableName(tableName);
    }



    @Test
    public void testDataService_deleteTableByTableName_successfulDeleteActualTable() {
        String tableName = "tdm_test_delete_test_data";
        String tableTitle = "TDM Test Delete Test Data";
        createTestDataTable(tableName);
        createTestDataTableCatalog(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),tableTitle, tableName);
        DropResults dropResults = testDataService.deleteTestData(tableName);
        Assertions.assertEquals(tableName, dropResults.getTableName());
        deleteTestDataTableIfExists(tableName);
        catalogRepository.deleteByTableName(tableName);
    }

    @Test
    public void testDataService_truncateDataInTableByTableName_successfullyTruncated() {
        String tableName = "tdm_test_truncate_table";
        UUID projectId = UUID.randomUUID();
        UUID systemId = UUID.randomUUID();
        createTestDataTable(tableName);
        createTestDataTableCatalog(projectId, systemId, UUID.randomUUID(),
                tableName, tableName);
        testDataService.truncateDataInTable(tableName, projectId, systemId);
        TestDataTable table = testDataService.getTestData(tableName);

        deleteTestDataTableIfExists(tableName);
        catalogRepository.deleteByTableName(tableName);

        Assertions.assertEquals(0, table.getRecords());
    }

    @Test
    public void testDataService_truncateDataInTableByTableName_tableNotFound() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            String tableName = "tdm_truncate_table_table_not_found";
            testDataService.truncateDataInTable(tableName, UUID.randomUUID(), UUID.randomUUID());
        });
    }


    @Test
    public void testDataService_deleteSeveralRowsInTableByIds_successfulDeleteOfSelectedRows() {
        String tableName = "tdm_test_delete_rows";
        createTestDataTable(tableName);
        createTestDataTableCatalog(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                tableName, tableName);
        TestDataTable table = testDataService.getTestData(tableName);
        List<UUID> rowIdsToDelete = extractRowIds(table.getData().subList(0, 2));
        testDataService.deleteTestDataTableRows(tableName, rowIdsToDelete);
        TestDataTable actualTable = testDataService.getTestData(tableName);
        List<UUID> actualRowIds = extractRowIds(actualTable.getData());
        Assertions.assertFalse(actualRowIds.containsAll(rowIdsToDelete));

        deleteTestDataTableIfExists(tableName);
        catalogRepository.deleteByTableName(tableName);
    }

    @Test
    public void testDataService_deleteSeveralOccupiedRowsInTableByIds_successfulDeleteOfSelectedRows() {
        String tableName = "tdm_test_delete_occupied_rows";
        createTestDataTable(tableName);
        createTestDataTableCatalog(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                tableName, tableName);
        TestDataTable table = testDataService.getTestData(tableName);
        List<UUID> rowIdsToDelete = extractRowIds(table.getData().subList(0, 2));
        testDataService.occupyTestData(tableName, "TestUser3", rowIdsToDelete);
        testDataService.deleteTestDataTableRows(tableName, rowIdsToDelete);
        TestDataTable actualTable = testDataTableRepository.getFullTestData(tableName);
        List<UUID> actualRowIds = extractRowIds(actualTable.getData());

        deleteTestDataTableIfExists(tableName);
        catalogRepository.deleteByTableName(tableName);

        Assertions.assertFalse(actualRowIds.containsAll(rowIdsToDelete));

    }

    @Test
    @Disabled
    public void testDataService_getTestDataTableAsExcelFile_returnDocumentWithEqualTable() throws IOException {
        String tableName = "tdm_test_get_table_as_excel";
        String tableTitle = "TDM Test Get Table As Excel";
        createTestDataTable(tableName);
        createTestDataTableCatalog(projectId, systemId, environmentId, tableTitle, tableName);
        TestDataTable table = testDataService.getTestData(tableName);
        List<UUID> rowIdsToOccupy = extractRowIds(table.getData().subList(0, 2));

        testDataService.occupyTestData(tableName, "TestUser3", rowIdsToOccupy);

        File erFile = getResourcesFile(TABLE_TO_EXCEL_FILE);
        File arFile = testDataService.getTestDataTableAsExcelFile(tableName);

        List<List<String>> erRows = ExcelRowsReader.read(erFile).collect(Collectors.toList());
        List<List<String>> erRowsPerformed = ExcelRowsReader.read(erFile).collect(Collectors.toList());
        List<List<String>> arRows = ExcelRowsReader.read(arFile).collect(Collectors.toList());
        for (int j = 1; j < erRows.size(); ++j) {
            erRowsPerformed.get(j).set(CREATED_WHEN_COLUMN_INDEX, arRows.get(j).get(CREATED_WHEN_COLUMN_INDEX));
            erRowsPerformed.get(j).set(OCCUPIED_DATE_COLUMN_INDEX, arRows.get(j).get(OCCUPIED_DATE_COLUMN_INDEX));
        }

        deleteTestDataTableIfExists(tableName);
        catalogRepository.deleteByTableName(tableName);

        Assertions.assertEquals(erRowsPerformed, arRows);
    }

    @Test
    public void testDataService_getTestDataTableAsCsvFile_returnDocumentWithEqualTable() throws IOException {
        String tableName = "tdm_test_get_table_as_csv";
        createTestDataTableCatalog(projectId, systemId, environmentId,
                "table_title", tableName);
        createTestDataTable(tableName);
        File erFile = getResourcesFile(TABLE_TO_CSV_FILE);
        File arFile = testDataService.getTestDataTableAsCsvFile(tableName);
        List<String> erRows = Files.readAllLines(erFile.toPath());
        List<String> erRowsPerformed = new ArrayList<>();
        List<String> arRows = Files.readAllLines(arFile.toPath());
        List<String[]> splitList = new ArrayList<>();
        for (String row : arRows) {
            splitList.add(row.split(","));
        }
        int j = 0;
        erRowsPerformed.add(erRows.get(0));
        for (String element : erRows.subList(1, erRows.size())) {
            String[] splitDemo = element.split(",");
            String[] splitResult = new String[splitDemo.length + 1];
            StringBuilder resultToReplace = new StringBuilder();
            java.lang.System.arraycopy(splitDemo, 0, splitResult, 0, CREATED_WHEN_COLUMN_INDEX);
            for (int i = CREATED_WHEN_COLUMN_INDEX; i < splitResult.length; ++i) {
                if (i == CREATED_WHEN_COLUMN_INDEX) {
                    splitResult[CREATED_WHEN_COLUMN_INDEX] = splitList.get(++j)[CREATED_WHEN_COLUMN_INDEX];
                } else {
                    splitResult[i] = splitDemo[i - 1];
                }
            }
            resultToReplace.append(splitResult[0]);
            for (int k = 1; k < splitResult.length; ++k) {
                resultToReplace.append(",").append(splitResult[k]);
            }
            erRowsPerformed.add(resultToReplace.toString());
        }
        deleteTestDataTableIfExists(tableName);

        Assertions.assertEquals(erRowsPerformed, arRows);
    }

     @Test
     public void testDataService_importSqlTestData_rowsImportedAndStatisticReturned() {
        createTestDataTable("tdm_test_import_sql_source_table");
        String targetTableTitle = "TDM Test import sql";
        String targetTableName = "tdm_test_import_sql";

        createTestDataTableCatalog(projectId, systemId, environmentId, targetTableTitle, targetTableName);
        createTestDataTable(targetTableName);

        List<ImportTestDataStatistic> expectedStatistic = new ArrayList<>();
        ImportTestDataStatistic statistic = new ImportTestDataStatistic(environmentName, null, 6);
        expectedStatistic.add(statistic);

        List<ImportTestDataStatistic> actualStatistics = testDataService.importSqlTestData(projectId,
                Collections.singletonList(environmentId), systemName,
                targetTableTitle, "select \"sim\" from tdm_test_import_sql_source_table", 30);

        deleteTestDataTableIfExists("tdm_test_import_sql_source_table");
        deleteTestDataTableIfExists("tdm_test_import_sql");
        catalogRepository.deleteByTableName("tdm_test_import_sql");

        Assertions.assertEquals(expectedStatistic, actualStatistics);
    }

    @Test
    @Disabled
    public void testDataService_importSqlTestDataInNewTable_rowsImportedAndStatisticReturned() {
        createTestDataTable("tdm_test_import_sql_source_table_new");
        String targetTableTitle = "TDM Test import sql new";

        List<ImportTestDataStatistic> expectedStatistic = new ArrayList<>();
        ImportTestDataStatistic statistic = new ImportTestDataStatistic(environmentName,
                null, 6);
        expectedStatistic.add(statistic);

        List<ImportTestDataStatistic> actualStatistics = testDataService.importSqlTestData(projectId,
                Collections.singletonList(environmentId), systemName,
                targetTableTitle, "select sim from tdm_test_import_sql_source_table_new", 30);

        String newTableName = catalogRepository
                .findByProjectIdAndSystemIdAndTableTitle(projectId, systemId, targetTableTitle).getTableName();

        deleteTestDataTableIfExists("tdm_test_import_sql_source_table_new");
        deleteTestDataTableIfExists(newTableName);
        catalogRepository.deleteByTableName(newTableName);

        Assertions.assertEquals(expectedStatistic, actualStatistics);

    }

    @Test
    @Disabled
    public void testDataService_importSqlTestDataEmptyRequest_returnErrorMessage() {
        createTestDataTable("tdm_test_import_sql_source_table_new_empty_request");

        String targetTableTitle = "TDM Test import sql new empty request";

        List<ImportTestDataStatistic> expectedStatistic = new ArrayList<>();
        ImportTestDataStatistic statistic = new ImportTestDataStatistic(environmentName,
                TdmDbRowNotFoundException.DEFAULT_MESSAGE, 0);
        expectedStatistic.add(statistic);

        List<ImportTestDataStatistic> actualStatistics = testDataService.importSqlTestData(projectId,
                Collections.singletonList(environmentId), systemName, targetTableTitle,
                "select sim from tdm_test_import_sql_source_table_new_empty_request where sim = '1'",
                30);

        deleteTestDataTableIfExists("tdm_test_import_sql_source_table_new_empty_request");

        Assertions.assertEquals(expectedStatistic, actualStatistics);
    }

    @Test
    public void testDataService_importSqlTestDataWrongEnvironment_returnErrorMessage() {
        when(environmentsService.getEnvNameById(any())).thenThrow(new RuntimeException());
        when(gitEnvironmentsService.getEnvNameById(any())).thenThrow(new RuntimeException());
        List<ImportTestDataStatistic> expectedStatistic = new ArrayList<>();
        String error = String.format("Environment: [%s] was not found.", environmentId);
        ImportTestDataStatistic statistic = new ImportTestDataStatistic(environmentId.toString(),
                error, 0);
        expectedStatistic.add(statistic);

        List<ImportTestDataStatistic> actualStatistics = testDataService.importSqlTestData(projectId,
                Collections.singletonList(environmentId), systemName,
                "TDM Test import sql wrong table", "select column from table", 30);

        Assertions.assertEquals(expectedStatistic, actualStatistics);
    }

    @Test
    @Disabled
    public void testDataService_importSqlTestDataWrongSystem_returnErrorMessage() {
        String systemName = "Wrong Test System";
        List<ImportTestDataStatistic> expectedStatistic = new ArrayList<>();
        String error = String.format("System with name[%s] for environment[%s] was not found.", systemName,
                environmentId);
        ImportTestDataStatistic statistic = new ImportTestDataStatistic(environmentName,
                error, 0);
        expectedStatistic.add(statistic);

        List<ImportTestDataStatistic> actualStatistics = testDataService.importSqlTestData(projectId,
                Collections.singletonList(environmentId), systemName,
                "TDM Test import sql wrong table", "select column from table", 30);

        Assertions.assertEquals(expectedStatistic, actualStatistics);
    }

    @Test
    @Disabled
    public void testDataService_importSqlTestDataWrongConnection_returnErrorMessage() throws Exception {
        // Mock the connection service to return connections without the 'DB' connection
        when(environmentsService.getConnectionsSystemById(any(), any())).thenReturn(Arrays.asList(dbConnectionErrorName));
        when(gitEnvironmentsService.getConnectionsSystemById(any(), any())).thenReturn(Arrays.asList(dbConnectionErrorName));

        List<ImportTestDataStatistic> expectedStatistic = new ArrayList<>();
        String error = format(TdmEnvDbConnectionException.DEFAULT_MESSAGE, "DB");
        ImportTestDataStatistic statistic = new ImportTestDataStatistic(environmentName,
                error, 0);
        expectedStatistic.add(statistic);

        List<ImportTestDataStatistic> actualStatistics = testDataService.importSqlTestData(projectId,
                Collections.singletonList(environmentId), systemName,
                "TDM Test import sql wrong table", "select column from table", 30);

        Assertions.assertEquals(expectedStatistic, actualStatistics);
    }

    @Test
    @Disabled
    public void testDataService_importSqlTestDataWrongCredentials_returnErrorMessage() {
        List<ImportTestDataStatistic> expectedStatistic = new ArrayList<>();
        ImportTestDataStatistic statistic = new ImportTestDataStatistic(environmentName,
                TdmDbJdbsTemplateException.DEFAULT_MESSAGE, 0);
        expectedStatistic.add(statistic);

        List<ImportTestDataStatistic> actualStatistics = testDataService.importSqlTestData(projectId,
                Collections.singletonList(environmentId), systemName,
                "TDM Test import sql wrong table", "select column from table", 30);

        Assertions.assertEquals(expectedStatistic, actualStatistics);
    }

    @Test
    public void testDataService_getTableEnvironments_returnNormalMultipleEnvsList() {
        UUID firstEnvironmentId = UUID.randomUUID();
        UUID secondEnvironmentId = UUID.randomUUID();

        String targetTableTitle = "TDM Test environments table";
        String firstTargetTableName = "tdm_test_first_environments_table";
        String secondTargetTableName = "tdm_test_second_environments_table";

        createTestDataTableCatalog(projectId, systemId, firstEnvironmentId, targetTableTitle, firstTargetTableName);
        createTestDataTableCatalog(projectId, systemId, secondEnvironmentId, targetTableTitle, secondTargetTableName);

        List<UUID> environmentIds = new ArrayList<>();
        environmentIds.add(firstEnvironmentId);
        environmentIds.add(secondEnvironmentId);
        EnvsList expectedEnvsList = new EnvsList(environmentIds);
        Collections.sort(expectedEnvsList.getItems());

        EnvsList actualEnvsList = testDataService.getTableEnvironments(projectId, targetTableTitle);
        Collections.sort(actualEnvsList.getItems());

        catalogRepository.deleteByTableName("tdm_test_first_environments_table");
        catalogRepository.deleteByTableName("tdm_test_second_environments_table");

        Assertions.assertEquals(expectedEnvsList.getItems().size(), actualEnvsList.getItems().size());
        Assertions.assertTrue(actualEnvsList.getItems().equals(expectedEnvsList.getItems()));
    }

    @Test
    public void testDataService_getTableEnvironments_returnEmptyEnvsList() {
        String targetTableTitle = "TDM Test environments table";
        EnvsList actualEnvsList = testDataService.getTableEnvironments(projectId, targetTableTitle);
        Assertions.assertEquals(0, actualEnvsList.getItems().size());
    }

    @Test
    public void testDataService_getTableRow_successfulResult() {
        String tableName = "tdm_test_get_test_data_row";
        String tableTitle = "TDM Test row table";

        createTestDataTable(tableName);
        createTestDataTableCatalog(projectId, systemId, environmentId, tableTitle, tableName);
        Map<String, Object> expectedRow = createTestDataTableRow(tableName);

        Map<String, Object> actualRow = testDataService.getTableRow(projectId, systemId, tableTitle, "sim",
                "8901260720040140811", false);

        deleteTestDataTableIfExists(tableName);
        catalogRepository.deleteByTableName(tableName);

        Assertions.assertEquals(expectedRow, actualRow);
    }

    @Test
    public void testDataService_getTableRow_failResultWrongColumnName() {
        String tableName = "tdm_test_get_test_data_row_wrong_column_name";
        String tableTitle = "TDM Test row table wrong column name";

        createTestDataTable(tableName);
        createTestDataTableCatalog(projectId, systemId, environmentId, tableTitle, tableName);

        try {
            testDataService.getTableRow(projectId, systemId, tableTitle, "test",
                    "8901260720040140811", false);
        } catch (RuntimeException re) {
            String message = String.format("Error while retrieving test data from table [%s] under project "
                    + "[%s] and system [%s].", tableTitle, projectId, systemId);
            Assertions.assertEquals(message, re.getMessage());
        } finally {
            deleteTestDataTableIfExists(tableName);
            catalogRepository.deleteByTableName(tableName);
        }
    }

    @Test
    public void testDataService_getTableRow_failResultWrongProject() {
        String tableName = "tdm_test_get_test_data_wrong_project";
        String tableTitle = "TDM Test row table wrong project";

        createTestDataTable(tableName);
        createTestDataTableCatalog(projectId, systemId, environmentId, tableTitle, tableName);
        UUID tempProjectId = UUID.randomUUID();

        try {
            testDataService.getTableRow(tempProjectId, systemId, tableTitle,
                    "sim", "8901260720040140811", false);
        } catch (RuntimeException re) {
            String message = String.format("Table [%s] under project "
                    + "[%s] and system [%s] wasn't found.", tableTitle, tempProjectId, systemId);
            Assertions.assertEquals(message, re.getMessage());
        } finally {
            deleteTestDataTableIfExists(tableName);
            catalogRepository.deleteByTableName(tableName);
        }
    }

    @Test
    public void testDataService_getTableRow_failResultWrongTableTitle() {
        String tableName = "tdm_test_get_test_data_wrong_table_title";
        String tableTitle = "TDM Test row table wrong table title";
        String wrongTableTitle = "wrong_table_title";

        createTestDataTable(tableName);
        createTestDataTableCatalog(projectId, systemId, environmentId, tableTitle, tableName);

        try {
        testDataService.getTableRow(projectId, systemId, wrongTableTitle, "sim",
                "8901260720040140811", false);
        } catch (RuntimeException re) {
            String message = String.format("Table [%s] under project [%s] and system [%s] wasn't found.",
                    wrongTableTitle, projectId, systemId);
            Assertions.assertEquals(message, re.getMessage());
        } finally {
            deleteTestDataTableIfExists(tableName);
            catalogRepository.deleteByTableName(tableName);
        }
    }

    @Test
    public void testDataService_changeTestDataTitle_testDataTitleChanged() {
        String tableName = "tdm_test_change_test_data_title";
        String tableTitle = "TDM Test. Change Test Data Title";
        createTestDataTableCatalog(projectId, systemId, environmentId, tableTitle, tableName);

        String newTableTitle = "TDM Test. Change Test Data Title. New Title";
        boolean updated = testDataService.changeTestDataTitle(tableName, newTableTitle);

        Assertions.assertTrue(updated);

        TestDataTableCatalog tableCatalog = testDataService.getTestDataTablesCatalog(projectId, systemId).stream()
                .filter(t -> tableName.equals(t.getTableName()))
                .findFirst()
                .orElseGet(TestDataTableCatalog::new);

        catalogRepository.deleteByTableName(tableName);

        Assertions.assertEquals(newTableTitle, tableCatalog.getTableTitle());
    }

    @Test
    @Disabled
    public void testDataService_createTableBySql_updateBySql_rowsUpdatedAndStatisticReturned() {
        String tableName = "tdm_test_sql_update_sql_source_table";
        createTestDataTable(tableName);

        String targetTableTitle = "TDM Test create by sql update sql";
        String targetTableName = "tdm_test_create_by_sql_update_sql";
        String query = "select NAME, OBJECT_ID from nc_objects where rownum < 5";
        createTestDataTableCatalog(projectId, systemId, environmentId, targetTableTitle, targetTableName, query);
        createSmallTestDataTable(targetTableName);

        ImportTestDataStatistic expectedStatistic =
                new ImportTestDataStatistic("Test Environment", null, 6);

        ImportTestDataStatistic actualStatistics = testDataService.updateTestDataBySql(projectId,
                environmentId, systemId, targetTableName,
                "select sim, environment from tdm_test_sql_update_sql_source_table where sim = ${'SIM'}",
                30);

        deleteTestDataTableIfExists(tableName);
        deleteTestDataTableIfExists(targetTableName);
        catalogRepository.deleteByTableName(targetTableName);

        Assertions.assertEquals(expectedStatistic, actualStatistics);
    }

    @Test
    @Disabled
    public void testDataService_create_excel_updateBySql_rowsUpdatedAndStatisticReturned() {
        String tableName = "tdm_test_excel_update_sql_source_table";
        createTestDataTable(tableName);

        String targetTableTitle = "TDM Test create by excel update sql";
        String targetTableName = "tdm_test_create_by_excel_update_sql";

        createTestDataTableCatalog(projectId, systemId, environmentId, targetTableTitle, targetTableName);
        createSmallTestDataTable(targetTableName);

        ImportTestDataStatistic expectedStatistic =
                new ImportTestDataStatistic(environmentName, null, 6);

        ImportTestDataStatistic actualStatistics = testDataService.updateTestDataBySql(projectId,
                environmentId, systemId, targetTableName,
                "select sim, environment from tdm_test_excel_update_sql_source_table where sim = ${'SIM'}",
                30);

        deleteTestDataTableIfExists(tableName);
        deleteTestDataTableIfExists(targetTableName);
        catalogRepository.deleteByTableName(targetTableName);

        Assertions.assertEquals(expectedStatistic, actualStatistics);
    }

    @Test
    public void testDataService_updateBySql_wrongColumnName() {
        String tableName = "tdm_test_excel_update_wrong_sql_source_table";
        createTestDataTable(tableName);

        String targetTableTitle = "TDM Test update sql";
        String targetTableName = "tdm_test_update_wrong_sql";

        createTestDataTableCatalog(projectId, systemId, environmentId, targetTableTitle, targetTableName);
        createSmallTestDataTable(targetTableName);

        ImportTestDataStatistic actualStatistics = testDataService.updateTestDataBySql(projectId,
                environmentId, systemId, targetTableName,
                "select sim, wrong_column from tdm_test_excel_update_wrong_sql_source_table where \"sim\"=${'SIM'}",
                30);

        deleteTestDataTableIfExists(tableName);
        deleteTestDataTableIfExists(targetTableName);
        catalogRepository.deleteByTableName(targetTableName);

        Assertions.assertTrue(actualStatistics.getError().contains("Error while updating table: " + targetTableName));
    }

    @Test
    public void testDataService_updateBySql_wrongQuery() {
        String tableName = "tdm_test_excel_update_wrong_query_sql_source_table";
        createTestDataTable(tableName);

        String targetTableTitle = "TDM Test update sql";
        String targetTableName = "tdm_test_update_wrong_query_sql";

        createTestDataTableCatalog(projectId, systemId, environmentId, targetTableTitle, targetTableName);
        createSmallTestDataTable(targetTableName);

        ImportTestDataStatistic actualStatistics = testDataService.updateTestDataBySql(projectId,
                environmentId, systemId, targetTableName, "sElEsT sim "
                        + "from tdm_test_excel_update_wrong_query_sql_source_table where \"sim\"=${'SIM'}",
                30);

        deleteTestDataTableIfExists(tableName);
        deleteTestDataTableIfExists(targetTableName);
        catalogRepository.deleteByTableName(targetTableName);

        Assertions.assertTrue(actualStatistics.getError().contains("Error while updating table: " + targetTableName));
    }

    @Test
    public void testDataService_createTableBySql_updateBySql_returnConnectionException() throws Exception {
        when(environmentsService.getConnectionsSystemById(any(), any())).thenReturn(Arrays.asList(dbConnectionErrorCredentials));
        when(gitEnvironmentsService.getConnectionsSystemById(any(), any())).thenReturn(Arrays.asList(dbConnectionErrorCredentials));
        String tableName = "tdm_test_sql_update_sql_source_table";
        createTestDataTable(tableName);

        String targetTableTitle = "TDM Test create by sql update sql";
        String targetTableName = "tdm_test_create_by_sql_update_sql";
        String query = "select NAME, OBJECT_ID from nc_objects where rownum < 5";
        createTestDataTableCatalog(projectId, systemId, environmentId, targetTableTitle, targetTableName, query);
        createSmallTestDataTable(targetTableName);

        String errorMessage = "Error while updating table: "+ targetTableName + ". Can not create connection for";

        ImportTestDataStatistic actualStatistics = testDataService.updateTestDataBySql(projectId,
                environmentId, systemId, targetTableName,
                "select sim, environment from tdm_test_sql_update_sql_source_table where sim = ${'SIM'}",
                30);

        deleteTestDataTableIfExists(tableName);
        deleteTestDataTableIfExists(targetTableName);
        catalogRepository.deleteByTableName(targetTableName);

        Assertions.assertTrue(actualStatistics.getError().contains(errorMessage));
    }

    @Test
    @Disabled
    public void testDataService_getDistinctColumns_returnDistinctList() {
        UUID system2 = UUID.randomUUID();
        UUID project2 = UUID.randomUUID();
        String tableName = "distinctTableName_" + java.lang.System.currentTimeMillis();
        String tableTitle = "distinctTableTitle";
        List<String> expectedColumnsList = new ArrayList<>(Arrays.asList("8901260720040140811","8901260720040140822","8901260720040140973","8901260720040141084","8901260720040140975","8901260720040141106"));
        createTestDataTable(tableName);
        createTestDataTableCatalog(project2, system2, environmentId, tableTitle, tableName);
        List<TableColumnValues> actualResult = testDataService.getDistinctTablesColumnValues(system2, environmentId,
                "sim");

        Assertions.assertTrue(expectedColumnsList.containsAll(actualResult.get(0).getValues())
                && actualResult.get(0).getValues().containsAll(expectedColumnsList));
        Assertions.assertEquals(tableName.toLowerCase(), actualResult.get(0).getTableName());
        Assertions.assertEquals(tableTitle, actualResult.get(0).getTableTitle());
    }

    @Test
    public void testDataService_getLinkWhereFullValueStoredIntoTableCell_returnFirstValueFromTableCell() {
        UUID projectId = UUID.randomUUID();
        UUID systemId = UUID.randomUUID();

        createTestDataTable("tdm_test_get_full_link_from_table_cell");

        String targetTableTitle = "TDM get full link from table cell";
        String targetTableName = "tdm_get_full_link_from_table_cell";

        createTestDataTableCatalog(projectId, systemId, environmentId, targetTableTitle, targetTableName);
        createSmallTestDataTable(targetTableName);
        String actualLink = testDataService.getPreviewLink(projectId, systemId, "endpoit",
                "Assignment", targetTableName, true);

        deleteTestDataTableIfExists("tdm_test_get_full_link_from_table_cell");
        deleteTestDataTableIfExists("tdm_get_full_link_from_table_cell");
        catalogRepository.deleteByTableName("tdm_get_full_link_from_table_cell");

        Assertions.assertEquals(actualLink, "51");
    }
}

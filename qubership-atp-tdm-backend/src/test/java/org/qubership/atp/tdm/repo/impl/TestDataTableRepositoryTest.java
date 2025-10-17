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

package org.qubership.atp.tdm.repo.impl;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.qubership.atp.tdm.model.ColumnValues;
import org.qubership.atp.tdm.model.table.TestDataTable;
import org.qubership.atp.tdm.model.table.column.TestDataTableColumn;
import org.qubership.atp.tdm.utils.TestDataTableConvertor;

import org.qubership.atp.tdm.AbstractTestDataTest;

public class TestDataTableRepositoryTest extends AbstractTestDataTest {

    @Test
    public void testDataTableRepository_getFullTestDataTest_extractedTableEqualToExpected() {
        String tableName = TestDataTableConvertor.generateTestDataTableName();
        TestDataTable expectedTable = createTestDataTable(tableName);

        TestDataTable actualTable = testDataTableRepository.getFullTestData(tableName);

        for (int rowNum = 0; rowNum < expectedTable.getData().size() - 1; rowNum++) {
            Map<String, Object> expectedRow = expectedTable.getData().get(rowNum);
            Map<String, Object> actualRow = actualTable.getData().get(rowNum);
            for (String key : expectedRow.keySet()) {
                Object expectedValue = expectedRow.get(key);
                Object actualValue = actualRow.get(key);
                assertThat("Values are not equal.", expectedValue, is(actualValue));
            }
        }
        deleteTestDataTableIfExists(tableName);
    }

    @Test
    public void testInsertRow_addNewColumn_newColumnExist() {
        String tableName = TestDataTableConvertor.generateTestDataTableName();
        createTestDataTable(tableName);
        TestDataTable testDataBeforeInsertRow = testDataService.getTestData(tableName);
        List<TestDataTableColumn> columnsBeforeInsertRow = testDataBeforeInsertRow.getColumns();
        List<String> rowBeforeInsert = columnsBeforeInsertRow.stream().map(t -> t.getIdentity().getColumnName()).collect(Collectors.toList());

        Map<String, Object> srt = new HashMap<>();
        srt.put("newColumn", null);
        List<Map<String, Object>> rows = new ArrayList();
        rows.add(srt);

        testDataTableRepository.insertRows(tableName, true, rows, false);

        TestDataTable testDataAfterInsertRow = testDataService.getTestData(tableName);
        List<TestDataTableColumn> columnsAfterInsertRow = testDataAfterInsertRow.getColumns();
        List<String> rowAfterInsert = columnsAfterInsertRow.stream().map(t -> t.getIdentity().getColumnName()).collect(Collectors.toList());

        rowAfterInsert.removeAll(rowBeforeInsert);
        Assertions.assertEquals(rowAfterInsert.get(0), "newColumn");

        deleteTestDataTableIfExists(tableName);
    }

    @Test
    public void testInsertRow_addEmptyArrayRow_returnException() {
        String tableName = TestDataTableConvertor.generateTestDataTableName();
        createTestDataTable(tableName);
        List<Map<String, Object>> rows = new ArrayList();
        try {
            testDataTableRepository.insertRows(tableName, true, rows, false);
        } catch (RuntimeException e) {
            String message = "There are no data to insert.";
            Assertions.assertEquals(message, e.getMessage());
        } finally {
            deleteTestDataTableIfExists(tableName);
        }
    }

    @Test
    public void tableRepository_getColumnDistinctValues_success() {
        String tableName = TestDataTableConvertor.generateTestDataTableName();
        createTestDataTable(tableName);
        try {
            ColumnValues rowId = testDataTableRepository
                    .getColumnDistinctValues(tableName, "ROW_ID", false);
            Assertions.assertEquals(rowId.getItems().size(), 6);
        } catch (Exception e) {
            throw e;
        } finally {
            deleteTestDataTableIfExists(tableName);
        }
    }


    @Test
    public void tableRepository_updateLastUsage_success() {
        String tableTitle = "tdm_update_last_usage";
        String tableName = TestDataTableConvertor.generateTestDataTableName();
        createTestDataTableCatalog(projectId, systemId, environmentId, tableTitle, tableName);
        testDataTableRepository.updateLastUsage(tableName);
        String expectedLastUsage = catalogRepository.findByTableName(tableName).getLastUsage().toString();
        catalogRepository.deleteByTableName(tableName);
        Assertions.assertTrue(expectedLastUsage.contains(new SimpleDateFormat("yyyy-MM-dd").format(new Date())));
    }
}

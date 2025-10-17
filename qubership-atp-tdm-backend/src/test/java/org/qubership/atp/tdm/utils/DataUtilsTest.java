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

package org.qubership.atp.tdm.utils;

import org.qubership.atp.tdm.AbstractTestDataTest;
import org.qubership.atp.tdm.exceptions.db.TdmDbCheckColumnNameException;
import org.qubership.atp.tdm.exceptions.db.TdmDbCheckQueryException;
import org.qubership.atp.tdm.exceptions.db.TdmDbCheckTableNameException;
import org.qubership.atp.tdm.model.TestDataTableCatalog;
import org.qubership.atp.tdm.model.cleanup.TestDataCleanupConfig;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

public class DataUtilsTest extends AbstractTestDataTest {

    private static final UUID projectId = UUID.randomUUID();
    private static final String SELECT_FOR_COMPARISION =
            "SELECT * FROM %s WHERE \"CREATED_WHEN\" >= TIMESTAMP '%s 23:59:59' AND \"ROW_ID\" = ${'ROW_ID'}::UUID";

    @AfterEach
    public void after() {
        catalogRepository.deleteByTableName("tdm_prepare_cleanup_config");
        deleteTestDataTableIfExists("tdm_prepare_cleanup_config");
    }

    @Test
    public void queryUtils_prepareCleanupByDateQuery_normalQuery() throws Exception {
        String tableName = "tdm_prepare_cleanup_config";
        TestDataTableCatalog table = createTestDataTableCatalog(projectId, systemId, environmentId,
                "TDM Prepare Cleanup Config", tableName);
        TestDataCleanupConfig testDataDateCleanupConfig = createDateCleanupConfig(table);
        LocalDate  expectedQuery = LocalDate.now().minusWeeks(3).minusDays(4);
        LocalDate actualQuery = DataUtils.calculateExpiredData(testDataDateCleanupConfig.getSearchDate());
        catalogRepository.deleteByTableName(tableName);
        Assertions.assertEquals(expectedQuery, actualQuery);
    }

    @Test
    public void dataUtils_checkTableName_success() {
        String tableNameGenerate = TestDataTableConvertor.generateTestDataTableName();
        String tableName = "tdm_api_test_check_table_name";
        DataUtils.checkTableName(tableNameGenerate);
        DataUtils.checkTableName(tableName);
    }

    @Test
    public void dataUtils_checkTableName_error() {
        String tableName = "tdm_api_test_check table_name";
        try {
            DataUtils.checkTableName(tableName);
        } catch (Exception e) {
            String message = String.format(TdmDbCheckTableNameException.DEFAULT_MESSAGE, tableName);
            Assertions.assertEquals(message, e.getMessage());
        }
    }

    @Test
    public void dataUtils_checkColumnName_success() {
        String columnName = "ROW_ID";
        DataUtils.checkColumnName(columnName);
    }

    @Test
    public void dataUtils_checkColumnName_error() {
        String columnName = "ROW_ID\"";
        try {
            DataUtils.checkColumnName(columnName);
        } catch (Exception e) {
            String message = String.format(TdmDbCheckColumnNameException.DEFAULT_MESSAGE, columnName);
            Assertions.assertEquals(message, e.getMessage());
        }
    }

    @Test
    public void dataUtils_checkQ_success() {
        String query = "select row_id from catalog;";
        DataUtils.checkQuery(query);
    }

    @Test
    public void dataUtils_checkQ_error() {
        String query = "select row_id from catalog; drop catalog";
        try {
            DataUtils.checkQuery(query);
        } catch (Exception e) {
            String message = TdmDbCheckQueryException.DEFAULT_MESSAGE;
            Assertions.assertEquals(message, e.getMessage());
        }
    }
}

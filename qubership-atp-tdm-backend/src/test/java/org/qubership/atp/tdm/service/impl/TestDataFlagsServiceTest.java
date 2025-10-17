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
import org.qubership.atp.tdm.model.table.TestDataFlagsTable;
import org.qubership.atp.tdm.repo.TestDataColumnFlagsRepository;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TestDataFlagsServiceTest extends AbstractTestDataTest {

    @Autowired
    protected TestDataFlagsServiceImpl testDataFlagsService;

    @Autowired
    protected TestDataColumnFlagsRepository testDataColumnFlagsRepository;

    @Test
    public void testDataFlagsService_setValidateUnoccupiedResourcesFlag_flagSet() {
        final UUID projectId = UUID.randomUUID();
        String tableName = "tdm_test_data_setup_validate_unoccupied_resources_flag";
        String tableTitle = "Tdm Test Data Setup Validate Unoccupied Resources Flag";
        createTestDataTable(tableName);
        createTestDataTableCatalog(projectId, systemId, environmentId, tableTitle, tableName);
        TestDataFlagsTable expectedTestDataFlagsTable = new TestDataFlagsTable(tableName, false);
        testDataFlagsService.setValidateUnoccupiedResourcesFlag(tableName,false, false);
        TestDataFlagsTable actualTestDataFlagsTable = testDataColumnFlagsRepository.findRowByTableName(tableName);

        catalogRepository.deleteByTableName(tableName);
        deleteTestDataTableIfExists(tableName);
        Assertions.assertEquals(expectedTestDataFlagsTable, actualTestDataFlagsTable);
    }

    @Test
    public void testDataFlagsService_bulkSetValidateUnoccupiedResourcesFlag_flagsSet() {
        List<String> tableNames = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            tableNames.add(UUID.randomUUID().toString());
        }
        tableNames.forEach(tableName -> createTestDataTableCatalog(projectId,
                systemId,
                environmentId,
                "bulkFlagsSet",
                tableName));
        testDataFlagsService.setValidateUnoccupiedResourcesFlag(tableNames.get(0), false, true);
        int flagsTablesSize =
                (int) tableNames.stream()
                        .map(tableName -> testDataColumnFlagsRepository.findRowByTableName(tableName)).count();

        Assertions.assertEquals(tableNames.size(), flagsTablesSize);
    }
}

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

package org.qubership.atp.tdm.benchmarks.facades;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.annotation.Nonnull;

import org.apache.poi.openxml4j.opc.OPCPackage;
import org.qubership.atp.tdm.benchmarks.utils.Helper;
import org.springframework.web.multipart.MultipartFile;

import com.google.common.base.Strings;
import org.qubership.atp.tdm.model.ImportTestDataStatistic;
import org.qubership.atp.tdm.model.table.TestDataTable;
import org.qubership.atp.tdm.repo.TestDataTableRepository;
import org.qubership.atp.tdm.repo.impl.loader.TestDataExcelLoader;
import org.qubership.atp.tdm.service.TestDataService;
import org.apache.commons.lang3.RandomStringUtils;

public class GeneralFacade {

    private static final String TEST_DATA_TABLE_DEFAULT = "tdm_benchmark_test_data_default";
    public static final String TEST_DATA_SEARCH_VALUE = "8901260720040140973";

    protected TestDataService testDataService;
    protected TestDataTableRepository testDataTableRepository;

    public GeneralFacade(@Nonnull TestDataService testDataService,
                         @Nonnull TestDataTableRepository testDataTableRepository) {
        this.testDataService = testDataService;
        this.testDataTableRepository = testDataTableRepository;
    }

    public void createTestDataTable(String tableName) {
        tableName = Strings.isNullOrEmpty(tableName) ? TEST_DATA_TABLE_DEFAULT : tableName;
        testDataTableRepository.saveTestData(tableName, false, buildTestDataTableWithSpecificData());
    }

    public String occupyTestData(String tableName) {
        tableName = Strings.isNullOrEmpty(tableName) ? TEST_DATA_TABLE_DEFAULT : tableName;
        TestDataTable table = testDataService.getTestData(tableName);
        List<UUID> rowIdsToOccupy = Helper.extractRowIds(table.getData().subList(0, 5));
        testDataService.occupyTestData(tableName, "TestUser", rowIdsToOccupy);
        return "Finished";
    }

    public String releaseTestData(String tableName) {
        tableName = Strings.isNullOrEmpty(tableName) ? TEST_DATA_TABLE_DEFAULT : tableName;
        TestDataTable table = testDataService.getTestData(tableName);
        List<UUID> rowIdsToRelease = Helper.extractRowIds(table.getData().subList(0, 5));
        testDataService.releaseTestData(tableName, rowIdsToRelease);
        return "Finished";
    }

    public File getTestDataTableAsExcelFile(String tableName) throws IOException {
        return testDataService.getTestDataTableAsExcelFile(tableName);
    }

    public File getTestDataTableAsCsvFile(String tableName) throws IOException {
        return testDataService.getTestDataTableAsCsvFile(tableName);
    }

    protected TestDataTable buildTestDataTableWithSpecificData() {
        TestDataTable table = new TestDataTable();
        List<String> columns = Arrays.asList("SIM", "Status", "Partner", "Partner category",
                "Partner ID", "Operator ID", "Environment", "Assignment");
        table.setColumns(Helper.getTestDataTableColumns(columns));
        List<Map<String, Object>> data = new ArrayList<>();
        for (int i = 0; i < 499; i++) {
            String number = RandomStringUtils.random(19, false, true);
            data.add(Helper.buildTestDataTableRow(columns, Arrays.asList(number, "51", "CINTEX",
                    "MVNO", "1", "2500", "ZLAB08", "Test Automation")));
        }
        data.add(Helper.buildTestDataTableRow(columns, Arrays.asList(TEST_DATA_SEARCH_VALUE, "51", "CINTEX",
                "MVNO", "1", "2500", "ZLAB08", "Test Automation")));
        for (int i = 0; i < 500; i++) {
            String number = RandomStringUtils.random(19, false, true);
            data.add(Helper.buildTestDataTableRow(columns, Arrays.asList(number, "51", "CINTEX",
                    "MVNO", "1", "2500", "ZLAB08", "Test Automation")));
        }
        table.setData(data);
        table.setRecords(1000);
        return table;
    }

    public TestDataTable importExcelTestData(@Nonnull String fileName) throws IOException {
        MultipartFile file = Helper.toMultipartFile(Helper.getResourcesFile(fileName));
        try (OPCPackage opcPackage = OPCPackage.open(file.getInputStream())) {
            TestDataExcelLoader loader = new TestDataExcelLoader(opcPackage);
            return loader.process();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public List<ImportTestDataStatistic> importExcelTestDataViaService(@Nonnull String fileName,
                                                                       @Nonnull String tableTitle) throws IOException {
        return testDataService.importExcelTestData(UUID.randomUUID(), null, null,
                tableTitle, false, Helper.toMultipartFile(Helper.getResourcesFile(fileName)));
    }

    public List<ImportTestDataStatistic> importSqlTestData(@Nonnull UUID environmentId, @Nonnull String tableTitle,
                                                           @Nonnull String sourceTable) {
        return testDataService.importSqlTestData(UUID.randomUUID(), Collections.singletonList(environmentId),
                "Test System", tableTitle, "select SIM from " + sourceTable, 30);
    }
}

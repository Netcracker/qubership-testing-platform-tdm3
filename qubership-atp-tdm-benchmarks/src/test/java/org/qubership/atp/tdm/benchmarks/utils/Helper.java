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

package org.qubership.atp.tdm.benchmarks.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import org.qubership.atp.tdm.model.cleanup.CleanupSettings;
import org.apache.commons.io.IOUtils;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import org.qubership.atp.tdm.model.ColumnType;
import org.qubership.atp.tdm.model.TestDataTableCatalog;
import org.qubership.atp.tdm.model.cleanup.TestDataCleanupConfig;
import org.qubership.atp.tdm.model.table.column.TestDataTableColumn;
import org.qubership.atp.tdm.model.table.column.TestDataTableColumnIdentity;
import org.qubership.atp.tdm.repo.CatalogRepository;
import org.qubership.atp.tdm.service.CleanupService;

public class Helper {

    private static final String RESOURCES_PATH = "src/test/resources/";

    /**
     * Get a list of Test Data table columns.
     */
    public static List<TestDataTableColumn> getTestDataTableColumns(List<String> columnNames) {
        return columnNames.stream()
                .map(columnName -> new TestDataTableColumn(new TestDataTableColumnIdentity("", columnName)))
                .collect(Collectors.toList());
    }

    /**
     * Get a list of UUID for extracted rows.
     */
    public static List<UUID> extractRowIds(List<Map<String, Object>> data) {
        return data.stream()
                .map(row -> UUID.fromString(String.valueOf(row.get("ROW_ID"))))
                .collect(Collectors.toList());
    }

    /**
     * Get resource file.
     */
    public static File getResourcesFile(String fileName) {
        return new File(RESOURCES_PATH + fileName);
    }

    /**
     * Convert file to multipart.
     */
    public static MultipartFile toMultipartFile(File file) throws IOException {
        FileInputStream input = new FileInputStream(file);
        return new MockMultipartFile("file",
                file.getName(), "application/vnd.ms-excel", IOUtils.toByteArray(input));
    }

    /**
     * Build a row of Test Data table.
     */
    public static Map<String, Object> buildTestDataTableRow(List<String> columnNames, List<String> values) {
        return IntStream.range(0, columnNames.size()).boxed()
                .collect(Collectors.toMap(columnNames::get, values::get));
    }

    /**
     * Create record for test data type in the catalog.
     */
    public static void createTestDataTableCatalog(@Nullable UUID environmentId, @Nonnull UUID projectId,
                                                  @Nullable UUID systemId, @Nonnull String tableTitle,
                                                  @Nonnull String tableName,
                                                  @Nonnull CatalogRepository catalogRepository) {
        TestDataTableCatalog catalog =
                new TestDataTableCatalog(tableName, environmentId, projectId, systemId, tableTitle);
        catalogRepository.save(catalog);
    }

    /**
     * Create Test Data Cleanup config.
     */
    public static TestDataCleanupConfig createTestDataCleanupConfig(@Nonnull CleanupService cleanupService,
                                                                    @Nonnull String tableName,
                                                                    boolean shared) throws Exception {
        TestDataCleanupConfig cleanup = new TestDataCleanupConfig();
        cleanup.setEnabled(true);
        cleanup.setSchedule("0 0/1 * * * ?");
        cleanup.setSearchSql("select * from test_data_table_catalog where table_title = "
                + "${'Partner'}");
        cleanup.setShared(shared);
        CleanupSettings cleanupSettings = new CleanupSettings();
        cleanupSettings.setTestDataCleanupConfig(cleanup);
        cleanupSettings.setTableName(tableName);
        return cleanupService.saveCleanupConfig(cleanupSettings).getTestDataCleanupConfig();
    }
}

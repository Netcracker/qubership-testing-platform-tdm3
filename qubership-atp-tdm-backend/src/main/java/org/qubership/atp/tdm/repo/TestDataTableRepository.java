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

package org.qubership.atp.tdm.repo;

import java.io.File;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.qubership.atp.tdm.env.configurator.model.Server;
import org.qubership.atp.tdm.model.ColumnValues;
import org.qubership.atp.tdm.model.ImportTestDataStatistic;
import org.qubership.atp.tdm.model.table.TestDataTable;
import org.qubership.atp.tdm.model.table.TestDataTableFilter;
import org.qubership.atp.tdm.model.table.TestDataTableOrder;
import org.qubership.atp.tdm.model.table.TestDataType;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

public interface TestDataTableRepository {

    ImportTestDataStatistic importExcelTestData(@Nonnull String tableName, boolean exists, @Nonnull MultipartFile file);

    ImportTestDataStatistic importSqlTestData(@Nonnull String tableName, boolean exists, @Nonnull String query,
                                              @Nonnull Integer queryTimeout, @Nonnull Server server);

    ImportTestDataStatistic updateTableBySql(@Nonnull String tableName, @Nonnull String query,
                                             @Nonnull Integer queryTimeout, @Nonnull Server server);

    TestDataTable getTestDataMultiple(@Nonnull String tableName, @Nullable List<TestDataTableFilter> filters);

    TestDataTable getTestData(@Nonnull Boolean isOccupied, @Nonnull String tableName, @Nullable Integer offset,
                              @Nullable Integer limit, @Nullable List<TestDataTableFilter> filters,
                              @Nullable TestDataTableOrder order);

    TestDataTable getTestData(@Nonnull String tableName, @Nonnull List<String> columnNames,
                              @Nullable List<TestDataTableFilter> filters);

    TestDataTable getFullTestData(@Nonnull String tableName);

    File getTestDataTableAsExcel(@Nonnull String tableName, @Nullable Integer offset,
                                 @Nullable Integer limit, @Nullable List<TestDataTableFilter> filters);

    File getTestDataTableAsCsv(@Nonnull String tableName, @Nullable Integer offset,
                               @Nullable Integer limit, @Nullable List<TestDataTableFilter> filters);

    TestDataTable saveTestData(@Nonnull String tableName, boolean exists, TestDataTable testDataTable);

    void alterOccupiedByColumn(List<String> tableNames);

    String occupyTestData(@Nonnull String tableName, @Nonnull String occupiedBy, @Nonnull List<UUID> rows);

    void releaseTestData(@Nonnull String tableName, @Nonnull List<UUID> rows);

    void insertRows(@Nonnull String tableName, boolean exists, @Nonnull List<Map<String, Object>> rows,
                    boolean skipSchemaUpdate);

    int updateRows(@Nonnull String tableName, @Nonnull List<TestDataTableFilter> filters,
                   @Nonnull Map<String, String> dataForUpdate);

    int addInfoToRow(@Nonnull String tableName, @Nonnull List<TestDataTableFilter> filters,
                     @Nonnull Map<String, String> dataForUpdate);

    int getCountRows(@Nonnull String tableName);

    void deleteRows(@Nonnull String tableName, @Nonnull List<UUID> rows);

    int deleteRowsByDate(@Nonnull String tableName, @Nonnull LocalDate date);

    void dropTable(@Nonnull String tableName);

    void truncateTable(@Nonnull String tableName);

    void deleteAllRows(@Nonnull String tableName);

    void deleteUnoccupiedRows(@Nonnull String tableName);

    void alterCreatedWhenColumn(List<String> tableNames);

    String evaluateQuery(@Nonnull String tableName, @Nonnull String query);

    ColumnValues getColumnDistinctValues(@Nonnull String tableName, @Nonnull String columnName, Boolean occupied);

    int getColumnDistinctValuesCount(@Nonnull String tableName, @Nonnull String columnName,
                                     String columnType, Boolean occupied);

    TestDataTable getTableByCreatedWhen(@Nonnull String tableName, @Nonnull LocalDate dateFrom,
                                        @Nonnull LocalDate dateTo);

    boolean changeTestDataTitle(@Nonnull String tableName, @Nullable String tableTitle);

    Long getTestDataSize(@Nonnull String tableName, @Nonnull TestDataType dataType);

    List<String> getTestDataTableCatalogDiscrepancyTestDataFlagsTable();

    List<String> getTestDataFlagsTableDiscrepancyTestDataTableCatalog();

    void saveTestDataTableCatalog(@Nonnull String tableName, @Nonnull String tableTitle, @Nonnull UUID projectId,
                                  @Nonnull UUID systemId, @Nonnull UUID environmentId);

    String getFirstRecordFromDataStorageTable(@Nonnull String tableName, @Nonnull String columnName);

    void updateLastUsage(@Nonnull String tableName);

    List<String> getTablesBySystemIdAndExistingColumn(@Nonnull UUID systemId, @Nonnull UUID environmentId,
                                                      @Nonnull String columnName);

    List<String> getAllColumnNamesBySystemId(@Nonnull UUID systemId);
}

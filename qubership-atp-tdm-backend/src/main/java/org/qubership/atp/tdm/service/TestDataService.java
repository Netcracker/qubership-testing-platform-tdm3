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

package org.qubership.atp.tdm.service;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.qubership.atp.tdm.model.ColumnValues;
import org.qubership.atp.tdm.model.DropResults;
import org.qubership.atp.tdm.model.EnvsList;
import org.qubership.atp.tdm.model.ImportTestDataStatistic;
import org.qubership.atp.tdm.model.TestDataTableCatalog;
import org.qubership.atp.tdm.model.ei.TdmDataToExport;
import org.qubership.atp.tdm.model.statistics.DateStatistics;
import org.qubership.atp.tdm.model.table.TableColumnValues;
import org.qubership.atp.tdm.model.table.TestDataFlagsTable;
import org.qubership.atp.tdm.model.table.TestDataTable;
import org.qubership.atp.tdm.model.table.TestDataTableFilter;
import org.qubership.atp.tdm.model.table.TestDataTableOrder;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

public interface TestDataService {

    List<TestDataTableCatalog> getTestDataTablesCatalog(@Nonnull UUID projectId, @Nullable UUID systemId);

    TdmDataToExport tablesToExport(@Nonnull UUID projectId);

    Map<String, String> tablesToExportByEnvironment(@Nonnull UUID projectId, @Nonnull UUID environmentId);

    TestDataTable getTestData(@Nonnull String tableName);

    TestDataTable getTestData(@Nonnull String tableName, @Nullable Integer offset,
                              @Nullable Integer limit, @Nullable List<TestDataTableFilter> filters,
                              @Nullable TestDataTableOrder order, @Nonnull Boolean isOccupied);

    TestDataTable getTestData(@Nonnull String tableName, @Nonnull List<String> columnNames,
                              @Nullable List<TestDataTableFilter> filters);

    List<ImportTestDataStatistic> importExcelTestData(@Nonnull UUID projectId, @Nullable UUID environmentId,
                                                      @Nullable UUID systemId, @Nonnull String tableTitle,
                                                      @Nonnull Boolean runSqlScript, @Nonnull MultipartFile file);

    List<ImportTestDataStatistic> importSqlTestData(@Nonnull UUID projectId, @Nonnull List<UUID> environmentsIds,
                                                    @Nonnull String systemName, @Nonnull String tableTitle,
                                                    @Nonnull String query, @Nonnull Integer queryTimeout);

    void occupyTestData(@Nonnull String tableName, @Nonnull String occupiedBy, @Nonnull List<UUID> rows);

    void releaseTestData(@Nonnull String tableName, @Nonnull List<UUID> rows);

    DropResults deleteTestData(@Nonnull String tableName);

    DropResults truncateDataInTable(@Nonnull String tableName, @Nonnull UUID projectId, UUID systemId);

    void deleteTestDataTableRows(@Nonnull String tableName, @Nonnull List<UUID> rows);

    File getTestDataTableAsExcelFile(@Nonnull String tableName) throws IOException;

    File getTestDataTableAsCsvFile(@Nonnull String tableName) throws IOException;

    String getPreviewLink(@Nonnull UUID projectId, @Nullable UUID systemId, @Nullable String endpoint,
                          @Nonnull String columnName, @Nullable String tableName,
                          @Nonnull Boolean pickUpFullLinkFromTableCell);

    EnvsList getTableEnvironments(@Nonnull UUID projectId, @Nonnull String tableTitle);

    void alterOccupiedByColumn();

    void setupColumnLinks(@Nonnull Boolean isAll, @Nonnull UUID projectId, @Nonnull UUID systemId,
                          @Nonnull String tableName, @Nonnull String columnName, @Nonnull String endpoint,
                          @Nonnull Boolean validateUnoccupiedResources, @Nonnull Boolean pickUpFullLinkFromTableCell);

    void deleteProjectFromCatalogue(UUID projectId);

    void alterCreatedWhenColumn();

    void fillEnvIdColumn();

    String evaluateQuery(@Nonnull String tableName, @Nonnull String query);

    ColumnValues getColumnDistinctValues(@Nonnull String tableName, @Nonnull String columnName, Boolean occupied);

    Map<String, Object> getTableRow(@Nonnull UUID projectId, @Nullable UUID systemId, @Nonnull String tableTitle,
                                    @Nonnull String columnName, @Nonnull String searchValue,
                                    boolean occupied);

    Map<String, Object> getTableRow(@Nonnull String tableName, @Nonnull String columnName,
                                    @Nonnull String searchValue, boolean occupied);

    boolean changeTestDataTitle(@Nonnull String tableName, @Nullable String tableTitle);

    DateStatistics getTableByCreatedWhen(@Nonnull List<TestDataTableCatalog> catalogList, @Nonnull LocalDate dateFrom,
                                         @Nonnull LocalDate dateTo);

    void alterOccupyStatistic();

    ImportTestDataStatistic updateTestDataBySql(@Nonnull UUID projectId, @Nonnull UUID environmentId,
                                                @Nonnull UUID systemId, @Nonnull String tableName,
                                                @Nonnull String query, @Nonnull Integer queryTimeout);

    TestDataFlagsTable getUnoccupiedValidationFlagStatus(@Nonnull String tableName);

    void resolveDiscrepancyTestDataFlagsTableAndTestDataTableCatalog();

    List<TableColumnValues> getDistinctTablesColumnValues(@Nonnull UUID systemId, @Nonnull UUID environmentId,
                                                          @Nonnull String columnName);

    List<String> getAllColumnNamesBySystemId(@Nonnull UUID systemId);
}

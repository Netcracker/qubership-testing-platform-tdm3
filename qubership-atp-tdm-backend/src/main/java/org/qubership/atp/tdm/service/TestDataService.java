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

/**
 * Test data tables: import, read, occupy and release rows, export, and maintenance.
 */
public interface TestDataService {

    List<TestDataTableCatalog> getTestDataTablesCatalog(@Nonnull UUID projectId, @Nullable UUID systemId);

    /**
     * Lists every table of the project, for the ATP Export Import service.
     */
    TdmDataToExport tablesToExport(@Nonnull UUID projectId);

    /**
     * Maps the database table name to the title of every table of the project in {@code environmentId}.
     */
    Map<String, String> tablesToExportByEnvironment(@Nonnull UUID projectId, @Nonnull UUID environmentId);

    /**
     * Same as {@link #getTestData(String, Integer, Integer, List, TestDataTableOrder, Boolean)} with no paging,
     * filter, or order, for the table's available rows.
     */
    TestDataTable getTestData(@Nonnull String tableName);

    /**
     * Returns {@code tableName}'s occupied or available rows, filtered, sorted, and paged as given.
     */
    TestDataTable getTestData(@Nonnull String tableName, @Nullable Integer offset,
                              @Nullable Integer limit, @Nullable List<TestDataTableFilter> filters,
                              @Nullable TestDataTableOrder order, @Nonnull Boolean isOccupied);

    /**
     * Returns only {@code columnNames} of every row of {@code tableName}, occupied and available alike, filtered
     * as given and with no paging.
     */
    TestDataTable getTestData(@Nonnull String tableName, @Nonnull List<String> columnNames,
                              @Nullable List<TestDataTableFilter> filters);

    /**
     * Loads {@code file} into the table with {@code tableTitle} in {@code systemId}, creating the table if none
     * exists yet. When {@code runSqlScript} is set, also runs the table's saved update-by-query script against the
     * loaded data afterward.
     */
    List<ImportTestDataStatistic> importExcelTestData(@Nonnull UUID projectId, @Nullable UUID environmentId,
                                                      @Nullable UUID systemId, @Nonnull String tableTitle,
                                                      @Nonnull Boolean runSqlScript, @Nonnull MultipartFile file);

    /**
     * Runs {@code query} against {@code systemName} for each of {@code environmentsIds}, importing or updating one
     * table per environment.
     */
    List<ImportTestDataStatistic> importSqlTestData(@Nonnull UUID projectId, @Nonnull List<UUID> environmentsIds,
                                                    @Nonnull String systemName, @Nonnull String tableTitle,
                                                    @Nonnull String query, @Nonnull Integer queryTimeout);

    void occupyTestData(@Nonnull String tableName, @Nonnull String occupiedBy, @Nonnull List<UUID> rows);

    void releaseTestData(@Nonnull String tableName, @Nonnull List<UUID> rows);

    /**
     * Drops {@code tableName}'s table, deletes its catalog entry, columns, flags, and import info, and removes its
     * own refresh job. Also removes every cleanup configuration and general-statistics monitoring schedule that no
     * table references any more afterward, not only {@code tableName}'s own.
     */
    DropResults deleteTestData(@Nonnull String tableName);

    /**
     * Deletes every row of {@code tableName}, keeping the table itself and its catalog entry.
     */
    DropResults truncateDataInTable(@Nonnull String tableName, @Nonnull UUID projectId, UUID systemId);

    void deleteTestDataTableRows(@Nonnull String tableName, @Nonnull List<UUID> rows);

    File getTestDataTableAsExcelFile(@Nonnull String tableName) throws IOException;

    File getTestDataTableAsCsvFile(@Nonnull String tableName) throws IOException;

    /**
     * Returns the URL for a link column: the first row's own value of {@code columnName} when
     * {@code pickUpFullLinkFromTableCell} is set, or {@code endpoint} appended to the system's own base URL
     * otherwise.
     */
    String getPreviewLink(@Nonnull UUID projectId, @Nullable UUID systemId, @Nullable String endpoint,
                          @Nonnull String columnName, @Nullable String tableName,
                          @Nonnull Boolean pickUpFullLinkFromTableCell);

    EnvsList getTableEnvironments(@Nonnull UUID projectId, @Nonnull String tableTitle);

    /**
     * Adds the {@code OCCUPIED_BY} column to every test data table that lacks it. A migration for tables created
     * before the column existed.
     */
    void alterOccupiedByColumn();

    void setupColumnLinks(@Nonnull Boolean isAll, @Nonnull UUID projectId, @Nonnull UUID systemId,
                          @Nonnull String tableName, @Nonnull String columnName, @Nonnull String endpoint,
                          @Nonnull Boolean validateUnoccupiedResources, @Nonnull Boolean pickUpFullLinkFromTableCell);

    /**
     * Drops every table of {@code projectId} and deletes their catalog entries and the project's own saved
     * settings. Unlike {@link #deleteTestData(String)}, does not remove a dropped table's cleanup or refresh
     * configuration, flags, or import info.
     */
    void deleteProjectFromCatalogue(UUID projectId);

    /**
     * Adds the {@code CREATED_WHEN} column to every test data table that lacks it. A migration for tables created
     * before the column existed.
     */
    void alterCreatedWhenColumn();

    /**
     * Sets the environment ID in the catalog entries of tables that have a system, and deletes the tables of a
     * project whose loading fails with a "not found" error. A migration for tables created before the catalog
     * stored the environment.
     */
    void fillEnvIdColumn();

    /**
     * Replaces every {@code ${...}} sub-query in {@code query} with the comma-joined result of running it against
     * {@code tableName}, and returns {@code query} unchanged if it has none.
     */
    String evaluateQuery(@Nonnull String tableName, @Nonnull String query);

    ColumnValues getColumnDistinctValues(@Nonnull String tableName, @Nonnull String columnName, Boolean occupied);

    /**
     * Same as {@link #getTableRow(String, String, String, boolean)}, resolving the table by project, system, and
     * title instead of by database table name.
     */
    Map<String, Object> getTableRow(@Nonnull UUID projectId, @Nullable UUID systemId, @Nonnull String tableTitle,
                                    @Nonnull String columnName, @Nonnull String searchValue,
                                    boolean occupied);

    /**
     * Returns the occupied or available row of {@code tableName} whose {@code columnName} equals
     * {@code searchValue}.
     */
    Map<String, Object> getTableRow(@Nonnull String tableName, @Nonnull String columnName,
                                    @Nonnull String searchValue, boolean occupied);

    /**
     * Renames {@code tableName}'s title in the catalog, and, where the rename succeeds, in its occupation
     * statistics too.
     *
     * @return whether the catalog entry was found and renamed
     */
    boolean changeTestDataTitle(@Nonnull String tableName, @Nullable String tableTitle);

    DateStatistics getTableByCreatedWhen(@Nonnull List<TestDataTableCatalog> catalogList, @Nonnull LocalDate dateFrom,
                                         @Nonnull LocalDate dateTo);

    /**
     * Adds an occupation record to the statistics for every occupied row of every table. A migration for rows
     * occupied before the statistics existed.
     */
    void alterOccupyStatistic();

    ImportTestDataStatistic updateTestDataBySql(@Nonnull UUID projectId, @Nonnull UUID environmentId,
                                                @Nonnull UUID systemId, @Nonnull String tableName,
                                                @Nonnull String query, @Nonnull Integer queryTimeout);

    TestDataFlagsTable getUnoccupiedValidationFlagStatus(@Nonnull String tableName);

    /**
     * Adds default validation flags for catalog tables that have none, and deletes flags of tables that are not in
     * the catalog.
     */
    void resolveDiscrepancyTestDataFlagsTableAndTestDataTableCatalog();

    List<TableColumnValues> getDistinctTablesColumnValues(@Nonnull UUID systemId, @Nonnull UUID environmentId,
                                                          @Nonnull String columnName);

    List<String> getAllColumnNamesBySystemId(@Nonnull UUID systemId);
}

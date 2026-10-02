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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import org.qubership.atp.tdm.model.LinkSetupResult;
import org.qubership.atp.tdm.model.TestDataTableCatalog;
import org.qubership.atp.tdm.model.table.TestDataTableOrder;
import org.qubership.atp.tdm.model.table.TestDataType;
import org.qubership.atp.tdm.model.table.column.TestDataTableColumn;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * Column metadata of test data tables: type, sort and filter behavior, and links to another system.
 */
public interface ColumnService {

    /**
     * Same as {@link #extractColumns(String, TestDataType, ResultSet)}, then overwrites {@code columnType} and
     * {@code columnLink} from {@code tableName}'s already-saved columns, and orders the result by
     * {@code testDataTableOrder} when given.
     */
    List<TestDataTableColumn> extractColumns(@Nonnull String tableName, @Nonnull TestDataType testDataType,
                                             @Nonnull ResultSet resultSet,
                                             @Nullable TestDataTableOrder testDataTableOrder) throws SQLException;

    /**
     * Builds one {@link TestDataTableColumn} per column of {@code resultSet}: {@code DATE} type and filter for
     * {@code CREATED_WHEN} and {@code OCCUPIED_DATE}, and a filter type from the number of distinct values the
     * column has among {@code tableName}'s available or occupied rows otherwise. {@code columnType} is left unset
     * for every other column.
     */
    List<TestDataTableColumn> extractColumns(@Nonnull String tableName, @Nonnull TestDataType testDataType,
                                             @Nonnull ResultSet resultSet)
            throws SQLException;

    /**
     * Builds one {@link TestDataTableColumn} per column of {@code resultSet}, with only {@code DATE} type and
     * filter for {@code CREATED_WHEN} and {@code OCCUPIED_DATE}; unlike {@link #extractColumns(String, TestDataType,
     * ResultSet)}, no other column gets a filter type. Used for a multi-column ATP action request, where
     * {@code resultSet} is already projected to the requested columns.
     */
    List<TestDataTableColumn> extractColumnsMultiple(@Nonnull String tableName, @Nonnull TestDataType testDataType,
                                                     @Nonnull ResultSet resultSet)
            throws SQLException;

    String getColumnLink(@Nonnull UUID projectId, @Nonnull UUID systemId, @Nonnull String endpoint);

    void setupColumnLinks(@Nonnull Boolean isAll, @Nonnull UUID projectId, @Nonnull UUID systemId,
                          @Nonnull String tableName, @Nonnull String columnName, @Nonnull String endpoint,
                          @Nonnull Boolean pickUpFullLinkFromTableCell);

    /**
     * Reapplies every column's own saved link settings to {@code tableName}, and returns every column's name,
     * comma-separated.
     */
    LinkSetupResult setUpLinks(@Nonnull UUID projectId, @Nonnull UUID systemId,
                               @Nonnull String tableName);

    /**
     * Copies every bulk link column of an existing table with {@code tableTitle} onto the new table
     * {@code tableName}. Does nothing if no table with that title is cataloged yet.
     */
    void setUpLinks(@Nonnull UUID projectId, @Nonnull UUID systemId, @Nonnull String tableTitle,
                    @Nonnull String tableName);

    List<TestDataTableColumn> getAllColumnsByTableName(@Nonnull String tableName);

    List<TestDataTableCatalog> getAllTablesWithLinks(@Nonnull UUID projectId, @Nonnull UUID systemId);

    List<TestDataTableColumn> getDistinctTableNames();

    void deleteByTableName(@Nonnull String tableName);
}

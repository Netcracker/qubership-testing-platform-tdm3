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

public interface ColumnService {

    List<TestDataTableColumn> extractColumns(@Nonnull String tableName, @Nonnull TestDataType testDataType,
                                             @Nonnull ResultSet resultSet,
                                             @Nullable TestDataTableOrder testDataTableOrder) throws SQLException;

    List<TestDataTableColumn> extractColumns(@Nonnull String tableName, @Nonnull TestDataType testDataType,
                                             @Nonnull ResultSet resultSet)
            throws SQLException;

    List<TestDataTableColumn> extractColumnsMultiple(@Nonnull String tableName, @Nonnull TestDataType testDataType,
                                                     @Nonnull ResultSet resultSet)
            throws SQLException;

    String getColumnLink(@Nonnull UUID projectId, @Nonnull UUID systemId, @Nonnull String endpoint);

    void setupColumnLinks(@Nonnull Boolean isAll, @Nonnull UUID projectId, @Nonnull UUID systemId,
                          @Nonnull String tableName, @Nonnull String columnName, @Nonnull String endpoint,
                          @Nonnull Boolean pickUpFullLinkFromTableCell);

    LinkSetupResult setUpLinks(@Nonnull UUID projectId, @Nonnull UUID systemId,
                               @Nonnull String tableName);

    void setUpLinks(@Nonnull UUID projectId, @Nonnull UUID systemId, @Nonnull String tableTitle,
                    @Nonnull String tableName);

    List<TestDataTableColumn> getAllColumnsByTableName(@Nonnull String tableName);

    List<TestDataTableCatalog> getAllTablesWithLinks(@Nonnull UUID projectId, @Nonnull UUID systemId);

    List<TestDataTableColumn> getDistinctTableNames();

    void deleteByTableName(@Nonnull String tableName);
}

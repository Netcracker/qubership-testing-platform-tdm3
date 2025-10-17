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

package org.qubership.atp.tdm.repo.impl.extractors;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.qubership.atp.tdm.model.DateFormatter;
import org.qubership.atp.tdm.model.table.TestDataTable;
import org.qubership.atp.tdm.model.table.TestDataType;
import org.qubership.atp.tdm.model.table.column.TestDataTableColumn;
import org.qubership.atp.tdm.service.ColumnService;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ResultSetExtractor;

import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TestDataTableMultipleExtractor implements ResultSetExtractor<TestDataTable> {

    private final ColumnService columnService;
    private final String tableName;
    private TestDataType testDataType;

    TestDataTableMultipleExtractor(@Nonnull ColumnService columnService, @Nonnull String tableName,
                                   @Nonnull TestDataType testDataType) {
        this.columnService = columnService;
        this.tableName = tableName;
        this.testDataType = testDataType;
    }


    @Override
    public TestDataTable extractData(@Nonnull ResultSet resultSet) throws SQLException, DataAccessException {
        log.debug("ExtractData Start");
        TestDataTable testDataTable = new TestDataTable();
        List<TestDataTableColumn> columns = getColumns(resultSet, testDataType);
        testDataTable.setColumns(columns);
        List<Map<String, Object>> rows = new ArrayList<>();
        while (resultSet.next()) {
            Map<String, Object> row = new HashMap<>();
            for (TestDataTableColumn column : columns) {
                String columnName = column.getIdentity().getColumnName();
                row.put(columnName, formatColumn(resultSet.getObject(columnName)));
            }
            rows.add(row);
        }
        testDataTable.setData(rows);
        log.debug("ExtractData Finish");
        return testDataTable;
    }

    private List<TestDataTableColumn> getColumns(@Nonnull ResultSet resultSet,
                                                 @Nonnull TestDataType testDataType) throws SQLException {
        return columnService.extractColumnsMultiple(this.tableName, testDataType, resultSet);
    }

    private Object formatColumn(Object value) {
        if (value instanceof Timestamp) {
            value = DateFormatter.DB_DATE_FORMATTER.format(new Timestamp(((Timestamp) value).getTime()));
        }
        return value;
    }
}

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

package org.qubership.atp.tdm.repo.impl.loader;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.qubership.atp.integration.configuration.mdc.MdcUtils;
import org.qubership.atp.tdm.model.table.TestDataTable;
import org.qubership.atp.tdm.model.table.column.TestDataTableColumn;
import org.qubership.atp.tdm.model.table.column.TestDataTableColumnIdentity;
import org.slf4j.MDC;
import org.springframework.dao.DataAccessException;

import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TestDataSqlLoader {

    private final Map<Integer, String> columns = new HashMap<>();
    private final Connection connection;
    private final String sqlQuery;
    private final Integer queryTimeout;

    /**
     * Full constructor.
     */
    public TestDataSqlLoader(@Nonnull Connection connection, @Nonnull String sqlQuery, @Nonnull Integer queryTimeout) {
        this.connection = connection;
        this.sqlQuery = sqlQuery;
        this.queryTimeout = queryTimeout;
    }

    /**
     * Process query.
     */
    public TestDataTable process() throws Exception {
        log.info("Executing query: {}", sqlQuery);
        try (CallableStatement statement = connection.prepareCall(sqlQuery)) {
            ExecutorService executorService = Executors.newSingleThreadExecutor();
            Map<String, String> mdcContext = MDC.getCopyOfContextMap();
            try (ResultSet rs = executorService.submit(() -> {
                        MdcUtils.setContextMap(mdcContext);
                        return statement.executeQuery();
                    })
                    .get(queryTimeout, TimeUnit.SECONDS)) {
                return extractData(rs);
            }
        }
    }

    private TestDataTable extractData(ResultSet resultSet) throws SQLException, DataAccessException {
        log.info("Extracting data from result set...");

        TestDataTable testDataTable = new TestDataTable();

        ResultSetMetaData metaData = resultSet.getMetaData();
        int colCount = metaData.getColumnCount();

        for (int c = 1; c <= colCount; c++) {
            String columnName = metaData.getColumnName(c);
            columns.put(c, columnName);
        }

        testDataTable.setColumns(columns.values().stream()
                .map(c -> new TestDataTableColumn(new TestDataTableColumnIdentity("", c)))
                .collect(Collectors.toList()));

        log.info("Processing rows...");
        List<Map<String, Object>> rows = new ArrayList<>();
        while (resultSet.next()) {
            Map<String, Object> row = new HashMap<>();
            for (int key : columns.keySet()) {
                String columnName = columns.get(key);
                row.put(columnName, resultSet.getObject(columnName));
            }
            rows.add(row);
        }
        testDataTable.setData(rows);

        log.info("Extracting finished.");

        return testDataTable;
    }
}

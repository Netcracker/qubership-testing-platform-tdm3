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

package org.qubership.atp.tdm.utils;

import static java.lang.String.format;
import static org.qubership.atp.tdm.model.DateFormatter.DB_DATE_FORMATTER;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import org.qubership.atp.tdm.env.configurator.exceptions.internal.TdmEnvDbConnectionException;
import org.qubership.atp.tdm.env.configurator.model.Connection;
import org.qubership.atp.tdm.env.configurator.model.Server;
import org.qubership.atp.tdm.exceptions.internal.TdmJsonParsingException;
import org.qubership.atp.tdm.model.table.TestDataTable;
import org.qubership.atp.tdm.repo.impl.SystemColumns;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Iterables;
import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TestDataUtils {

    /**
     * Returns column names from sql query.
     */
    public static List<String> getColumnsNamesFromQuery(@Nonnull String query) {
        log.debug("Get column names from query:[{}]", query);
        int indexOfFirstSpace = query.indexOf(" ");
        int indexOfFromWord = query.indexOf("from");
        String[] columns = query.substring(indexOfFirstSpace + 1, indexOfFromWord - 1).split(",");
        List<String> columnNames = Arrays.stream(columns).map(c -> {
            c = c.trim();
            return c;
        }).collect(Collectors.toList());
        ;
        log.debug("Column names parsed:[{}]", columnNames);
        return columnNames;
    }

    /**
     * Escapes characters.
     */
    public static String escapeCharacters(@Nonnull String value) {
        if (value.contains("'")) {
            return value.replaceAll("'", "''");
        }
        return value;
    }

    /**
     * Generate Insert Template.
     */
    public static String generateInsertTemplate(String tableName, List<String> columns, boolean systemColumnExists) {
        //TODO refactor
        StringBuilder query = new StringBuilder("INSERT INTO " + tableName + " (");
        StringBuilder values = new StringBuilder("VALUES (");

        if (!systemColumnExists) {
            query.append("\"").append(SystemColumns.ROW_ID.getName()).append("\"").append(", ")
                    .append("\"").append(SystemColumns.CREATED_WHEN.getName()).append("\"").append(", ");
            values.append("uuid_generate_v4()").append(", ")
                    .append("'").append(DB_DATE_FORMATTER.format(new Timestamp(new Date().getTime())))
                    .append("'").append(", ");
        }

        for (String column : columns) {
            query.append("\"").append(column).append("\"").append(", ");
            values.append("?, ");
        }
        query.setLength(query.length() - 2);
        values.setLength(values.length() - 2);

        query.append(") ").append(values).append(")");
        return query.toString();
    }

    /**
     * Convert JSON in table row to string without loss.
     *
     * @param rowContent - row object.
     * @return - converted string.
     */
    public static String convertToJsonString(Object rowContent) {
        try {
            return new ObjectMapper().writeValueAsString(rowContent);
        } catch (JsonProcessingException e) {
            log.error(format(TdmJsonParsingException.DEFAULT_MESSAGE, rowContent), e);
            throw new TdmJsonParsingException(rowContent);
        }
    }

    /**
     * Returns index of header column by name.
     */
    public static Integer getIndexHeaderColumnByName(TestDataTable testDataTable, String columnName) {
        return Iterables.indexOf(testDataTable.getColumns(), c -> {
            assert c != null;
            return columnName.equals(c.getIdentity().getColumnName());
        });
    }

    /**
     * Returns server.
     */
    public static Server getServer(List<Connection> connections, String type) {
        Connection connection = connections.stream().filter(sys -> type.equalsIgnoreCase(sys.getName()))
                .findFirst()
                .orElseThrow(() -> new TdmEnvDbConnectionException(type));
        return new Server(connection, type);
    }

    /**
     * Returns connection.
     */
    public static Connection getConnection(List<Connection> connections, String type) {
        Connection connection = connections.stream().filter(sys -> type.equalsIgnoreCase(sys.getName()))
                .findFirst()
                .orElseThrow(() -> new TdmEnvDbConnectionException(type));
        return connection;
    }
}

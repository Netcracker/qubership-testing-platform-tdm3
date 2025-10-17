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

package org.qubership.atp.tdm.model.table;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.qubership.atp.tdm.model.ColumnType;
import org.qubership.atp.tdm.model.FilterType;
import org.qubership.atp.tdm.model.table.column.TestDataTableColumn;
import org.qubership.atp.tdm.repo.impl.SystemColumns;
import org.qubership.atp.tdm.utils.TestDataTableConvertor;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import jakarta.annotation.Nonnull;

public class TableSerializer extends JsonSerializer<TestDataTable> {

    private static final String LINK_DELIMITER = ";";
    private static final String LINK_CELL_CONTENT = "this.linkCellContent";
    private static final String LONG_CELL_CONTENT = "this.longCellContent";
    private static final String SIMPLE_CELL_CONTENT = "this.simpleCellContent";
    private static final int LONG_STRING_MINIMUM_LENGTH = 60;

    @Override
    public void serialize(TestDataTable table, JsonGenerator jsonGenerator,
                          SerializerProvider serializerProvider) throws IOException {
        jsonGenerator.writeStartObject();

        removeColumns(table);

        jsonGenerator.writeFieldName("data");
        jsonGenerator.writeStartObject();
        List<TestDataTableColumn> columns = table.getColumns();

        LinkedList<TestDataTableColumn> orderedColumns = getOrderedColumns(columns);
        buildHeader(jsonGenerator, orderedColumns);
        buildBody(jsonGenerator, orderedColumns, table.getData());

        jsonGenerator.writeEndObject();
        jsonGenerator.writeNumberField("records", table.getRecords());
        jsonGenerator.writeStringField("name", table.getName());
        jsonGenerator.writeStringField("query", table.getQuery());
        jsonGenerator.writeStringField("updateByQuery", table.getUpdateByQuery());
        jsonGenerator.writeEndObject();
    }

    private LinkedList<TestDataTableColumn> getOrderedColumns(List<TestDataTableColumn> columns) {
        LinkedList<TestDataTableColumn> orderedColumns = new LinkedList<>();
        int unknown = -1;
        int occupiedByColumnIndex = unknown;
        int createdWhenColumnIndex = unknown;
        for (int i = 0; i < columns.size(); i++) {
            TestDataTableColumn column = columns.get(i);
            String columnName = column.getIdentity().getColumnName();
            if (SystemColumns.OCCUPIED_BY.getName().equalsIgnoreCase(columnName)) {
                occupiedByColumnIndex = i;
            } else if (SystemColumns.CREATED_WHEN.getName().equalsIgnoreCase(columnName)) {
                createdWhenColumnIndex = i;
            } else {
                orderedColumns.add(column);
            }
        }
        if (occupiedByColumnIndex != unknown) {
            orderedColumns.addFirst(columns.get(occupiedByColumnIndex));
        }
        if (createdWhenColumnIndex != unknown) {
            orderedColumns.addLast(columns.get(createdWhenColumnIndex));
        }

        return orderedColumns;
    }

    private void removeColumns(@Nonnull TestDataTable table) {
        if (TestDataType.OCCUPIED.equals(table.getType())) {
            table.setColumns(table.getColumns().stream()
                    .filter(c -> !SystemColumns.SELECTED.getName().equals(c.getIdentity().getColumnName())
                            && !SystemColumns.ROW_ID.getName().equals(c.getIdentity().getColumnName()))
                    .collect(Collectors.toList()));
        } else {
            table.setColumns(table.getColumns().stream()
                    .filter(c -> !SystemColumns.SELECTED.getName().equals(c.getIdentity().getColumnName())
                            && !SystemColumns.ROW_ID.getName().equals(c.getIdentity().getColumnName())
                            && !SystemColumns.OCCUPIED_DATE.getName().equals(c.getIdentity().getColumnName())
                            && !SystemColumns.OCCUPIED_BY.getName().equals(c.getIdentity().getColumnName()))
                    .collect(Collectors.toList()));
        }
    }

    private void buildHeader(JsonGenerator jsonGenerator, List<TestDataTableColumn> headers) throws IOException {
        jsonGenerator.writeFieldName("header");
        jsonGenerator.writeStartObject();
        jsonGenerator.writeFieldName("rows");
        jsonGenerator.writeStartArray();
        buildColumns(jsonGenerator, headers);
        jsonGenerator.writeEndArray();
        jsonGenerator.writeEndObject();
    }

    private void buildBody(JsonGenerator jsonGenerator, List<TestDataTableColumn> headers,
                           List<Map<String, Object>> rows) throws IOException {
        jsonGenerator.writeFieldName("body");
        jsonGenerator.writeStartObject();
        jsonGenerator.writeFieldName("rows");
        jsonGenerator.writeStartArray();
        for (Map<String, Object> row : rows) {
            buildColumns(jsonGenerator, headers, row);
        }
        jsonGenerator.writeEndArray();
        jsonGenerator.writeEndObject();
    }

    private void buildColumns(JsonGenerator jsonGenerator, List<TestDataTableColumn> headers,
                              Map<String, Object> columns) throws IOException {
        jsonGenerator.writeStartObject();
        jsonGenerator.writeStringField("id", columns.get(SystemColumns.ROW_ID.getName()).toString());
        jsonGenerator.writeFieldName("columns");
        jsonGenerator.writeStartArray();
        addCheckbox(jsonGenerator);
        for (TestDataTableColumn header : headers) {
            String columnName = header.getIdentity().getColumnName();
            String value = String.valueOf(columns.get(columnName));
            if ("null".equals(value)) {
                value = "";
            }
            jsonGenerator.writeStartObject();
            ColumnType type = header.getColumnType();
            if (ColumnType.LINK.equals(type)) {
                buildLinkCell(jsonGenerator, header.getColumnLink(), value, columns);
            } else if (value.length() > LONG_STRING_MINIMUM_LENGTH) {
                buildLongCell(jsonGenerator, value);
            } else {
                buildSimpleCell(jsonGenerator, value);
            }
            jsonGenerator.writeEndObject();
        }
        jsonGenerator.writeEndArray();
        jsonGenerator.writeEndObject();
    }

    private void buildColumns(JsonGenerator jsonGenerator, List<TestDataTableColumn> columns) throws IOException {
        jsonGenerator.writeStartObject();
        jsonGenerator.writeFieldName("columns");
        jsonGenerator.writeStartArray();
        addCheckbox(jsonGenerator);
        for (TestDataTableColumn column : columns) {
            String columnName = column.getIdentity().getColumnName();
            jsonGenerator.writeStartObject();
            jsonGenerator.writeStringField("value", columnName);
            OrderType orderType = column.getOrderType();
            if (Objects.nonNull(orderType)) {
                jsonGenerator.writeObjectField("sort", orderType.getValue());
            }

            if (!FilterType.NONE.equals(column.getFilterType())) {
                jsonGenerator.writeBooleanField("filter", true);
                jsonGenerator.writeStringField("filterType", column.getFilterType().getValue());
            }

            jsonGenerator.writeFieldName("contentModel");
            jsonGenerator.writeStartObject();
            jsonGenerator.writeStringField("id", columnName);
            jsonGenerator.writeEndObject();
            jsonGenerator.writeEndObject();
        }
        jsonGenerator.writeEndArray();
        jsonGenerator.writeEndObject();
    }

    private void addCheckbox(JsonGenerator jsonGenerator) throws IOException {
        jsonGenerator.writeStartObject();
        jsonGenerator.writeStringField("type", "checkbox");
        jsonGenerator.writeBooleanField("value", false);
        jsonGenerator.writeEndObject();
    }

    private void buildLinkCell(JsonGenerator jsonGenerator, String columnLink,
                               String cellValue, Map<String, Object> columns) throws IOException {
        jsonGenerator.writeStringField("value", LINK_CELL_CONTENT);
        jsonGenerator.writeStringField("type", "content");
        jsonGenerator.writeFieldName("contentModel");
        jsonGenerator.writeStartObject();
        jsonGenerator.writeFieldName("links");
        jsonGenerator.writeStartArray();
        for (String value : cellValue.split(LINK_DELIMITER)) {
            String finalLink = "";
            if (columnLink.contains("${")) {
                finalLink = TestDataTableConvertor.replaceParams(columns, columnLink);
                jsonGenerator.writeStartObject();
                jsonGenerator.writeStringField("value", value);
                jsonGenerator.writeStringField("url", finalLink);
                jsonGenerator.writeEndObject();
            } else {
                jsonGenerator.writeStartObject();
                jsonGenerator.writeStringField("value", value.trim());
                jsonGenerator.writeStringField("url", columnLink + value.trim());
                jsonGenerator.writeEndObject();
            }
        }
        jsonGenerator.writeEndArray();
        jsonGenerator.writeEndObject();
    }

    private void buildLongCell(JsonGenerator jsonGenerator, String cellValue) throws IOException {
        jsonGenerator.writeStringField("value", LONG_CELL_CONTENT);
        jsonGenerator.writeStringField("type", "content");
        jsonGenerator.writeFieldName("contentModel");
        jsonGenerator.writeStartObject();
        jsonGenerator.writeStringField("value", cellValue.substring(0, 60) + "...");
        jsonGenerator.writeStringField("fullValue", cellValue);
        jsonGenerator.writeStringField("shortValue", cellValue.substring(0, 60) + "...");
        jsonGenerator.writeEndObject();
    }

    private void buildSimpleCell(JsonGenerator jsonGenerator, String cellValue) throws IOException {
        jsonGenerator.writeStringField("value", SIMPLE_CELL_CONTENT);
        jsonGenerator.writeStringField("type", "content");
        jsonGenerator.writeFieldName("contentModel");
        jsonGenerator.writeStartObject();
        jsonGenerator.writeStringField("value", cellValue);
        jsonGenerator.writeEndObject();
    }
}

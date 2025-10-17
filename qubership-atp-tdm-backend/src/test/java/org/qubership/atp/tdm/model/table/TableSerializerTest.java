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

import static org.hamcrest.MatcherAssert.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.qubership.atp.tdm.model.ColumnType;
import org.qubership.atp.tdm.model.FilterType;
import org.qubership.atp.tdm.model.table.column.TestDataTableColumn;
import org.qubership.atp.tdm.model.table.column.TestDataTableColumnIdentity;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.qubership.atp.tdm.matchers.JsonMatcher;

public class TableSerializerTest {

    private static final String ER_TABLES_PATH = "src/test/resources/serialized-tables/";

    @Test
    public void testSerialize_testOnEmptyTable_normalSerialize() throws IOException {
        String er = readErFromFile(ER_TABLES_PATH + "empty-table.json");

        TestDataTable table = new TestDataTable();
        table.setColumns(buildColumns(Arrays.asList("SELECTED", "ROW_ID", "SIM", "Status"),
                ColumnType.TEXT, FilterType.TEXT));
        table.setData(new ArrayList<>());
        table.setRecords(0);
        String ar = new ObjectMapper().writeValueAsString(table);

        assertThat(ar, JsonMatcher.isMinified(er));
    }

    @Test
    public void testSerialize_testWithTransferColumn_normalSerialize() throws IOException {
        String er = readErFromFile(ER_TABLES_PATH + "table-with-transferred-columns.json");

        TestDataTable table = new TestDataTable();
        List<TestDataTableColumn> columnList = buildColumns(Arrays.asList("SELECTED", "ROW_ID"), ColumnType.TEXT,
                FilterType.TEXT);
        columnList.addAll(buildColumns(Collections.singletonList("OCCUPIED_DATE"), ColumnType.DATE, FilterType.DATE));
        columnList.addAll(buildColumns(Arrays.asList("OCCUPIED_BY", "SIM", "Status"), ColumnType.TEXT,
                FilterType.TEXT));
        columnList.addAll(buildColumns(Collections.singletonList("CREATED_WHEN"), ColumnType.DATE, FilterType.DATE));
        table.setColumns(columnList);
        table.setData(new ArrayList<>());
        table.setRecords(0);
        table.setType(TestDataType.OCCUPIED);
        String ar = new ObjectMapper().writeValueAsString(table);

        assertThat(ar, JsonMatcher.isMinified(er));
    }

    @Test
    public void testSerialize_testOnTableWithQuery_normalSerialize() throws IOException {
        String er = readErFromFile(ER_TABLES_PATH + "table-with-filled-import-query.json");

        TestDataTable table = new TestDataTable();
        table.setColumns(buildColumns(Arrays.asList("SELECTED", "ROW_ID", "SIM", "Status"), ColumnType.TEXT,
                FilterType.TEXT));
        table.setData(new ArrayList<>());
        table.setRecords(0);
        table.setQuery("select name, object_id, object_type_id, description from nc_objects where rownum < 10");
        String ar = new ObjectMapper().writeValueAsString(table);

        assertThat(ar, JsonMatcher.isMinified(er));
    }

    @Test
    public void testSerialize_testOnTableWithMultilinks_normalSerialize() throws IOException {
        String er = readErFromFile(ER_TABLES_PATH + "table-with-multiple-links.json");

        TestDataTable table = new TestDataTable();
        table.setColumns(buildColumns("SIM", "http://localhost:8000/endpoint?&="));
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Object> firstRow = new HashMap<>();
        firstRow.put("SELECTED", false);
        firstRow.put("ROW_ID", "3d88b62b-dd00-426c-9ace-0de10204303d");
        firstRow.put("SIM", "item");
        rows.add(firstRow);
        table.setData(rows);
        table.setRecords(0);
        String ar = new ObjectMapper().writeValueAsString(table);

        assertThat(ar, JsonMatcher.isMinified(er));
    }

    @Test
    public void testSerialize_testOnTableWithLinkPlaceholders_normalSerialize() throws IOException {
        String er = readErFromFile(ER_TABLES_PATH + "table-with-link-placeholders.json");

        TestDataTable table = new TestDataTable();
        table.setColumns(buildColumns("HEADER", "http://localhost:8000/wizard?header=${HEADER}"));
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Object> firstRow = new HashMap<>();
        firstRow.put("SELECTED", false);
        firstRow.put("ROW_ID", "3d88b62b-dd00-426c-9ace-0de10204303d");
        firstRow.put("HEADER", "header_value");
        rows.add(firstRow);
        table.setData(rows);
        table.setRecords(0);
        String ar = new ObjectMapper().writeValueAsString(table);

        assertThat(ar, JsonMatcher.isMinified(er));
    }

    @Test
    public void testSerialize_testOnTableWithLongCells_TableWithShortLongAndNormalValues() throws IOException {
        String er = readErFromFile(ER_TABLES_PATH + "table-with-long-cells.json");

        TestDataTable table = new TestDataTable();
        table.setColumns(buildColumns(Arrays.asList("SELECTED", "ROW_ID", "SIM"), ColumnType.TEXT, FilterType.TEXT));
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Object> firstRow = new HashMap<>();
        firstRow.put("SELECTED", false);
        firstRow.put("ROW_ID", "3d88b62b-dd00-426c-9ace-0de10204303d");
        firstRow.put("SIM", "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Morbi egestas turpis eu blandit"
                + " sodales. Nunc varius turpis et felis tincidunt, ac faucibus neque vehicula. Mauris suscipit elit "
                + "at sollicitudin mattis. Donec in neque at lectus varius lobortis at vitae mi. Nunc nisi turpis, "
                + "laoreet eu nisi sit amet, maximus molestie sem. Phasellus facilisis magna vitae ligula mattis, sit"
                + " amet blandit elit rhoncus. Suspendisse potenti. Morbi quis fringilla tellus. Ut quis mollis "
                + "justo, sit amet porttitor sapien. Duis nec lacus sit amet dui hendrerit malesuada.");
        rows.add(firstRow);
        table.setData(rows);
        table.setRecords(0);
        String ar = new ObjectMapper().writeValueAsString(table);

        assertThat(ar, JsonMatcher.isMinified(er));
    }

    private List<TestDataTableColumn> buildColumns(List<String> columnNames, ColumnType columnType,
                                                   FilterType filterType) {
        return columnNames.stream()
                .map(columnName -> buildColumn(columnName, columnType, filterType))
                .collect(Collectors.toList());
    }

    private List<TestDataTableColumn> buildColumns(String columnName, String columnLink) {
        TestDataTableColumn column = new TestDataTableColumn(new TestDataTableColumnIdentity("",
                columnName), ColumnType.LINK, FilterType.TEXT, columnLink, false);
        return Collections.singletonList(column);
    }

    private TestDataTableColumn buildColumn(String columnName, ColumnType columnType, FilterType filterType) {
        return new TestDataTableColumn(new TestDataTableColumnIdentity("", columnName),
                columnType, filterType);
    }

    private String readErFromFile(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)));
    }
}

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

import java.util.List;
import java.util.Map;

import org.qubership.atp.tdm.model.table.column.TestDataTableColumn;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Schema(description = "Content of a test data table, for the TDM UI grid. TableSerializer writes this class "
        + "to a nested {header: {rows}, body: {rows}, records, name, query, updateByQuery} shape rather than "
        + "these fields directly; title, columns, and type do not appear in the JSON output.")
@Data
@EqualsAndHashCode
@ToString
@JsonSerialize(using = TableSerializer.class)
public class TestDataTable {

    @Schema(description = "Database table name of the table.")
    private String name;
    @Schema(description = "Title of the table. Not written to the JSON output; see the class description.")
    private String title;
    @Schema(description = "Columns of the table, in storage order. Not written to the JSON output; see the "
            + "class description.")
    private List<TestDataTableColumn> columns;
    @Schema(description = "Rows of the table, one map of column name to value per row.")
    private List<Map<String, Object>> data;
    @Schema(description = "Number of rows in data.")
    private int records;
    @Schema(description = "Import query that fills the table.")
    private String query;
    @Schema(description = "Query that updates the table's rows from POST /api/tdm/update/sql.")
    private String updateByQuery;
    @Schema(description = "Whether data holds available or occupied rows. Not written to the JSON output; see "
            + "the class description.")
    private TestDataType type;
}

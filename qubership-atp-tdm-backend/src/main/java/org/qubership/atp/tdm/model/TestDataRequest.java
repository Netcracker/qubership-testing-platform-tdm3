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

package org.qubership.atp.tdm.model;

import java.util.List;

import org.qubership.atp.tdm.model.table.TestDataTableFilter;
import org.qubership.atp.tdm.model.table.TestDataTableOrder;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TestDataRequest {
    @Schema(description = "Database table name of the table to read.")
    private String tableName;
    @Schema(description = "Reads occupied rows instead of available ones.")
    private boolean occupied;
    @Schema(description = "Number of rows to skip, for paging.")
    private Integer offset;
    @Schema(description = "Maximum number of rows to return.")
    private Integer limit;
    @Schema(description = "Column filters to apply.")
    private List<TestDataTableFilter> filters;
    @Schema(description = "Column and direction to sort by.")
    private TestDataTableOrder dataTableOrder;
}

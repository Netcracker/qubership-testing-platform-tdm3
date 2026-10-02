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

package org.qubership.atp.tdm.model.statistics.available;

import java.util.HashMap;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

@Schema(description = "Number of available rows of one table, broken down by the value of a chosen "
        + "column.")
@Data
@AllArgsConstructor
public class TableAvailableDataStats {
    @Schema(description = "Database table name of the table.")
    private String tableName;
    @Schema(description = "Title of the table.")
    private String tableTitle;
    @Schema(description = "Number of available rows for each value found in the chosen column, keyed by that "
            + "value.")
    private Map<String,Integer> options;

    public TableAvailableDataStats() {
        options = new HashMap<>();
    }
}

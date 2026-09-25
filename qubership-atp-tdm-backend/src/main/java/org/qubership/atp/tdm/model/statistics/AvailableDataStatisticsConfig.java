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

package org.qubership.atp.tdm.model.statistics;

import java.util.List;
import java.util.UUID;

import org.qubership.atp.tdm.model.table.TableColumnValues;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "Available-data-by-column monitoring configuration of one system's environment.")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AvailableDataStatisticsConfig {

    @Schema(description = "Name of the column currently grouped on; one of columnKeys.")
    private String description;
    @Schema(description = "Same value as description: the column currently grouped on.")
    private String activeColumnKey;
    @Schema(description = "Names of the columns available to group on, common to every table of the system.")
    private List<String> columnKeys;
    @Schema(description = "System ID.")
    private UUID systemId;
    @Schema(description = "Environment ID.")
    private UUID environmentId;
    @Schema(description = "Per-table filter: for each table, the column values to count as available. A table "
            + "missing here counts every value.")
    private List<TableColumnValues> tablesColumns;

    public AvailableDataStatisticsConfig(UUID systemId, UUID environmentId) {
        this.systemId = systemId;
        this.environmentId = environmentId;
    }
}

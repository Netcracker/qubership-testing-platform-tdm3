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

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AvailableDataStatisticsConfig {

    private String description;
    private String activeColumnKey;
    private List<String> columnKeys;
    private UUID systemId;
    private UUID environmentId;
    private List<TableColumnValues> tablesColumns;

    public AvailableDataStatisticsConfig(UUID systemId, UUID environmentId) {
        this.systemId = systemId;
        this.environmentId = environmentId;
    }
}

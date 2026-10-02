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

package org.qubership.atp.tdm.model.cleanup;

import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "Cleanup settings of a table, and, when saving them, the environments of the other "
        + "tables with the same title and system to apply them to too.")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CleanupSettings {
    @Schema(description = "The cleanup configuration.")
    TestDataCleanupConfig testDataCleanupConfig;
    @Schema(description = "IDs of the environments of the tables that already share this configuration, in the "
            + "response of GET /api/tdm/cleanup/config/{id}; the environments to share it with, when saving.")
    List<UUID> environmentsList;
    @Schema(description = "Database table name of the table to save the configuration for. Read only when "
            + "saving; not set in the response of GET /api/tdm/cleanup/config/{id}.")
    String tableName;
}

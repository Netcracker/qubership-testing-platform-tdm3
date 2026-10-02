/*
 * # Copyright 2024-2025 NetCracker Technology Corporation
 * #
 * # Licensed under the Apache License, Version 2.0 (the "License");
 * # you may not use this file except in compliance with the License.
 * # You may obtain a copy of the License at
 * #
 * #      http://www.apache.org/licenses/LICENSE-2.0
 * #
 * # Unless required by applicable law or agreed to in writing, software
 * # distributed under the License is distributed on an "AS IS" BASIS,
 * # WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * # See the License for the specific language governing permissions and
 * # limitations under the License.
 */

package org.qubership.atp.tdm.model;

import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body of {@code POST /api/tdm/v2}.
 */
@Schema(description = "Request body of POST /api/tdm/v2: runs a query against a system and imports its result "
        + "as a new test data table, or as new rows of an existing one with the same title, for each listed "
        + "environment.")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImportSqlTestDataRequest {

    @Schema(description = "Project ID.")
    private UUID projectId;

    @Schema(description = "Environment IDs to import into; one table is imported or updated per environment.")
    private List<UUID> environmentsIds;

    @Schema(description = "System name whose DB connection runs the query.")
    private String systemName;

    @Schema(description = "Title of the table to create or update.")
    private String tableTitle;

    @Schema(description = "SQL query that returns the rows to import.")
    private String query;

    @Schema(description = "Query timeout, in seconds.")
    private Integer queryTimeout;
}

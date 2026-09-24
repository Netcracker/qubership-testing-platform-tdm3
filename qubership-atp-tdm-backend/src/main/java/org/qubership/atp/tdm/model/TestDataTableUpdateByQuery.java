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

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "Request body of POST /api/tdm/update/sql: runs a query against a system and updates a "
        + "table's rows from its result. The query and timeout are saved with the table.")
@Data
public class TestDataTableUpdateByQuery {
    @Schema(description = "Database table name of the test data table.")
    private String tableName;
    @Schema(description = "Project ID.")
    private UUID projectId;
    @Schema(description = "Environment ID.")
    private UUID environmentId;
    @Schema(description = "System ID whose DB connection runs the query.")
    private UUID systemId;
    @Schema(description = "SQL query that returns the new rows.")
    private String query;
    @Schema(description = "Query timeout, in seconds.")
    private Integer queryTimeout;
}

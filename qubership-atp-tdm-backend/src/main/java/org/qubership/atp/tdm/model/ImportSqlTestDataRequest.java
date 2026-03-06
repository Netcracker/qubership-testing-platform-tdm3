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

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * Request model for SQL test data import endpoint v2.
 * Contains all parameters previously passed as RequestParam.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImportSqlTestDataRequest {
    
    /** Project identifier */
    private UUID projectId;
    
    /** List of environment identifiers where data will be imported */
    private List<UUID> environmentsIds;
    
    /** Name of the system/database */
    private String systemName;
    
    /** Title for the imported table */
    private String tableTitle;
    
    /** SQL query to execute for data import */
    private String query;
    
    /** Timeout in seconds for query execution */
    private Integer queryTimeout;
}

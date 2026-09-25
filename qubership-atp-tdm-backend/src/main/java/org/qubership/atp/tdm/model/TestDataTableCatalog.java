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

import java.util.Date;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Transient;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@ToString
public class TestDataTableCatalog {
    @Schema(description = "Database table name of the table.")
    @Id
    @Column(name = "table_name")
    private String tableName;
    @Schema(description = "Project ID.")
    @Column(name = "project_id")
    private UUID projectId;
    @Schema(description = "Environment ID.")
    @Column(name = "environment_id")
    private UUID environmentId;
    @Schema(description = "System ID.")
    @Column(name = "system_id")
    private UUID systemId;
    @Schema(description = "Title of the table.")
    @Column(name = "table_title")
    private String tableTitle;
    @Schema(description = "ID of the table's cleanup configuration, or null when none is set.")
    @Column(name = "cleanup_config_id")
    private UUID cleanupConfigId;
    @Schema(description = "ID of the table's refresh configuration, or null when none is set.")
    @Column(name = "refresh_config_id")
    private UUID refreshConfigId;
    @Schema(description = "When the table was last read or written.")
    @Column(name = "last_usage")
    private Date lastUsage;

    @Schema(description = "Query that imports the table's data, from its refresh configuration; not persisted "
            + "in this entity's own table.")
    @Transient
    private String importQuery;
    @Schema(description = "Timeout of importQuery, in seconds; not persisted in this entity's own table.")
    @Transient
    private Integer queryTimeout;

    /**
     * Constructor for creation catalog with null config ids.
     *
     * @param tableName  - table name
     * @param projectId  - project id
     * @param systemId   - system id
     * @param tableTitle - table title
     */
    public TestDataTableCatalog(String tableName, UUID projectId, UUID environmentId, UUID systemId,
                                String tableTitle) {
        this.tableName = tableName;
        this.projectId = projectId;
        this.systemId = systemId;
        this.tableTitle = tableTitle;
        this.environmentId = environmentId;
    }
}

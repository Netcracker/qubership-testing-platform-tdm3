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
    @Id
    @Column(name = "table_name")
    private String tableName;
    @Column(name = "project_id")
    private UUID projectId;
    @Column(name = "environment_id")
    private UUID environmentId;
    @Column(name = "system_id")
    private UUID systemId;
    @Column(name = "table_title")
    private String tableTitle;
    @Column(name = "cleanup_config_id")
    private UUID cleanupConfigId;
    @Column(name = "refresh_config_id")
    private UUID refreshConfigId;
    @Column(name = "last_usage")
    private Date lastUsage;

    @Transient
    private String importQuery;
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

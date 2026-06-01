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

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "dynamic_environment")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DynamicEnvironment {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "env_name", nullable = false)
    private String envName;

    @Column(name = "system_name", nullable = false)
    private String systemName;

    @Column(name = "connection_name", nullable = false)
    private String connectionName;

    @Column(name = "connection_type", nullable = false)
    private String connectionType;

    /**
     * JSON-serialized Map&lt;String, String&gt; of connection parameters.
     */
    @Column(name = "connection_parameters", columnDefinition = "TEXT")
    private String connectionParameters;
}

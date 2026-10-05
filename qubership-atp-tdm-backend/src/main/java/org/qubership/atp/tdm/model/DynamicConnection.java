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

import org.hibernate.annotations.UuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "dynamic_connection", uniqueConstraints = {
        @UniqueConstraint(name = "uq_dynamic_connection_system_name",
                columnNames = {"system_id", "connection_name"})
})
@Getter
@Setter
@NoArgsConstructor
public class DynamicConnection {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "system_id", nullable = false)
    private DynamicSystem system;

    @Column(name = "connection_name", nullable = false)
    private String connectionName;

    @Column(name = "connection_type", nullable = false)
    private String connectionType;

    /**
     * JSON-serialized Map&lt;String, String&gt; of connection parameters.
     */
    @Column(name = "connection_parameters", columnDefinition = "TEXT", nullable = false)
    private String connectionParameters;

    public DynamicConnection(DynamicSystem system,
                             String connectionName,
                             String connectionType,
                             String connectionParameters) {
        this.system = system;
        this.connectionName = connectionName;
        this.connectionType = connectionType;
        this.connectionParameters = connectionParameters;
    }
}

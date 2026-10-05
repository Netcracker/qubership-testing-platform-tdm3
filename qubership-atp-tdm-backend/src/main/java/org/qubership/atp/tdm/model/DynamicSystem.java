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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.hibernate.annotations.UuidGenerator;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "dynamic_system")
@Getter
@Setter
@NoArgsConstructor
public class DynamicSystem {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "env_id", nullable = false)
    private DynamicEnvironment env;

    @Column(name = "system_name", nullable = false)
    private String systemName;

    @OneToMany(mappedBy = "system", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Fetch(FetchMode.SUBSELECT)
    private List<DynamicConnection> connections = new ArrayList<>();

    public DynamicSystem(DynamicEnvironment env, String systemName) {
        this.env = env;
        this.systemName = systemName;
        this.connections = new ArrayList<>();
    }

    /**
     * Creates the system with a single connection. Existing callers that still pass one connection use this.
     */
    public DynamicSystem(DynamicEnvironment env,
                         String systemName,
                         String connectionType,
                         String connectionParameters) {
        this(env, systemName);
        addConnection(connectionType, connectionParameters);
    }

    /**
     * @deprecated The connection name is ignored. Connections are identified by their canonical type.
     */
    @Deprecated
    public DynamicSystem(DynamicEnvironment env,
                         String systemName,
                         String connectionName,
                         String connectionType,
                         String connectionParameters) {
        this(env, systemName, connectionType, connectionParameters);
    }

    public void addConnection(String connectionType, String connectionParameters) {
        if (this.connections == null) {
            this.connections = new ArrayList<>();
        }
        this.connections.add(new DynamicConnection(this, connectionType, connectionParameters));
    }

    /**
     * @deprecated The connection name is ignored. Connections are identified by their canonical type.
     */
    @Deprecated
    public void addConnection(String connectionName, String connectionType, String connectionParameters) {
        addConnection(connectionType, connectionParameters);
    }
}

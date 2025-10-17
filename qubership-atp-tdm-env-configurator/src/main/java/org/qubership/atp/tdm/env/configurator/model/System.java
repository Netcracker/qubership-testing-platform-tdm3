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

package org.qubership.atp.tdm.env.configurator.model;

import java.util.List;
import java.util.UUID;

import org.qubership.atp.tdm.env.configurator.exceptions.internal.TdmEnvDbConnectionException;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@EqualsAndHashCode(callSuper = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class System extends AbstractConfiguratorModel {

    private UUID environmentId;
    private List<Connection> connections;

    /**
     * Get connection by name.
     */
    public Connection getConnection(String connectionName) {
        return connections.stream()
                .filter(connection -> connectionName.equalsIgnoreCase(connection.getName()))
                .findFirst()
                .orElseThrow(() -> new TdmEnvDbConnectionException(connectionName));
    }

    public Server getServer(String name) {
        return new Server(getConnection(name), name);
    }
}

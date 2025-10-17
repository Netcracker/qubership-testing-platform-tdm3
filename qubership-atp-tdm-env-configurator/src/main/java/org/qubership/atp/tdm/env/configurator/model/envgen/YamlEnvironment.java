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

package org.qubership.atp.tdm.env.configurator.model.envgen;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class YamlEnvironment {

    private UUID id;
    private String name;
    private String clusterName;
    private UUID projectId;
    private List<YamlSystem> yamlSystems;
    private Object parameters;

    public YamlEnvironment(String name) {
        this.id = UUID.nameUUIDFromBytes(name.getBytes());
        this.name = name;
    }

    public void setYamlSystems(List<YamlSystem> yamlSystems) {
        this.yamlSystems = yamlSystems;
        yamlSystems.stream()
                .forEach(yamlSystem -> yamlSystem.setId(
                        UUID.nameUUIDFromBytes(String.format("%s/%s", this.name, yamlSystem.getName()).getBytes())));
        setConnectionId();
    }

    private void setConnectionId() {
        yamlSystems.forEach(yamlSystem -> {
            yamlSystem.getConnections().forEach(yamlConnection -> {
                yamlConnection.setId(
                        UUID.nameUUIDFromBytes(
                                String.format(
                                                "%s/%s/%s", this.name, yamlSystem.getName(), yamlConnection.getName()
                                        )
                                        .getBytes()
                        )
                );
            });
        });
    }

    public void setYamlSystemsWithCredentials(List<YamlSystem> yamlSystems) {
        yamlSystems.stream()
                .forEach(yamlSystem -> yamlSystem.setId(
                        UUID.nameUUIDFromBytes(String.format("%s/%s", this.name, yamlSystem.getName()).getBytes())));

        yamlSystems.forEach(yamlSystem -> {
            yamlSystem.getConnections().forEach(yamlConnection -> {
                yamlConnection.setId(
                        UUID.nameUUIDFromBytes(
                                String.format(
                                                "%s/%s/%s", this.name, yamlSystem.getName(), yamlConnection.getName()
                                        )
                                        .getBytes()
                        )
                );
            });
        });
        mergeConnections(yamlSystems);
    }

    private void mergeConnections(List<YamlSystem> yamlSystems) {
        this.yamlSystems.forEach(system -> {
            system.getConnections().forEach(connection -> {
                yamlSystems.forEach(yamlSystem -> {
                    YamlConnection yamlConnection = yamlSystem.getConnectionById(connection.getId());
                    if (yamlConnection != null) {
                        connection.getParameters().putAll(yamlConnection.getParameters());
                    }
                });
            });
        });
    }

    public List<UUID> getSystemIds() {
        return yamlSystems.stream().map(YamlSystem::getId).collect(Collectors.toList());
    }

    public YamlSystem getSystemById(UUID id) {
        return yamlSystems
                .stream()
                .filter(yamlSystem -> yamlSystem.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    public YamlSystem getSystemByName(String name) {
        return yamlSystems
                .stream()
                .filter(yamlSystem -> yamlSystem.getName().equals(name))
                .findFirst()
                .orElse(null);
    }

    public List<YamlConnection> getConnectionsSystemById(UUID id) {
        return getSystemById(id).getConnections();
    }
}

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

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class YamlSystem {

    private UUID id;
    private UUID projectId;
    private String name;
    @JsonProperty("connections")
    private List<YamlConnection> connections;

    public YamlConnection getConnectionById(UUID id) {
        return connections
                .stream()
                .filter(yamlSystem -> yamlSystem.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    public YamlConnection getConnectionByName(String name) {
        return connections
                .stream()
                .filter(yamlSystem -> yamlSystem.getName().equals(name))
                .findFirst()
                .orElse(null);
    }

    public List<String> getListConnections() {
        return connections
                .stream()
                .map(yamlConnection -> yamlConnection.getId().toString())
                .collect(Collectors.toList());
    }
}

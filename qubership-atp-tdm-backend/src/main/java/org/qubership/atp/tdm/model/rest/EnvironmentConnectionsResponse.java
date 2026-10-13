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

package org.qubership.atp.tdm.model.rest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "Connections of a dynamic environment. Secret parameter values are masked.")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EnvironmentConnectionsResponse {

    @Schema(description = "Project name from the request.")
    private String projectName;
    @Schema(description = "Environment name from the request.")
    private String envName;
    @Schema(description = "Systems in the environment. One element when systemName was set on the request.")
    private List<SystemConnections> systems = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SystemConnections {

        @Schema(description = "System name.")
        private String systemName;
        @Schema(description = "Connections of the system. Sensitive parameter values are \"***\".")
        private List<Connection> connections = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Connection {

        @Schema(description = "Canonical connection type: DB or HTTP.")
        private String type;
        @Schema(description = "Connection parameters. Sensitive parameter values are \"***\".")
        private Map<String, String> parameters;
    }
}

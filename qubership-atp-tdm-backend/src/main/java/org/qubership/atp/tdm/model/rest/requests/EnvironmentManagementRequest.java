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

package org.qubership.atp.tdm.model.rest.requests;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "Request body of every atp-env-controller operation (GET, POST, PUT, and DELETE "
        + "/api/tdm/rest/create-env). Its fields can be sent flat, as shown here, or nested under an "
        + "\"environment\" property; both are equivalent.")
@Data
public class EnvironmentManagementRequest {

    @Schema(description = "Project name from PROJECTS_INFO.")
    private String projectName;
    @Schema(description = "Environment name to create, update, or delete.")
    private String envName;
    @Schema(description = "System name within the environment. Read by POST and PUT.")
    private String systemName;
    @Schema(description = "New environment name. Read by PUT to rename the environment.")
    private String newEnvName;
    @Schema(description = "New system name. Read by PUT to rename systemName.")
    private String newSystemName;
    @Schema(description = "Read by DELETE: when set, only this system is deleted; otherwise the whole "
            + "environment is deleted.")
    private String systemDeleteName;
    @Schema(description = "One connection for systemName. Read by POST and PUT when connections is absent. "
            + "Ignored when connections is present.")
    private EnvironmentConnectionRequest connection;
    @Schema(description = "Connections for systemName. Read by POST and PUT. Preferred over connection when both "
            + "are sent. Ignored by GET.")
    private List<EnvironmentConnectionRequest> connections;
    private boolean connectionsPresent;

    @JsonSetter("connections")
    public void setConnections(List<EnvironmentConnectionRequest> connections) {
        this.connectionsPresent = true;
        this.connections = connections;
    }

    /**
     * Connections to persist. {@code connections} wins when it is present, including when it is empty.
     * Otherwise the legacy singular {@code connection} is used as a one-element list.
     */
    public List<EnvironmentConnectionRequest> resolvedConnections() {
        if (connectionsPresent) {
            return connections;
        }
        if (connection != null) {
            return Collections.singletonList(connection);
        }
        return Collections.emptyList();
    }

    @JsonProperty("environment")
    private void unpackEnvironment(Map<String, Object> environment) {
        this.projectName = (String) environment.get("projectName");
        this.envName = (String) environment.get("envName");
        this.systemName = (String) environment.get("systemName");
        if (environment.containsKey("newEnvName")) {
            this.newEnvName = (String) environment.get("newEnvName");
        }
        if (environment.containsKey("newSystemName")) {
            this.newSystemName = (String) environment.get("newSystemName");
        }
        if (environment.containsKey("systemDeleteName")) {
            this.systemDeleteName = (String) environment.get("systemDeleteName");
        }
        ObjectMapper mapper = new ObjectMapper();
        if (environment.containsKey("connections")) {
            Object connectionsObj = environment.get("connections");
            setConnections(connectionsObj == null ? null : mapper.convertValue(connectionsObj,
                    new TypeReference<List<EnvironmentConnectionRequest>>() {}));
        }
        Object connectionObj = environment.get("connection");
        if (connectionObj instanceof Map) {
            this.connection = mapper.convertValue(connectionObj, EnvironmentConnectionRequest.class);
        }
    }
}

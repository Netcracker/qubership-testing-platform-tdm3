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

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;

@Data
public class EnvironmentManagementRequest {

    private String projectName;
    private String envName;
    private String systemName;
    private EnvironmentConnectionRequest connection;

    @JsonProperty("environment")
    private void unpackEnvironment(Map<String, Object> environment) {
        this.projectName = (String) environment.get("projectName");
        this.envName = (String) environment.get("envName");
        this.systemName = (String) environment.get("systemName");
        Object connectionObj = environment.get("connection");
        if (connectionObj instanceof Map) {
            ObjectMapper mapper = new ObjectMapper();
            this.connection = mapper.convertValue(connectionObj, EnvironmentConnectionRequest.class);
        }
    }
}

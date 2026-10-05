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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class EnvironmentManagementRequestTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void unpackEnvironment_readsConnections() throws Exception {
        String json = "{\"environment\":{\"projectName\":\"MyProject\",\"envName\":\"myEnv\","
                + "\"systemName\":\"system1\",\"connections\":[{\"name\":\"DB\",\"type\":\"DB\","
                + "\"parameters\":{\"host\":\"localhost\"}},{\"name\":\"HTTP\",\"type\":\"HTTP\","
                + "\"parameters\":{\"url\":\"https://api.example.com\"}}]}}";

        EnvironmentManagementRequest request = mapper.readValue(json, EnvironmentManagementRequest.class);

        assertEquals("MyProject", request.getProjectName());
        assertEquals(2, request.resolvedConnections().size());
        assertEquals("HTTP", request.resolvedConnections().get(1).getName());
    }

    @Test
    void resolvedConnections_prefersConnectionsOverLegacyConnection() throws Exception {
        String json = "{\"projectName\":\"MyProject\",\"envName\":\"myEnv\",\"systemName\":\"system1\","
                + "\"connection\":{\"name\":\"DB\",\"type\":\"DB\",\"parameters\":{\"host\":\"localhost\"}},"
                + "\"connections\":[{\"name\":\"HTTP\",\"type\":\"HTTP\",\"parameters\":{\"url\":\"https://x\"}}]}";

        EnvironmentManagementRequest request = mapper.readValue(json, EnvironmentManagementRequest.class);

        assertEquals(1, request.resolvedConnections().size());
        assertEquals("HTTP", request.resolvedConnections().get(0).getName());
    }

    @Test
    void resolvedConnections_emptyConnectionsDoesNotFallBack() throws Exception {
        String json = "{\"connections\":[],\"connection\":{\"name\":\"DB\",\"type\":\"DB\","
                + "\"parameters\":{\"host\":\"localhost\"}}}";

        EnvironmentManagementRequest request = mapper.readValue(json, EnvironmentManagementRequest.class);

        assertTrue(request.resolvedConnections().isEmpty());
    }
}

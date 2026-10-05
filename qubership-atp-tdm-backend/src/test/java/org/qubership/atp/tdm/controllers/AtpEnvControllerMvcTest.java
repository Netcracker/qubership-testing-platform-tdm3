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

package org.qubership.atp.tdm.controllers;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.qubership.atp.tdm.AbstractEnvTest;
import org.qubership.atp.tdm.model.DynamicSystem;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class AtpEnvControllerMvcTest extends AbstractEnvTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void createEnv_newEnvAndSystem_returns200() throws Exception {
        mockMvc.perform(post(API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestBody(ENV_NAME, SYSTEM_NAME)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("SUCCESS"));

        assertEquals(1, countH2Rows(ENV_NAME));
    }

    @Test
    void createEnv_existingSystem_returns400() throws Exception {
        mockMvc.perform(post(API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestBody(ENV_NAME, SYSTEM_NAME)))
                .andExpect(status().isOk());

        mockMvc.perform(post(API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestBody(ENV_NAME, SYSTEM_NAME)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Use PUT to update.")));
    }

    @Test
    void createEnv_existingEnvNewSystem_addsSystem() throws Exception {
        mockMvc.perform(post(API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestBody(ENV_NAME, SYSTEM_NAME)))
                .andExpect(status().isOk());

        mockMvc.perform(post(API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestBody(ENV_NAME, SYSTEM_NAME_2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("SUCCESS"));

        assertEquals(2, countH2Rows(ENV_NAME));
    }

    @Test
    void deleteEnv_notFound_returns404() throws Exception {
        mockMvc.perform(delete(API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(deleteRequestBody(ENV_NAME, null)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("ERROR"));
    }

    @Test
    void deleteEnv_singleSystem_deletesFromDb() throws Exception {
        mockMvc.perform(post(API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestBody(ENV_NAME, SYSTEM_NAME)))
                .andExpect(status().isOk());

        mockMvc.perform(delete(API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(deleteRequestBody(ENV_NAME, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("SUCCESS"));

        assertEquals(0, countAllH2Rows());
    }

    @Test
    void deleteEnv_multipleSystemsNoSystemName_deletesAll() throws Exception {
        mockMvc.perform(post(API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestBody(ENV_NAME, SYSTEM_NAME)))
                .andExpect(status().isOk());
        mockMvc.perform(post(API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestBody(ENV_NAME, SYSTEM_NAME_2)))
                .andExpect(status().isOk());
        assertEquals(2, countH2Rows(ENV_NAME));

        mockMvc.perform(delete(API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(deleteRequestBody(ENV_NAME, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("SUCCESS"));

        assertEquals(0, countAllH2Rows());
    }

    @Test
    void deleteEnv_multipleSystemsWithSystemName_deletesOne() throws Exception {
        mockMvc.perform(post(API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestBody(ENV_NAME, SYSTEM_NAME)))
                .andExpect(status().isOk());
        mockMvc.perform(post(API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestBody(ENV_NAME, SYSTEM_NAME_2)))
                .andExpect(status().isOk());

        mockMvc.perform(delete(API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(deleteRequestBody(ENV_NAME, SYSTEM_NAME)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("SUCCESS"));

        assertEquals(1, countH2Rows(ENV_NAME));
        assertTrue(findH2Rows(ENV_NAME).stream().anyMatch(sys -> SYSTEM_NAME_2.equals(sys.getSystemName())));
    }

    @Test
    void deleteSystem_lastSystem_deletesSystemFromDb() throws Exception {
        mockMvc.perform(post(API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestBody(ENV_NAME, SYSTEM_NAME)))
                .andExpect(status().isOk());

        mockMvc.perform(delete(API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(deleteRequestBody(ENV_NAME, SYSTEM_NAME)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("SUCCESS"));

        assertEquals(0, countAllH2Rows());
    }

    @Test
    void renameEnv_updatesAllH2RowsAndCatalog() throws Exception {
        mockMvc.perform(post(API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestBody(ENV_NAME, SYSTEM_NAME)))
                .andExpect(status().isOk());
        mockMvc.perform(post(API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestBody(ENV_NAME, SYSTEM_NAME_2)))
                .andExpect(status().isOk());


        mockMvc.perform(put(API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequestBody(ENV_NAME, SYSTEM_NAME, NEW_ENV_NAME, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("SUCCESS"));

        assertEquals(2, countH2Rows(NEW_ENV_NAME));
        assertEquals(0, countH2Rows(ENV_NAME));

    }

    @Test
    void renameSystem_updatesCatalogSystemId() throws Exception {
        mockMvc.perform(post(API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestBody(ENV_NAME, SYSTEM_NAME)))
                .andExpect(status().isOk());


        mockMvc.perform(put(API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequestBody(ENV_NAME, SYSTEM_NAME, null, NEW_SYSTEM_NAME)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("SUCCESS"));

        assertEquals(1, countH2Rows(ENV_NAME));
    }

    @Test
    void renameSystem_preservesCatalogEnvironmentId() throws Exception {
        mockMvc.perform(post(API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestBody(ENV_NAME, SYSTEM_NAME)))
                .andExpect(status().isOk());


        mockMvc.perform(put(API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequestBody(ENV_NAME, SYSTEM_NAME, null, NEW_SYSTEM_NAME)))
                .andExpect(status().isOk());

    }

    @Test
    void renameEnvAndSystem_updatesH2AndCatalog() throws Exception {
        mockMvc.perform(post(API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestBody(ENV_NAME, SYSTEM_NAME)))
                .andExpect(status().isOk());


        mockMvc.perform(put(API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequestBody(ENV_NAME, SYSTEM_NAME, NEW_ENV_NAME, NEW_SYSTEM_NAME)))
                .andExpect(status().isOk());

        assertEquals(1, countH2Rows(NEW_ENV_NAME));
        DynamicSystem row = findH2Rows(NEW_ENV_NAME).get(0);
        assertEquals(NEW_SYSTEM_NAME, row.getSystemName());

    }

    @Test
    void updateEnv_notFound() throws Exception {
        mockMvc.perform(put(API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequestBody(ENV_NAME, SYSTEM_NAME, null, null)))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void updateEnv_newSystemNameAlreadyExists_returns400() throws Exception {
        mockMvc.perform(post(API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestBody(ENV_NAME, SYSTEM_NAME)))
                .andExpect(status().isOk());
        mockMvc.perform(post(API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestBody(ENV_NAME, SYSTEM_NAME_2)))
                .andExpect(status().isOk());

        mockMvc.perform(put(API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequestBody(ENV_NAME, SYSTEM_NAME, null, SYSTEM_NAME_2)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("already exists in environment")));
    }

    @Test
    void updateEnv_multipleSystemsWithSameEnvName_noException() throws Exception {
        mockMvc.perform(post(API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestBody(ENV_NAME, SYSTEM_NAME)))
                .andExpect(status().isOk());
        mockMvc.perform(post(API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestBody(ENV_NAME, SYSTEM_NAME_2)))
                .andExpect(status().isOk());

        mockMvc.perform(put(API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequestBody(ENV_NAME, SYSTEM_NAME, null, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("SUCCESS"));

        assertEquals(2, countH2Rows(ENV_NAME));
    }

    @Test
    void createEnv_twoConnections_storesBoth() throws Exception {
        mockMvc.perform(post(API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(twoConnectionBody(ENV_NAME, SYSTEM_NAME)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("SUCCESS"));

        assertEquals(2, findH2Rows(ENV_NAME).get(0).getConnections().size());
    }

    @Test
    void createEnv_nestedConnections_unpacks() throws Exception {
        String body = "{\"environment\":" + twoConnectionBody(ENV_NAME, SYSTEM_NAME) + "}";

        mockMvc.perform(post(API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        assertEquals(2, findH2Rows(ENV_NAME).get(0).getConnections().size());
    }

    @Test
    void createEnv_gitType_returns400() throws Exception {
        String body = createRequestBody(ENV_NAME, SYSTEM_NAME).replace("\"type\":\"DB\"", "\"type\":\"GIT\"");

        mockMvc.perform(post(API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("ERROR"))
                .andExpect(jsonPath("$.content").value(containsString("Allowed types: DB, HTTP.")));

        assertEquals(0, countH2Rows(ENV_NAME));
    }

    @Test
    void createEnv_lowercaseHttp_storedAsHttp() throws Exception {
        String body = "{\"projectName\":\"" + PROJECT_NAME + "\",\"envName\":\"" + ENV_NAME
                + "\",\"systemName\":\"" + SYSTEM_NAME + "\",\"connection\":"
                + "{\"name\":\"HTTP\",\"type\":\"http\",\"parameters\":{\"url\":\"https://api.example.com\"}}}";

        mockMvc.perform(post(API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        assertEquals("HTTP", findH2Rows(ENV_NAME).get(0).getConnections().get(0).getConnectionType());
    }

    @Test
    void createEnv_duplicateTypes_returns400() throws Exception {
        String body = "{\"projectName\":\"" + PROJECT_NAME + "\",\"envName\":\"" + ENV_NAME
                + "\",\"systemName\":\"" + SYSTEM_NAME + "\",\"connections\":["
                + "{\"name\":\"DB\",\"type\":\"DB\",\"parameters\":{\"host\":\"localhost\"}},"
                + "{\"name\":\"different\",\"type\":\"db\",\"parameters\":{\"url\":\"https://api.example.com\"}}]}";

        mockMvc.perform(post(API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.content").value(containsString("Duplicate connection type")));
    }

    @Test
    void updateEnv_addsSecondConnectionAndLeavesFirst() throws Exception {
        mockMvc.perform(post(API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestBody(ENV_NAME, SYSTEM_NAME)))
                .andExpect(status().isOk());

        String addHttp = "{\"projectName\":\"" + PROJECT_NAME + "\",\"envName\":\"" + ENV_NAME
                + "\",\"systemName\":\"" + SYSTEM_NAME + "\",\"connections\":["
                + "{\"name\":\"HTTP\",\"type\":\"HTTP\",\"parameters\":{\"url\":\"https://api.example.com\","
                + "\"token\":\"abc123\"}}]}";
        mockMvc.perform(put(API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addHttp))
                .andExpect(status().isOk());

        assertEquals(2, findH2Rows(ENV_NAME).get(0).getConnections().size());

        mockMvc.perform(put(API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequestBody(ENV_NAME, SYSTEM_NAME, NEW_ENV_NAME, NEW_SYSTEM_NAME)))
                .andExpect(status().isOk());

        assertEquals(NEW_SYSTEM_NAME, findH2Rows(NEW_ENV_NAME).get(0).getSystemName());
        assertEquals(2, findH2Rows(NEW_ENV_NAME).get(0).getConnections().size());
    }

    @Test
    void updateEnv_invalidType_returns400AndLeavesData() throws Exception {
        mockMvc.perform(post(API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestBody(ENV_NAME, SYSTEM_NAME)))
                .andExpect(status().isOk());

        String body = updateRequestBody(ENV_NAME, SYSTEM_NAME, null, null).replace("\"type\":\"DB\"", "\"type\":\"GIT\"");
        mockMvc.perform(put(API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        assertEquals("DB", findH2Rows(ENV_NAME).get(0).getConnections().get(0).getConnectionType());
        assertTrue(findH2Rows(ENV_NAME).get(0).getConnections().get(0).getConnectionParameters().contains("localhost"));
    }

    @Test
    void getConnections_masksSecrets() throws Exception {
        String body = "{\"projectName\":\"" + PROJECT_NAME + "\",\"envName\":\"" + ENV_NAME
                + "\",\"systemName\":\"" + SYSTEM_NAME + "\",\"connections\":["
                + "{\"name\":\"DB\",\"type\":\"DB\",\"parameters\":{\"host\":\"db.example.com\",\"port\":\"5432\","
                + "\"db_login\":\"tdm\",\"db_password\":\"secret\",\"DB_PASSWORD\":\"secret\",\"apiToken\":\"t\"}},"
                + "{\"name\":\"HTTP\",\"type\":\"HTTP\",\"parameters\":{\"url\":\"https://api.example.com\","
                + "\"token\":\"abc123\"}}]}";
        mockMvc.perform(post(API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        String getBody = "{\"environment\":{\"projectName\":\"" + PROJECT_NAME + "\",\"envName\":\"" + ENV_NAME
                + "\",\"systemName\":\"" + SYSTEM_NAME + "\",\"connection\":{\"name\":\"ignored\",\"type\":\"DB\","
                + "\"parameters\":{\"host\":\"x\"}}}}";
        mockMvc.perform(get(API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(getBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectName").value(PROJECT_NAME))
                .andExpect(jsonPath("$.systems.length()").value(1))
                .andExpect(jsonPath("$.systems[0].systemName").value(SYSTEM_NAME))
                .andExpect(jsonPath("$.systems[0].connections[0].parameters.host").value("db.example.com"))
                .andExpect(jsonPath("$.systems[0].connections[0].parameters.db_login").value("tdm"))
                .andExpect(jsonPath("$.systems[0].connections[0].parameters.db_password").value("***"))
                .andExpect(jsonPath("$.systems[0].connections[0].parameters.DB_PASSWORD").value("***"))
                .andExpect(jsonPath("$.systems[0].connections[0].parameters.apiToken").value("***"))
                .andExpect(jsonPath("$.systems[0].connections[1].parameters.url").value("https://api.example.com"))
                .andExpect(jsonPath("$.systems[0].connections[1].parameters.token").value("***"));

        assertTrue(findH2Rows(ENV_NAME).get(0).getConnections().get(0).getConnectionParameters().contains("secret"));
    }

    @Test
    void getConnections_omitsSystemName_returnsAllSystems() throws Exception {
        mockMvc.perform(post(API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestBody(ENV_NAME, SYSTEM_NAME)))
                .andExpect(status().isOk());
        mockMvc.perform(post(API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestBody(ENV_NAME, SYSTEM_NAME_2)))
                .andExpect(status().isOk());

        String getBody = "{\"projectName\":\"" + PROJECT_NAME + "\",\"envName\":\"" + ENV_NAME + "\"}";
        mockMvc.perform(get(API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(getBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.systems.length()").value(2));
    }

    @Test
    void getConnections_unknownEnvOrSystem_returns404() throws Exception {
        mockMvc.perform(get(API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectName\":\"" + PROJECT_NAME + "\",\"envName\":\"missing\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("ERROR"));

        mockMvc.perform(post(API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestBody(ENV_NAME, SYSTEM_NAME)))
                .andExpect(status().isOk());

        mockMvc.perform(get(API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectName\":\"" + PROJECT_NAME + "\",\"envName\":\"" + ENV_NAME
                                + "\",\"systemName\":\"missing\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("ERROR"));
    }

    private String twoConnectionBody(String envName, String systemName) {
        return "{\"projectName\":\"" + PROJECT_NAME + "\",\"envName\":\"" + envName
                + "\",\"systemName\":\"" + systemName + "\",\"connections\":["
                + "{\"name\":\"DB\",\"type\":\"DB\",\"parameters\":{\"host\":\"localhost\",\"port\":\"5432\"}},"
                + "{\"name\":\"HTTP\",\"type\":\"HTTP\",\"parameters\":{\"url\":\"https://api.example.com\"}}]}";
    }
}

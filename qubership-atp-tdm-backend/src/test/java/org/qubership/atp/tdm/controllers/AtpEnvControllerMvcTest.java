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
}

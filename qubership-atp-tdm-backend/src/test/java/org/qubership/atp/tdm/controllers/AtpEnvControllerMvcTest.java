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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.qubership.atp.tdm.AbstractEnvTest;
import org.qubership.atp.tdm.env.configurator.model.envgen.YamlEnvironment;
import org.qubership.atp.tdm.model.DynamicEnvironment;
import org.qubership.atp.tdm.model.TestDataTableCatalog;
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
        verify(environmentsService).registerEnvironmentInCache(
                eq(PROJECT_ID), eq(ENV_NAME), eq(SYSTEM_NAME), anyString(), anyString(), anyMap());
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
        verify(environmentsService).addSystemToEnvironment(
                eq(PROJECT_ID), eq(ENVIRONMENT_ID), eq(SYSTEM_NAME_2), anyString(), anyString(), anyMap());
    }

    @Test
    void deleteEnv_notFound_returns404() throws Exception {
        mockMvc.perform(delete(API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(deleteRequestBody(ENV_NAME, null)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("ERROR"));

        verify(environmentsService, never()).removeEnvironmentFromCache(any());
    }

    @Test
    void deleteEnv_singleSystem_removesFromCacheAndDb() throws Exception {
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
        verify(environmentsService).removeEnvironmentFromCache(ENVIRONMENT_ID);
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
        verify(environmentsService).removeEnvironmentFromCache(ENVIRONMENT_ID);
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
        assertTrue(findH2Rows(ENV_NAME).stream().anyMatch(row -> SYSTEM_NAME_2.equals(row.getSystemName())));
        verify(environmentsService).removeSystemFromCache(ENVIRONMENT_ID, SYSTEM_NAME);
        verify(environmentsService, never()).removeEnvironmentFromCache(any());
    }

    @Test
    void deleteSystem_lastSystem_removesEnvFromCache() throws Exception {
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
        verify(environmentsService).removeSystemFromCache(ENVIRONMENT_ID, SYSTEM_NAME);
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

        UUID oldSystemId1 = YamlEnvironment.composeSystemId(ENV_NAME, SYSTEM_NAME);
        UUID oldSystemId2 = YamlEnvironment.composeSystemId(ENV_NAME, SYSTEM_NAME_2);
        TestDataTableCatalog catalog1 = createCatalogEntry(oldSystemId1, ENVIRONMENT_ID, "table1");
        TestDataTableCatalog catalog2 = createCatalogEntry(oldSystemId2, ENVIRONMENT_ID, "table2");

        mockMvc.perform(put(API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequestBody(ENV_NAME, SYSTEM_NAME, NEW_ENV_NAME, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("SUCCESS"));

        assertEquals(2, countH2Rows(NEW_ENV_NAME));
        assertEquals(0, countH2Rows(ENV_NAME));
        for (DynamicEnvironment row : findH2Rows(NEW_ENV_NAME)) {
            assertEquals(NEW_ENV_NAME, row.getEnvName());
        }

        UUID newSystemId1 = YamlEnvironment.composeSystemId(NEW_ENV_NAME, SYSTEM_NAME);
        UUID newSystemId2 = YamlEnvironment.composeSystemId(NEW_ENV_NAME, SYSTEM_NAME_2);
        assertEquals(newSystemId1, catalogRepository.findByTableName(catalog1.getTableName()).getSystemId());
        assertEquals(newSystemId2, catalogRepository.findByTableName(catalog2.getTableName()).getSystemId());
        assertEquals(ENVIRONMENT_ID, catalogRepository.findByTableName(catalog1.getTableName()).getEnvironmentId());
    }

    @Test
    void renameSystem_updatesCatalogSystemId() throws Exception {
        mockMvc.perform(post(API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestBody(ENV_NAME, SYSTEM_NAME)))
                .andExpect(status().isOk());

        UUID oldSystemId = YamlEnvironment.composeSystemId(ENV_NAME, SYSTEM_NAME);
        TestDataTableCatalog catalog = createCatalogEntry(oldSystemId, ENVIRONMENT_ID, "table1");

        mockMvc.perform(put(API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequestBody(ENV_NAME, SYSTEM_NAME, null, NEW_SYSTEM_NAME)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("SUCCESS"));

        UUID newSystemId = YamlEnvironment.composeSystemId(ENV_NAME, NEW_SYSTEM_NAME);
        assertEquals(1, countH2Rows(ENV_NAME));
        assertEquals(newSystemId, findH2Rows(ENV_NAME).get(0).getId());
        assertEquals(newSystemId, catalogRepository.findByTableName(catalog.getTableName()).getSystemId());
        assertEquals(ENVIRONMENT_ID, catalogRepository.findByTableName(catalog.getTableName()).getEnvironmentId());
    }

    @Test
    void renameSystem_preservesCatalogEnvironmentId() throws Exception {
        mockMvc.perform(post(API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestBody(ENV_NAME, SYSTEM_NAME)))
                .andExpect(status().isOk());

        UUID oldSystemId = YamlEnvironment.composeSystemId(ENV_NAME, SYSTEM_NAME);
        TestDataTableCatalog catalog = createCatalogEntry(oldSystemId, ENVIRONMENT_ID, "table1");

        mockMvc.perform(put(API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequestBody(ENV_NAME, SYSTEM_NAME, null, NEW_SYSTEM_NAME)))
                .andExpect(status().isOk());

        assertEquals(ENVIRONMENT_ID, catalogRepository.findByTableName(catalog.getTableName()).getEnvironmentId());
    }

    @Test
    void renameEnvAndSystem_updatesH2AndCatalog() throws Exception {
        mockMvc.perform(post(API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestBody(ENV_NAME, SYSTEM_NAME)))
                .andExpect(status().isOk());

        UUID oldSystemId = YamlEnvironment.composeSystemId(ENV_NAME, SYSTEM_NAME);
        TestDataTableCatalog catalog = createCatalogEntry(oldSystemId, ENVIRONMENT_ID, "table1");

        mockMvc.perform(put(API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequestBody(ENV_NAME, SYSTEM_NAME, NEW_ENV_NAME, NEW_SYSTEM_NAME)))
                .andExpect(status().isOk());

        assertEquals(1, countH2Rows(NEW_ENV_NAME));
        DynamicEnvironment row = findH2Rows(NEW_ENV_NAME).get(0);
        assertEquals(NEW_ENV_NAME, row.getEnvName());
        assertEquals(NEW_SYSTEM_NAME, row.getSystemName());

        UUID newSystemId = YamlEnvironment.composeSystemId(NEW_ENV_NAME, NEW_SYSTEM_NAME);
        assertEquals(newSystemId, catalogRepository.findByTableName(catalog.getTableName()).getSystemId());
    }

    @Test
    void updateEnv_notFound() throws Exception {
        mockMvc.perform(put(API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequestBody(ENV_NAME, SYSTEM_NAME, null, null)))
                .andExpect(status().is5xxServerError());
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

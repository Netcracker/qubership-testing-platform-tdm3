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

package org.qubership.atp.tdm.service.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.qubership.atp.tdm.AbstractTestDataTest;
import org.qubership.atp.tdm.env.configurator.model.LazyEnvironment;
import org.qubership.atp.tdm.model.DynamicEnvironment;
import org.qubership.atp.tdm.repo.DynamicEnvironmentRepository;
import org.qubership.atp.tdm.service.AtpActionService;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Tests that loadDynamicEnvironmentsFromDb() correctly reloads H2-persisted
 * DynamicEnvironment records into the in-memory cache on application startup.
 */
public class DynamicEnvironmentStartupLoaderTest extends AbstractTestDataTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private AtpActionService atpActionService;

    @Autowired
    private DynamicEnvironmentRepository dynamicEnvironmentRepository;

    @BeforeEach
    public void setUp() {
        when(environmentsService.getLazyProjectByName(any())).thenReturn(lazyProject);
        when(environmentsService.getLazyEnvironmentByName(any(), any())).thenReturn(lazyEnvironment);
        when(environmentsService.getLazySystemByName(any(), any(), any())).thenReturn(lazySystem);
        when(environmentsService.getConnectionsSystemById(any(), any())).thenReturn(connections);
    }

    @Test
    public void loadDynamicEnvironmentsFromDb_persistedRecord_isRestoredToCache() throws Exception {
        UUID dynProjectId = UUID.randomUUID();
        String dynEnvName = "dynamic-startup-test-env-" + dynProjectId;
        String dynSystemName = "dynamic-system";
        String dynConnectionName = "DB";
        String dynConnectionType = "DB";

        Map<String, String> parameters = new HashMap<>();
        parameters.put("host", "localhost");
        parameters.put("port", "5432");
        String parametersJson = OBJECT_MAPPER.writeValueAsString(parameters);

        UUID recordId = UUID.nameUUIDFromBytes(dynEnvName.getBytes());
        DynamicEnvironment record = new DynamicEnvironment(
                recordId, dynProjectId, dynEnvName, dynSystemName,
                dynConnectionName, dynConnectionType, parametersJson);
        dynamicEnvironmentRepository.save(record);

        LazyEnvironment capturedEnv = null;
        try {
            when(environmentsService.registerEnvironmentInCache(any(), any(), any(), any(), any(), any()))
                    .thenAnswer(invocation -> {
                        LazyEnvironment env = new LazyEnvironment();
                        env.setId(UUID.nameUUIDFromBytes(
                                invocation.<String>getArgument(1).getBytes()));
                        env.setName(invocation.getArgument(1));
                        env.setProjectId(invocation.getArgument(0));
                        return env;
                    });

            ((AtpActionServiceImpl) atpActionService).loadDynamicEnvironmentsFromDb();

            org.mockito.ArgumentCaptor<String> envNameCaptor =
                    org.mockito.ArgumentCaptor.forClass(String.class);
            org.mockito.Mockito.verify(environmentsService).registerEnvironmentInCache(
                    org.mockito.ArgumentMatchers.eq(dynProjectId),
                    envNameCaptor.capture(),
                    org.mockito.ArgumentMatchers.eq(dynSystemName),
                    org.mockito.ArgumentMatchers.eq(dynConnectionName),
                    org.mockito.ArgumentMatchers.eq(dynConnectionType),
                    org.mockito.ArgumentMatchers.anyMap());

            Assertions.assertEquals(dynEnvName, envNameCaptor.getValue(),
                    "loadDynamicEnvironmentsFromDb should call registerEnvironmentInCache with the persisted env name");
        } finally {
            dynamicEnvironmentRepository.deleteById(recordId);
        }
    }

    @Test
    public void loadDynamicEnvironmentsFromDb_invalidParametersJson_skipsRecord() {
        UUID dynProjectId = UUID.randomUUID();
        String dynEnvName = "dynamic-startup-bad-params-" + dynProjectId;
        UUID recordId = UUID.nameUUIDFromBytes(dynEnvName.getBytes());

        DynamicEnvironment record = new DynamicEnvironment(
                recordId, dynProjectId, dynEnvName, "some-system",
                "DB", "DB", "not-valid-json{{");
        dynamicEnvironmentRepository.save(record);

        try {
            Assertions.assertDoesNotThrow(
                    () -> ((AtpActionServiceImpl) atpActionService).loadDynamicEnvironmentsFromDb(),
                    "loadDynamicEnvironmentsFromDb should not throw when parameters JSON is malformed");
        } finally {
            dynamicEnvironmentRepository.deleteById(recordId);
        }
    }
}

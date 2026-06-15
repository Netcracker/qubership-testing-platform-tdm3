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
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.qubership.atp.tdm.env.configurator.model.LazyEnvironment;
import org.qubership.atp.tdm.env.configurator.service.EnvironmentsService;
import org.qubership.atp.tdm.model.DynamicEnvironment;
import org.qubership.atp.tdm.model.DynamicSystem;
import org.qubership.atp.tdm.repo.AtpActionRepository;
import org.qubership.atp.tdm.repo.DynamicEnvironmentRepository;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Tests that loadDynamicEnvironmentsFromDb() correctly reloads H2-persisted
 * DynamicEnvironment records into the in-memory cache on application startup.
 */
public class DynamicEnvironmentStartupLoaderTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private AtpActionServiceImpl atpActionService;
    private DynamicEnvironmentRepository dynamicEnvironmentRepository;
    private EnvironmentsService environmentsService;

    @BeforeEach
    public void setUp() {
        environmentsService = mock(EnvironmentsService.class);
        dynamicEnvironmentRepository = mock(DynamicEnvironmentRepository.class);
        atpActionService = new AtpActionServiceImpl(
                environmentsService, mock(AtpActionRepository.class), dynamicEnvironmentRepository);
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

        UUID envId = UUID.nameUUIDFromBytes(dynEnvName.getBytes());
        DynamicEnvironment env = new DynamicEnvironment(envId, dynProjectId, dynEnvName);
        DynamicSystem sys = new DynamicSystem(
                UUID.nameUUIDFromBytes((dynEnvName + dynSystemName).getBytes()),
                env, dynSystemName, dynConnectionName, dynConnectionType, parametersJson);
        env.getSystems().add(sys);

        when(dynamicEnvironmentRepository.findAllWithSystems()).thenReturn(Collections.singletonList(env));
        when(environmentsService.registerEnvironmentInCache(any(), any(), any(), any(), any(), any()))
                .thenReturn(LazyEnvironment.builder()
                        .id(envId)
                        .name(dynEnvName)
                        .projectId(dynProjectId)
                        .build());

        atpActionService.loadDynamicEnvironmentsFromDb();

        verify(environmentsService).registerEnvironmentInCache(
                eq(dynProjectId), eq(dynEnvName), eq(dynSystemName),
                eq(dynConnectionName), eq(dynConnectionType), anyMap());
        verify(environmentsService, never()).addSystemToEnvironment(any(), any(), any(), any(), any(), any());
    }

    @Test
    public void loadDynamicEnvironmentsFromDb_persistedRecordSeveralSystems_isRestoredToCache() throws Exception {
        UUID dynProjectId = UUID.randomUUID();
        String dynEnvName = "dynamic-startup-test-env-" + dynProjectId;
        String dynEnvName2 = "dynamic-startup-test-env2-" + dynProjectId;
        String systemName1 = "dynamic-system-1";
        String systemName2 = "dynamic-system-2";
        String systemName3 = "dynamic-system-3";
        String systemName4 = "dynamic-system-4";
        String dynConnectionName = "DB";
        String dynConnectionType = "DB";

        Map<String, String> parameters = new HashMap<>();
        parameters.put("host", "localhost");
        parameters.put("port", "5432");
        String parametersJson = OBJECT_MAPPER.writeValueAsString(parameters);

        UUID expectedEnvId = UUID.nameUUIDFromBytes(dynEnvName.getBytes());
        UUID expectedEnvId2 = UUID.nameUUIDFromBytes(dynEnvName2.getBytes());

        DynamicEnvironment env1 = new DynamicEnvironment(expectedEnvId, dynProjectId, dynEnvName);
        DynamicSystem sys1 = new DynamicSystem(UUID.nameUUIDFromBytes((dynEnvName + systemName1).getBytes()),
                env1, systemName1, dynConnectionName, dynConnectionType, parametersJson);
        DynamicSystem sys2 = new DynamicSystem(UUID.nameUUIDFromBytes((dynEnvName + systemName2).getBytes()),
                env1, systemName2, dynConnectionName, dynConnectionType, parametersJson);
        env1.getSystems().add(sys1);
        env1.getSystems().add(sys2);

        DynamicEnvironment env2 = new DynamicEnvironment(expectedEnvId2, dynProjectId, dynEnvName2);
        DynamicSystem sys3 = new DynamicSystem(UUID.nameUUIDFromBytes((dynEnvName2 + systemName3).getBytes()),
                env2, systemName3, dynConnectionName, dynConnectionType, parametersJson);
        DynamicSystem sys4 = new DynamicSystem(UUID.nameUUIDFromBytes((dynEnvName2 + systemName4).getBytes()),
                env2, systemName4, dynConnectionName, dynConnectionType, parametersJson);
        env2.getSystems().add(sys3);
        env2.getSystems().add(sys4);

        List<DynamicEnvironment> list = new ArrayList<>();
        list.add(env1);
        list.add(env2);

        when(dynamicEnvironmentRepository.findAllWithSystems()).thenReturn(list);
        when(environmentsService.registerEnvironmentInCache(any(), any(), any(), any(), any(), any()))
                .thenReturn(LazyEnvironment.builder()
                        .id(expectedEnvId)
                        .name(dynEnvName)
                        .projectId(dynProjectId)
                        .build())
                .thenReturn(LazyEnvironment.builder()
                        .id(expectedEnvId2)
                        .name(dynEnvName2)
                        .projectId(dynProjectId)
                        .build());

        atpActionService.loadDynamicEnvironmentsFromDb();

        verify(environmentsService).registerEnvironmentInCache(
                eq(dynProjectId), eq(dynEnvName), eq(systemName1),
                eq(dynConnectionName), eq(dynConnectionType), anyMap());
        verify(environmentsService).addSystemToEnvironment(
                eq(dynProjectId), eq(expectedEnvId), eq(systemName2),
                eq(dynConnectionName), eq(dynConnectionType), anyMap());
        verify(environmentsService).registerEnvironmentInCache(
                eq(dynProjectId), eq(dynEnvName2), eq(systemName3),
                eq(dynConnectionName), eq(dynConnectionType), anyMap());
        verify(environmentsService).addSystemToEnvironment(
                eq(dynProjectId), eq(expectedEnvId2), eq(systemName4),
                eq(dynConnectionName), eq(dynConnectionType), anyMap());
        verifyNoMoreInteractions(environmentsService);
    }

    @Test
    public void loadDynamicEnvironmentsFromDb_invalidParametersJson_skipsRecord() {
        UUID dynProjectId = UUID.randomUUID();
        String dynEnvName = "dynamic-startup-bad-params-" + dynProjectId;
        UUID envId = UUID.nameUUIDFromBytes(dynEnvName.getBytes());

        DynamicEnvironment env = new DynamicEnvironment(envId, dynProjectId, dynEnvName);
        DynamicSystem sys = new DynamicSystem(
                UUID.nameUUIDFromBytes((dynEnvName + "some-system").getBytes()),
                env, "some-system", "DB", "DB", "not-valid-json{{");
        env.getSystems().add(sys);

        when(dynamicEnvironmentRepository.findAllWithSystems()).thenReturn(Collections.singletonList(env));

        Assertions.assertDoesNotThrow(
                () -> atpActionService.loadDynamicEnvironmentsFromDb(),
                "loadDynamicEnvironmentsFromDb should not throw when parameters JSON is malformed");
        verify(environmentsService, never()).registerEnvironmentInCache(any(), any(), any(), any(), any(), any());
        verify(environmentsService, never()).addSystemToEnvironment(any(), any(), any(), any(), any(), any());
    }
}

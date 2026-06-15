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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.qubership.atp.tdm.AbstractTest;
import org.qubership.atp.tdm.env.configurator.exceptions.internal.TdmEnvConvertLazyEnvironmentByNameException;
import org.qubership.atp.tdm.env.configurator.model.LazyEnvironment;
import org.qubership.atp.tdm.env.configurator.model.LazySystem;
import org.qubership.atp.tdm.env.configurator.model.System;
import org.qubership.atp.tdm.env.configurator.service.CacheService;
import org.qubership.atp.tdm.env.configurator.service.EnvironmentsService;
import org.qubership.atp.tdm.model.DynamicEnvironment;
import org.qubership.atp.tdm.model.DynamicSystem;
import org.qubership.atp.tdm.repo.DynamicEnvironmentRepository;
import org.qubership.atp.tdm.repo.DynamicSystemRepository;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Spring-context variant of {@link DynamicEnvironmentStartupLoaderTest}.
 * Uses real H2 persistence and {@link EnvironmentsService} instead of mocks.
 */
class DynamicEnvironmentStartupLoaderSpringTest extends AbstractTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private AtpActionServiceImpl atpActionService;

    @Autowired
    private DynamicEnvironmentRepository dynamicEnvironmentRepository;

    @Autowired
    private DynamicSystemRepository dynamicSystemRepository;

    @Autowired
    private EnvironmentsService environmentsService;

    @Autowired
    private CacheService cacheService;

    @BeforeEach
    void setUp() {
        dynamicSystemRepository.deleteAll();
        dynamicEnvironmentRepository.deleteAll();
        cacheService.getEnvironments().forEach(env -> cacheService.remove(env.getId()));
    }

    @Test
    void loadDynamicEnvironmentsFromDb_persistedRecord_isRestoredToCache() throws Exception {
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
        DynamicEnvironment env = dynamicEnvironmentRepository.save(
                new DynamicEnvironment(envId, dynProjectId, dynEnvName));
        dynamicSystemRepository.save(new DynamicSystem(
                UUID.nameUUIDFromBytes((dynEnvName + dynSystemName).getBytes()),
                env, dynSystemName, dynConnectionName, dynConnectionType, parametersJson));

        atpActionService.loadDynamicEnvironmentsFromDb();

        LazyEnvironment lazyEnvironment = environmentsService.getLazyEnvironmentByName(dynProjectId, dynEnvName);
        assertEquals(dynEnvName, lazyEnvironment.getName());
        assertEquals(dynProjectId, lazyEnvironment.getProjectId());

        System system = environmentsService.getFullSystemByName(lazyEnvironment.getId(), dynSystemName);
        assertEquals("localhost", system.getConnections().get(0).getParameters().get("host"));
        assertEquals("5432", system.getConnections().get(0).getParameters().get("port"));
        assertEquals(1, environmentsService.getLazySystems(lazyEnvironment.getId()).size());
    }

    @Test
    void loadDynamicEnvironmentsFromDb_persistedRecordSeveralSystems_isRestoredToCache() throws Exception {
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

        DynamicEnvironment env1 = dynamicEnvironmentRepository.save(
                new DynamicEnvironment(UUID.nameUUIDFromBytes(dynEnvName.getBytes()), dynProjectId, dynEnvName));
        dynamicSystemRepository.save(new DynamicSystem(
                UUID.nameUUIDFromBytes((dynEnvName + systemName1).getBytes()),
                env1, systemName1, dynConnectionName, dynConnectionType, parametersJson));
        dynamicSystemRepository.save(new DynamicSystem(
                UUID.nameUUIDFromBytes((dynEnvName + systemName2).getBytes()),
                env1, systemName2, dynConnectionName, dynConnectionType, parametersJson));

        DynamicEnvironment env2 = dynamicEnvironmentRepository.save(
                new DynamicEnvironment(UUID.nameUUIDFromBytes(dynEnvName2.getBytes()), dynProjectId, dynEnvName2));
        dynamicSystemRepository.save(new DynamicSystem(
                UUID.nameUUIDFromBytes((dynEnvName2 + systemName3).getBytes()),
                env2, systemName3, dynConnectionName, dynConnectionType, parametersJson));
        dynamicSystemRepository.save(new DynamicSystem(
                UUID.nameUUIDFromBytes((dynEnvName2 + systemName4).getBytes()),
                env2, systemName4, dynConnectionName, dynConnectionType, parametersJson));

        atpActionService.loadDynamicEnvironmentsFromDb();

        assertEnvironmentHasSystems(dynProjectId, dynEnvName, systemName1, systemName2);
        assertEnvironmentHasSystems(dynProjectId, dynEnvName2, systemName3, systemName4);
    }

    @Test
    void loadDynamicEnvironmentsFromDb_invalidParametersJson_skipsRecord() {
        UUID dynProjectId = UUID.randomUUID();
        String dynEnvName = "dynamic-startup-bad-params-" + dynProjectId;
        UUID envId = UUID.nameUUIDFromBytes(dynEnvName.getBytes());

        DynamicEnvironment env = dynamicEnvironmentRepository.save(
                new DynamicEnvironment(envId, dynProjectId, dynEnvName));
        dynamicSystemRepository.save(new DynamicSystem(
                UUID.nameUUIDFromBytes((dynEnvName + "some-system").getBytes()),
                env, "some-system", "DB", "DB", "not-valid-json{{"));

        assertDoesNotThrow(
                () -> atpActionService.loadDynamicEnvironmentsFromDb(),
                "loadDynamicEnvironmentsFromDb should not throw when parameters JSON is malformed");
        assertThrows(TdmEnvConvertLazyEnvironmentByNameException.class,
                () -> environmentsService.getLazyEnvironmentByName(dynProjectId, dynEnvName));
    }

    private void assertEnvironmentHasSystems(UUID projectId, String envName, String... systemNames) {
        LazyEnvironment lazyEnvironment = environmentsService.getLazyEnvironmentByName(projectId, envName);
        List<LazySystem> systems = environmentsService.getLazySystems(lazyEnvironment.getId());
        assertEquals(systemNames.length, systems.size());
        for (String systemName : systemNames) {
            assertDoesNotThrow(() -> environmentsService.getLazySystemByName(
                    projectId, lazyEnvironment.getId(), systemName));
        }
    }
}

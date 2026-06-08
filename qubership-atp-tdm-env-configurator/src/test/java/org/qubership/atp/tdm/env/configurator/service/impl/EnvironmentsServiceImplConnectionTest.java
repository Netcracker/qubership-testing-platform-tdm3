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

package org.qubership.atp.tdm.env.configurator.service.impl;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.qubership.atp.tdm.env.configurator.model.LazyEnvironment;
import org.qubership.atp.tdm.env.configurator.model.LazySystem;
import org.qubership.atp.tdm.env.configurator.service.CacheService;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

@ExtendWith(MockitoExtension.class)
class EnvironmentsServiceImplConnectionTest {

    private static final String ENV_NAME = "testEnv";
    private static final String SYSTEM_NAME = "testSystem";
    private static final String CONNECTION_NAME = "DB";
    private static final String CONNECTION_TYPE = "DB";

    @Mock
    private CacheManager cacheManager;

    @Mock
    private Cache mockCache;

    private CacheService cacheService;
    private EnvironmentsServiceImpl environmentsService;

    private UUID projectId;

    @BeforeEach
    void setUp() {
        projectId = UUID.randomUUID();
        cacheService = new CacheService();
        environmentsService = new EnvironmentsServiceImpl(cacheService, cacheManager);

        lenient().when(cacheManager.getCache(anyString())).thenReturn(mockCache);
    }

    private Map<String, String> buildParams(String dbLogin) {
        Map<String, String> params = new HashMap<>();
        params.put("db_login", dbLogin);
        params.put("db_password", "secret");
        params.put("jdbc_url", "jdbc:h2:mem:test");
        params.put("db_type", "postgresql");
        return params;
    }

    // ── updateConnectionInCache ───────────────────────────────────────────────

    @Test
    void updateConnectionInCache_connectionIdIsSet_getSystemsDoesNotThrow() {
        LazyEnvironment lazyEnv = environmentsService.registerEnvironmentInCache(
                projectId, ENV_NAME, SYSTEM_NAME, CONNECTION_NAME, CONNECTION_TYPE, buildParams("user1"));

        UUID envId = lazyEnv.getId();
        environmentsService.updateConnectionInCache(envId, SYSTEM_NAME, CONNECTION_NAME, CONNECTION_TYPE,
                buildParams("user2"));

        List<LazySystem> systems = assertDoesNotThrow(
                () -> environmentsService.getLazySystems(envId));
        assertFalse(systems.isEmpty());
        assertFalse(systems.get(0).getConnections().isEmpty());
        assertNotNull(systems.get(0).getConnections().get(0));
    }

    @Test
    void updateConnectionInCache_parametersUpdated_newParamsReflected() {
        LazyEnvironment lazyEnv = environmentsService.registerEnvironmentInCache(
                projectId, ENV_NAME, SYSTEM_NAME, CONNECTION_NAME, CONNECTION_TYPE, buildParams("user1"));

        UUID envId = lazyEnv.getId();
        Map<String, String> updatedParams = buildParams("user2_updated");
        environmentsService.updateConnectionInCache(envId, SYSTEM_NAME, CONNECTION_NAME, CONNECTION_TYPE,
                updatedParams);

        org.qubership.atp.tdm.env.configurator.model.System fullSystem =
                environmentsService.getFullSystemByName(envId, SYSTEM_NAME);
        assertNotNull(fullSystem);
        assertEquals("user2_updated",
                fullSystem.getConnections().get(0).getParameters().get("db_login"));
    }

    // ── addSystemToEnvironment ────────────────────────────────────────────────

    @Test
    void addSystemToEnvironment_connectionIdIsSet_getSystemsDoesNotThrow() {
        String secondSystem = "secondSystem";

        LazyEnvironment lazyEnv = environmentsService.registerEnvironmentInCache(
                projectId, ENV_NAME, SYSTEM_NAME, CONNECTION_NAME, CONNECTION_TYPE, buildParams("user1"));

        UUID envId = lazyEnv.getId();
        environmentsService.addSystemToEnvironment(projectId, envId, secondSystem,
                CONNECTION_NAME, CONNECTION_TYPE, buildParams("user3"));

        List<LazySystem> systems = assertDoesNotThrow(
                () -> environmentsService.getLazySystems(envId));
        assertEquals(2, systems.size());
        systems.forEach(s -> {
            assertFalse(s.getConnections().isEmpty());
            assertNotNull(s.getConnections().get(0));
        });
    }
}

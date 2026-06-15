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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.HashMap;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.qubership.atp.tdm.env.configurator.exceptions.internal.TdmEnvConvertFullSystemByNameException;
import org.qubership.atp.tdm.env.configurator.exceptions.internal.TdmEnvDbConnectionException;
import org.qubership.atp.tdm.env.configurator.model.LazySystem;
import org.qubership.atp.tdm.env.configurator.model.System;
import org.qubership.atp.tdm.env.configurator.model.envgen.ConnectionType;
import org.qubership.atp.tdm.env.configurator.model.envgen.YamlConnection;
import org.qubership.atp.tdm.env.configurator.model.envgen.YamlEnvironment;
import org.qubership.atp.tdm.env.configurator.model.envgen.YamlSystem;
import org.qubership.atp.tdm.env.configurator.service.CacheService;
import org.springframework.cache.CacheManager;

@ExtendWith(MockitoExtension.class)
class EnvironmentsServiceImplSystemLookupTest {

    private static final String ENV_NAME = "env1";
    private static final String SYSTEM_NAME_LOWER = "db";

    @Mock
    private CacheService cacheService;

    @Mock
    private CacheManager cacheManager;

    @InjectMocks
    private EnvironmentsServiceImpl environmentsService;

    private UUID projectId;
    private UUID environmentId;
    private YamlEnvironment yamlEnvironment;
    private YamlSystem yamlSystem;
    private YamlConnection dbConnection;

    @BeforeEach
    void setUp() {
        projectId = UUID.randomUUID();

        dbConnection = new YamlConnection();
        dbConnection.setName("DB");
        dbConnection.setType(ConnectionType.DB);
        dbConnection.setParameters(new HashMap<>());

        yamlSystem = new YamlSystem();
        yamlSystem.setName(SYSTEM_NAME_LOWER);
        yamlSystem.setConnections(Collections.singletonList(dbConnection));

        yamlEnvironment = new YamlEnvironment(ENV_NAME);
        yamlEnvironment.setYamlSystems(Collections.singletonList(yamlSystem));

        environmentId = yamlEnvironment.getId();
    }

    // ── getLazySystemByName ───────────────────────────────────────────────────

    @Test
    void getLazySystemByName_exactMatch_returnsLazySystem() {
        when(cacheService.get(environmentId)).thenReturn(yamlEnvironment);

        LazySystem result = environmentsService.getLazySystemByName(projectId, environmentId, "db");

        assertEquals(yamlSystem.getName(), result.getName());
        assertEquals(yamlSystem.getId(), result.getId());
    }

    @Test
    void getLazySystemByName_upperCaseSystemName_throws() {
        when(cacheService.get(environmentId)).thenReturn(yamlEnvironment);

        assertThrows(TdmEnvConvertFullSystemByNameException.class,
                () -> environmentsService.getLazySystemByName(projectId, environmentId, "DB"));
    }

    @Test
    void getLazySystemByName_unknownSystemName_throwsException() {
        when(cacheService.get(environmentId)).thenReturn(yamlEnvironment);

        assertThrows(TdmEnvConvertFullSystemByNameException.class,
                () -> environmentsService.getLazySystemByName(projectId, environmentId, "unknown"));
    }

    // ── getFullSystemByName ───────────────────────────────────────────────────

    @Test
    void getFullSystemByName_exactMatch_returnsSystem() {
        when(cacheService.get(environmentId)).thenReturn(yamlEnvironment);

        System result = environmentsService.getFullSystemByName(environmentId, "db");

        assertEquals(SYSTEM_NAME_LOWER, result.getName());
        assertEquals(environmentId, result.getEnvironmentId());
    }

    @Test
    void getFullSystemByName_upperCaseSystemName_throwError() {
        when(cacheService.get(environmentId)).thenReturn(yamlEnvironment);

        assertThrows(TdmEnvConvertFullSystemByNameException.class,
                () -> environmentsService.getFullSystemByName(environmentId, "DB"));
    }

    @Test
    void getFullSystemByName_unknownSystemName_throwsException() {
        when(cacheService.get(environmentId)).thenReturn(yamlEnvironment);

        assertThrows(TdmEnvConvertFullSystemByNameException.class,
                () -> environmentsService.getFullSystemByName(environmentId, "unknown"));
    }

    @Test
    void getFullSystemByName_systemWithoutDbConnection_throwsDbConnectionException() {
        YamlConnection httpConnection = new YamlConnection();
        httpConnection.setName("HTTP");
        httpConnection.setType(ConnectionType.HTTP);
        httpConnection.setParameters(new HashMap<>());

        YamlSystem systemNoDb = new YamlSystem();
        systemNoDb.setName("httpSystem");
        systemNoDb.setConnections(Collections.singletonList(httpConnection));

        yamlEnvironment.setYamlSystems(Collections.singletonList(systemNoDb));

        when(cacheService.get(environmentId)).thenReturn(yamlEnvironment);

        assertThrows(TdmEnvDbConnectionException.class,
                () -> environmentsService.getFullSystemByName(environmentId, "httpSystem"));
    }
}

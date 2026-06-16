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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.qubership.atp.tdm.AbstractTest;
import org.qubership.atp.tdm.env.configurator.exceptions.internal.TdmEnvConvertFullSystemByNameException;
import org.qubership.atp.tdm.env.configurator.exceptions.internal.TdmEnvDbConnectionException;
import org.qubership.atp.tdm.env.configurator.model.LazySystem;
import org.qubership.atp.tdm.env.configurator.model.System;
import org.qubership.atp.tdm.env.configurator.service.EnvironmentsService;
import org.qubership.atp.tdm.model.DynamicEnvironment;
import org.qubership.atp.tdm.model.DynamicSystem;
import org.qubership.atp.tdm.repo.DynamicEnvironmentRepository;
import org.qubership.atp.tdm.repo.DynamicSystemRepository;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Verifies system lookup via the JPA-backed {@link EnvironmentsServiceImpl}.
 * Replaces the old mock-CacheService variant.
 */
class EnvironmentsServiceImplSystemLookupTest extends AbstractTest {

    private static final String ENV_NAME = "env1";
    private static final String SYSTEM_NAME_LOWER = "db";

    @Autowired
    private EnvironmentsService environmentsService;

    @Autowired
    private DynamicEnvironmentRepository dynamicEnvironmentRepository;

    @Autowired
    private DynamicSystemRepository dynamicSystemRepository;

    private UUID projectId;
    private UUID environmentId;

    @BeforeEach
    void setUp() {
        dynamicSystemRepository.deleteAll();
        dynamicEnvironmentRepository.deleteAll();
        projectId = UUID.randomUUID();

        DynamicEnvironment env = dynamicEnvironmentRepository.save(
                new DynamicEnvironment(projectId, ENV_NAME));
        environmentId = env.getId();
        dynamicSystemRepository.save(
                new DynamicSystem(env, SYSTEM_NAME_LOWER, "DB", "DB", "{\"host\":\"localhost\"}"));
    }

    // ── getLazySystemByName ───────────────────────────────────────────────────

    @Test
    void getLazySystemByName_exactMatch_returnsLazySystem() {
        LazySystem result = environmentsService.getLazySystemByName(projectId, environmentId, SYSTEM_NAME_LOWER);

        assertEquals(SYSTEM_NAME_LOWER, result.getName());
    }

    @Test
    void getLazySystemByName_upperCaseSystemName_throws() {
        assertThrows(TdmEnvConvertFullSystemByNameException.class,
                () -> environmentsService.getLazySystemByName(projectId, environmentId, "DB"));
    }

    @Test
    void getLazySystemByName_unknownSystemName_throwsException() {
        assertThrows(TdmEnvConvertFullSystemByNameException.class,
                () -> environmentsService.getLazySystemByName(projectId, environmentId, "unknown"));
    }

    // ── getFullSystemByName ───────────────────────────────────────────────────

    @Test
    void getFullSystemByName_exactMatch_returnsSystem() {
        System result = environmentsService.getFullSystemByName(environmentId, SYSTEM_NAME_LOWER);

        assertEquals(SYSTEM_NAME_LOWER, result.getName());
        assertEquals(environmentId, result.getEnvironmentId());
    }

    @Test
    void getFullSystemByName_upperCaseSystemName_throwError() {
        assertThrows(TdmEnvConvertFullSystemByNameException.class,
                () -> environmentsService.getFullSystemByName(environmentId, "DB"));
    }

    @Test
    void getFullSystemByName_unknownSystemName_throwsException() {
        assertThrows(TdmEnvConvertFullSystemByNameException.class,
                () -> environmentsService.getFullSystemByName(environmentId, "unknown"));
    }

    @Test
    void getFullSystemByName_systemWithoutDbConnection_throwsDbConnectionException() {
        DynamicEnvironment httpEnv = dynamicEnvironmentRepository.save(
                new DynamicEnvironment(projectId, "httpEnv"));
        dynamicSystemRepository.save(
                new DynamicSystem(httpEnv, "httpSystem", "HTTP", "HTTP", "{}"));

        assertThrows(TdmEnvDbConnectionException.class,
                () -> environmentsService.getFullSystemByName(httpEnv.getId(), "httpSystem"));
    }
}

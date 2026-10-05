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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import org.qubership.atp.tdm.env.configurator.service.EnvironmentsService;
import org.qubership.atp.tdm.exceptions.internal.EnvironmentNotFoundException;
import org.qubership.atp.tdm.model.DynamicEnvironment;
import org.qubership.atp.tdm.model.DynamicSystem;
import org.qubership.atp.tdm.model.rest.EnvironmentConnectionsResponse;
import org.qubership.atp.tdm.model.rest.ResponseMessage;
import org.qubership.atp.tdm.model.rest.ResponseType;
import org.qubership.atp.tdm.model.rest.requests.EnvironmentConnectionRequest;
import org.qubership.atp.tdm.repo.DynamicEnvironmentRepository;
import org.qubership.atp.tdm.repo.DynamicSystemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Spring-context variant of {@link DynamicEnvironmentServiceImplTest}.
 * Uses real H2 persistence and {@link EnvironmentsService} instead of mocks.
 */
class DynamicEnvironmentServiceImplSpringTest extends AbstractTest {

    private static final String PROJECT_NAME = "Test Project";
    private static final UUID PROJECT_ID = UUID.fromString("c9858f9f-f87b-3eae-aa86-bfa92856d55c");
    private static final String SYSTEM_NAME = "Test System";

    @Autowired
    private DynamicEnvironmentServiceImpl dynamicEnvironmentService;

    @Autowired
    private DynamicEnvironmentRepository dynamicEnvironmentRepository;

    @Autowired
    private DynamicSystemRepository dynamicSystemRepository;

    @Autowired
    private EnvironmentsService environmentsService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private EnvironmentConnectionRequest connection;

    @BeforeEach
    void setUp() {
        dynamicSystemRepository.deleteAll();
        dynamicEnvironmentRepository.deleteAll();
        connection = buildConnection();
    }

    @Test
    void createEnvironment_projectNotFound_returnsError() {
        String envName = uniqueName("dyn-env");

        assertThrows(IllegalArgumentException.class, () -> dynamicEnvironmentService.createEnvironment(
                "Unknown Project", envName, SYSTEM_NAME, connection));

        assertEquals(0, dynamicEnvironmentRepository.count());
        assertThrows(TdmEnvConvertLazyEnvironmentByNameException.class,
                () -> environmentsService.getLazyEnvironmentByName(PROJECT_ID, envName));
    }

    @Test
    void createEnvironment_envAndSystemFound_returnsDuplicateError() {
        String envName = uniqueName("dyn-env");

        dynamicEnvironmentService.createEnvironment(PROJECT_NAME, envName, SYSTEM_NAME, connection);

        assertThrows(IllegalArgumentException.class, () -> dynamicEnvironmentService.createEnvironment(
                PROJECT_NAME, envName, SYSTEM_NAME, connection));

        assertEquals(1, countDbSystemRows(envName));
    }

    @Test
    void createEnvironment_envFoundSystemNotFound_addsSystemAndSaves() {
        String envName = uniqueName("dyn-env");
        String secondSystem = "Second System";

        dynamicEnvironmentService.createEnvironment(PROJECT_NAME, envName, SYSTEM_NAME, connection);
        ResponseMessage response = dynamicEnvironmentService.createEnvironment(
                PROJECT_NAME, envName, secondSystem, connection);

        assertEquals(ResponseType.SUCCESS, response.getType());
        assertEquals(2, countDbSystemRows(envName));

        LazyEnvironment lazyEnvironment = environmentsService.getLazyEnvironmentByName(PROJECT_ID, envName);
        List<LazySystem> systems = environmentsService.getLazySystems(lazyEnvironment.getId());
        assertEquals(2, systems.size());
        assertDoesNotThrowSystemLookup(lazyEnvironment.getId(), SYSTEM_NAME);
        assertDoesNotThrowSystemLookup(lazyEnvironment.getId(), secondSystem);
    }

    @Test
    void createEnvironment_envNotFound_registersEnvironmentAndSaves() {
        String envName = uniqueName("dyn-env");

        ResponseMessage response = dynamicEnvironmentService.createEnvironment(
                PROJECT_NAME, envName, SYSTEM_NAME, connection);

        assertEquals(ResponseType.SUCCESS, response.getType());
        assertEquals(1, countDbSystemRows(envName));

        LazyEnvironment lazyEnvironment = environmentsService.getLazyEnvironmentByName(PROJECT_ID, envName);
        assertEquals(envName, lazyEnvironment.getName());
        assertEquals(1, environmentsService.getLazySystems(lazyEnvironment.getId()).size());
    }

    @Test
    void deleteEnvironment_envFound_deletesAllRows() {
        String envName = uniqueName("dyn-env");

        dynamicEnvironmentService.createEnvironment(PROJECT_NAME, envName, SYSTEM_NAME, connection);

        ResponseMessage response = dynamicEnvironmentService.deleteEnvironment(PROJECT_NAME, envName, null);

        assertEquals(ResponseType.SUCCESS, response.getType());
        assertEquals(0, countDbSystemRows(envName));
        assertThrows(TdmEnvConvertLazyEnvironmentByNameException.class,
                () -> environmentsService.getLazyEnvironmentByName(PROJECT_ID, envName));
    }

    @Test
    void deleteEnvironment_thenRecreate_registersFreshEnvironment() {
        String envName = uniqueName("dyn-env");

        dynamicEnvironmentService.createEnvironment(PROJECT_NAME, envName, SYSTEM_NAME, connection);
        dynamicEnvironmentService.deleteEnvironment(PROJECT_NAME, envName, null);

        ResponseMessage response = dynamicEnvironmentService.createEnvironment(
                PROJECT_NAME, envName, SYSTEM_NAME, connection);

        assertEquals(ResponseType.SUCCESS, response.getType());
        assertEquals(1, countDbSystemRows(envName));
        LazyEnvironment lazyEnvironment = environmentsService.getLazyEnvironmentByName(PROJECT_ID, envName);
        assertEquals(1, environmentsService.getLazySystems(lazyEnvironment.getId()).size());
    }

    @Test
    void deleteEnvironment_envNotFoundInDb_throwsNotFound() {
        String envName = uniqueName("dyn-env");

        assertThrows(EnvironmentNotFoundException.class,
                () -> dynamicEnvironmentService.deleteEnvironment(PROJECT_NAME, envName, SYSTEM_NAME));

        assertEquals(0, countDbSystemRows(envName));
        assertThrows(TdmEnvConvertLazyEnvironmentByNameException.class,
                () -> environmentsService.getLazyEnvironmentByName(PROJECT_ID, envName));
    }

    @Test
    void updateEnvironment_projectNotFound_throwsIllegalArgument() {
        String envName = uniqueName("dyn-env");

        assertThrows(IllegalArgumentException.class, () ->
                dynamicEnvironmentService.updateEnvironment(
                        "Unknown Project", envName, SYSTEM_NAME, connection, null, null));
    }

    @Test
    void updateEnvironment_envNotFound_throwsException() {
        String envName = uniqueName("dyn-env");

        assertThrows(EnvironmentNotFoundException.class, () ->
                dynamicEnvironmentService.updateEnvironment(
                        PROJECT_NAME, envName, SYSTEM_NAME, connection, null, null));
    }


    @Test
    void updateEnvironment_envAndSystemExist_updatesConnection() {
        String envName = uniqueName("dyn-env");

        dynamicEnvironmentService.createEnvironment(PROJECT_NAME, envName, SYSTEM_NAME, connection);

        ResponseMessage response = dynamicEnvironmentService.updateEnvironment(
                PROJECT_NAME, envName, SYSTEM_NAME, connection, null, null);

        assertEquals(ResponseType.SUCCESS, response.getType());

        DynamicEnvironment envRecord = dynamicEnvironmentRepository
                .findByEnvNameAndProjectId(envName, PROJECT_ID).orElse(null);
        assertNotNull(envRecord);
        DynamicSystem saved = dynamicSystemRepository
                .findByEnvIdAndSystemName(envRecord.getId(), SYSTEM_NAME)
                .orElse(null);
        assertNotNull(saved);
        assertTrue(saved.getConnections().get(0).getConnectionParameters().contains("localhost"));

        LazyEnvironment lazyEnvironment = environmentsService.getLazyEnvironmentByName(PROJECT_ID, envName);
        System system = environmentsService.getFullSystemByName(lazyEnvironment.getId(), SYSTEM_NAME);
        assertEquals("localhost", system.getConnections().get(0).getParameters().get("host"));
    }

    @Test
    void updateEnvironment_withNewNames_renamesAndUpdatesConnection() {
        String envName = uniqueName("dyn-env");
        String newEnvName = uniqueName("renamed-env");
        String newSystemName = "Renamed System";

        dynamicEnvironmentService.createEnvironment(PROJECT_NAME, envName, SYSTEM_NAME, connection);

        ResponseMessage response = dynamicEnvironmentService.updateEnvironment(
                PROJECT_NAME, envName, SYSTEM_NAME, connection, newEnvName, newSystemName);

        assertEquals(ResponseType.SUCCESS, response.getType());
        assertEquals(0, countDbSystemRows(envName));

        DynamicEnvironment envRecord = dynamicEnvironmentRepository
                .findByEnvNameAndProjectId(newEnvName, PROJECT_ID).orElse(null);
        assertNotNull(envRecord);
        assertEquals(newEnvName, envRecord.getEnvName());

        DynamicSystem saved = dynamicSystemRepository
                .findByEnvIdAndSystemName(envRecord.getId(), newSystemName)
                .orElse(null);
        assertNotNull(saved);
        assertEquals(newSystemName, saved.getSystemName());

        LazyEnvironment lazyEnvironment = environmentsService.getLazyEnvironmentByName(PROJECT_ID, newEnvName);
        assertDoesNotThrowSystemLookup(lazyEnvironment.getId(), newSystemName);
    }

    @Test
    void updateEnvironment_newEnvNameAlreadyExists_throwsDuplicate() {
        String envName = uniqueName("dyn-env");
        String existingEnvName = uniqueName("existing-env");

        dynamicEnvironmentService.createEnvironment(PROJECT_NAME, envName, SYSTEM_NAME, connection);
        dynamicEnvironmentService.createEnvironment(PROJECT_NAME, existingEnvName, SYSTEM_NAME, connection);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                dynamicEnvironmentService.updateEnvironment(
                        PROJECT_NAME, envName, SYSTEM_NAME, connection, existingEnvName, null));
        assertTrue(ex.getMessage().contains(existingEnvName));
    }

    @Test
    void updateEnvironment_newSystemNameAlreadyExists_throwsDuplicate() {
        String envName = uniqueName("dyn-env");
        String secondSystem = "Second System";
        String existingSystemName = secondSystem;

        dynamicEnvironmentService.createEnvironment(PROJECT_NAME, envName, SYSTEM_NAME, connection);
        dynamicEnvironmentService.createEnvironment(PROJECT_NAME, envName, secondSystem, connection);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                dynamicEnvironmentService.updateEnvironment(
                        PROJECT_NAME, envName, SYSTEM_NAME, connection, null, existingSystemName));
        assertTrue(ex.getMessage().contains(existingSystemName));
    }

    @Test
    void createEnvironment_twoConnections_persistedAndReloadedUnmasked() {
        String envName = uniqueName("dyn-env");
        EnvironmentConnectionRequest http = httpConnection("secret-token");

        dynamicEnvironmentService.createEnvironment(
                PROJECT_NAME, envName, SYSTEM_NAME, List.of(connection, http));

        DynamicSystem saved = findSystem(envName, SYSTEM_NAME);
        assertEquals(2, saved.getConnections().size());
        assertEquals("HTTP", saved.getConnections().stream()
                .filter(item -> "HTTP".equals(item.getConnectionType()))
                .findFirst().orElseThrow().getConnectionType());

        System system = environmentsService.getFullSystemByName(environmentId(envName), SYSTEM_NAME);
        assertEquals("secret-token", system.getConnections().stream()
                .filter(item -> "HTTP".equals(item.getConnectionType()))
                .findFirst().orElseThrow().getParameters().get("token"));
        assertEquals("localhost", system.getConnections().stream()
                .filter(item -> "DB".equals(item.getConnectionType()))
                .findFirst().orElseThrow().getParameters().get("host"));
    }

    @Test
    void uniqueConnectionType_isEnforced() {
        DynamicEnvironment env = dynamicEnvironmentRepository.save(
                new DynamicEnvironment(PROJECT_ID, uniqueName("dyn-env")));
        DynamicSystem system = new DynamicSystem(env, SYSTEM_NAME, "DB", "{\"host\":\"localhost\"}");
        system.addConnection("DB", "{\"url\":\"https://example.com\"}");

        assertThrows(DataIntegrityViolationException.class, () -> dynamicSystemRepository.saveAndFlush(system));
    }

    @Test
    void deleteSystem_removesItsConnections() {
        String envName = uniqueName("dyn-env");
        dynamicEnvironmentService.createEnvironment(
                PROJECT_NAME, envName, SYSTEM_NAME, List.of(connection, httpConnection("secret-token")));

        DynamicSystem saved = findSystem(envName, SYSTEM_NAME);
        assertEquals(2, countConnections(saved.getId()));

        dynamicEnvironmentService.deleteEnvironment(PROJECT_NAME, envName, SYSTEM_NAME);

        assertEquals(0, countConnections(saved.getId()));
    }

    @Test
    void updateEnvironment_addsConnectionAndUpdatesExisting() {
        String envName = uniqueName("dyn-env");
        dynamicEnvironmentService.createEnvironment(PROJECT_NAME, envName, SYSTEM_NAME, connection);

        dynamicEnvironmentService.updateEnvironment(
                PROJECT_NAME, envName, SYSTEM_NAME, httpConnection("secret-token"), null, null);

        DynamicSystem saved = findSystem(envName, SYSTEM_NAME);
        assertEquals(2, saved.getConnections().size());
        assertTrue(saved.getConnections().stream()
                .filter(item -> "DB".equals(item.getConnectionType()))
                .findFirst().orElseThrow().getConnectionParameters().contains("localhost"));

        EnvironmentConnectionRequest updatedDb = buildConnection();
        updatedDb.getParameters().put("host", "db-new.example.com");
        updatedDb.getParameters().put("db_password", "new-secret");
        dynamicEnvironmentService.updateEnvironment(
                PROJECT_NAME, envName, SYSTEM_NAME, updatedDb, null, null);

        DynamicSystem afterUpdate = findSystem(envName, SYSTEM_NAME);
        assertEquals(2, afterUpdate.getConnections().size());
        assertTrue(afterUpdate.getConnections().stream()
                .filter(item -> "DB".equals(item.getConnectionType()))
                .findFirst().orElseThrow().getConnectionParameters().contains("db-new.example.com"));
        assertTrue(afterUpdate.getConnections().stream()
                .anyMatch(item -> "HTTP".equals(item.getConnectionType())));

        EnvironmentConnectionsResponse masked = dynamicEnvironmentService.getConnections(
                PROJECT_NAME, envName, SYSTEM_NAME);
        Map<String, String> dbParameters = masked.getSystems().get(0).getConnections().stream()
                .filter(item -> "DB".equals(item.getType()))
                .findFirst().orElseThrow().getParameters();
        assertEquals("***", dbParameters.get("db_password"));
        assertEquals("db-new.example.com", dbParameters.get("host"));

        System internal = environmentsService.getFullSystemByName(environmentId(envName), SYSTEM_NAME);
        assertEquals("new-secret", internal.getConnections().stream()
                .filter(item -> "DB".equals(item.getName()))
                .findFirst().orElseThrow().getParameters().get("db_password"));
    }

    @Test
    void updateEnvironment_invalidType_doesNotChangeStoredConnection() {
        String envName = uniqueName("dyn-env");
        dynamicEnvironmentService.createEnvironment(PROJECT_NAME, envName, SYSTEM_NAME, connection);

        EnvironmentConnectionRequest git = buildConnection();
        git.setType("GIT");
        assertThrows(IllegalArgumentException.class, () -> dynamicEnvironmentService.updateEnvironment(
                PROJECT_NAME, envName, SYSTEM_NAME, git, null, null));

        DynamicSystem saved = findSystem(envName, SYSTEM_NAME);
        assertEquals(1, saved.getConnections().size());
        assertEquals("DB", saved.getConnections().get(0).getConnectionType());
        assertTrue(saved.getConnections().get(0).getConnectionParameters().contains("localhost"));
    }

    private EnvironmentConnectionRequest buildConnection() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("host", "localhost");
        parameters.put("port", "5432");

        EnvironmentConnectionRequest request = new EnvironmentConnectionRequest();
        request.setName("DB");
        request.setType("DB");
        request.setParameters(parameters);
        return request;
    }

    private String uniqueName(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    private long countDbSystemRows(String envName) {
        return dynamicEnvironmentRepository.findByEnvNameAndProjectId(envName, PROJECT_ID)
                .map(env -> (long) dynamicSystemRepository.findAllByEnvId(env.getId()).size())
                .orElse(0L);
    }

    private void assertDoesNotThrowSystemLookup(UUID envId, String systemName) {
        assertDoesNotThrow(() -> environmentsService.getLazySystemByName(PROJECT_ID, envId, systemName));
    }

    private EnvironmentConnectionRequest httpConnection(String token) {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("url", "https://api.example.com");
        parameters.put("token", token);

        EnvironmentConnectionRequest request = new EnvironmentConnectionRequest();
        request.setName("HTTP");
        request.setType("http");
        request.setParameters(parameters);
        return request;
    }

    private DynamicSystem findSystem(String envName, String systemName) {
        return dynamicSystemRepository.findByEnvIdAndSystemName(environmentId(envName), systemName).orElseThrow();
    }

    private UUID environmentId(String envName) {
        return dynamicEnvironmentRepository.findByEnvNameAndProjectId(envName, PROJECT_ID).orElseThrow().getId();
    }

    private int countConnections(UUID systemId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dynamic_connection WHERE system_id = ?", Integer.class, systemId);
        return count == null ? 0 : count;
    }
}

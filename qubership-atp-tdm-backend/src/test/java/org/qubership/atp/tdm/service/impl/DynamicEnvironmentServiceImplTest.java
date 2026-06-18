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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.qubership.atp.tdm.env.configurator.model.LazyEnvironment;
import org.qubership.atp.tdm.env.configurator.model.LazyProject;
import org.qubership.atp.tdm.env.configurator.model.LazySystem;
import org.qubership.atp.tdm.env.configurator.service.EnvironmentsService;
import org.qubership.atp.tdm.exceptions.internal.EnvironmentNotFoundException;
import org.qubership.atp.tdm.model.DynamicEnvironment;
import org.qubership.atp.tdm.model.DynamicSystem;
import org.qubership.atp.tdm.model.rest.ResponseMessage;
import org.qubership.atp.tdm.model.rest.ResponseType;
import org.qubership.atp.tdm.model.rest.requests.EnvironmentConnectionRequest;
import org.qubership.atp.tdm.repo.CatalogRepository;
import org.qubership.atp.tdm.repo.DynamicEnvironmentRepository;
import org.qubership.atp.tdm.repo.DynamicSystemRepository;

@ExtendWith(MockitoExtension.class)
class DynamicEnvironmentServiceImplTest {

    private static final String PROJECT_NAME = "Test Project";
    private static final String ENV_NAME = "Test Environment";
    private static final String SYSTEM_NAME = "Test System";

    @Mock
    private EnvironmentsService environmentsService;

    @Mock
    private DynamicEnvironmentRepository dynamicEnvironmentRepository;

    @Mock
    private DynamicSystemRepository dynamicSystemRepository;

    @Mock
    private CatalogRepository catalogRepository;

    @InjectMocks
    private DynamicEnvironmentServiceImpl dynamicEnvironmentService;

    private LazyProject lazyProject;
    private LazyEnvironment lazyEnvironment;
    private LazySystem lazySystem;
    private EnvironmentConnectionRequest connection;
    private DynamicEnvironment envRecord;

    @BeforeEach
    void setUp() {
        UUID projectId = UUID.randomUUID();
        UUID environmentId = UUID.nameUUIDFromBytes(ENV_NAME.getBytes());
        UUID systemId = UUID.randomUUID();

        lazyProject = new LazyProject();
        lazyProject.setId(projectId);
        lazyProject.setName(PROJECT_NAME);

        lazySystem = new LazySystem();
        lazySystem.setId(systemId);
        lazySystem.setName(SYSTEM_NAME);

        lazyEnvironment = new LazyEnvironment();
        lazyEnvironment.setId(environmentId);
        lazyEnvironment.setName(ENV_NAME);
        lazyEnvironment.setProjectId(projectId);

        envRecord = new DynamicEnvironment(projectId, ENV_NAME);

        Map<String, String> parameters = new HashMap<>();
        parameters.put("host", "localhost");
        parameters.put("port", "5432");

        connection = new EnvironmentConnectionRequest();
        connection.setName("DB");
        connection.setType("DB");
        connection.setParameters(parameters);
    }

    @Test
    void createEnvironment_projectNotFound_returnsError() {
        when(environmentsService.getLazyProjectByName(PROJECT_NAME))
                .thenThrow(new IllegalArgumentException("Project [" + PROJECT_NAME + "] not found."));

        assertThrows(IllegalArgumentException.class, () -> dynamicEnvironmentService.createEnvironment(
                PROJECT_NAME, ENV_NAME, SYSTEM_NAME, connection));

        verify(dynamicEnvironmentRepository, never()).save(any());
    }

    @Test
    void createEnvironment_envAndSystemFound_returnsDuplicateError() {
        when(environmentsService.getLazyProjectByName(PROJECT_NAME)).thenReturn(lazyProject);
        when(dynamicEnvironmentRepository.findByEnvNameAndProjectId(ENV_NAME, lazyProject.getId()))
                .thenReturn(Optional.of(envRecord));
        when(dynamicSystemRepository.existsByEnvIdAndSystemName(envRecord.getId(), SYSTEM_NAME))
                .thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> dynamicEnvironmentService.createEnvironment(
                PROJECT_NAME, ENV_NAME, SYSTEM_NAME, connection));

        verify(dynamicSystemRepository, never()).save(any());
    }

    @Test
    void createEnvironment_envFoundSystemNotFound_addsSystemAndSaves() {
        when(environmentsService.getLazyProjectByName(PROJECT_NAME)).thenReturn(lazyProject);
        when(dynamicEnvironmentRepository.findByEnvNameAndProjectId(ENV_NAME, lazyProject.getId()))
                .thenReturn(Optional.of(envRecord));
        when(dynamicSystemRepository.existsByEnvIdAndSystemName(envRecord.getId(), SYSTEM_NAME))
                .thenReturn(false);

        ResponseMessage response = dynamicEnvironmentService.createEnvironment(
                PROJECT_NAME, ENV_NAME, SYSTEM_NAME, connection);

        assertEquals(ResponseType.SUCCESS, response.getType());
        verify(dynamicSystemRepository).save(any(DynamicSystem.class));
        verify(dynamicEnvironmentRepository, never()).save(any());
    }

    @Test
    void createEnvironment_envNotFound_registersEnvironmentAndSaves() {
        when(environmentsService.getLazyProjectByName(PROJECT_NAME)).thenReturn(lazyProject);
        when(dynamicEnvironmentRepository.findByEnvNameAndProjectId(ENV_NAME, lazyProject.getId()))
                .thenReturn(Optional.empty());
        when(dynamicEnvironmentRepository.save(any())).thenReturn(envRecord);

        ResponseMessage response = dynamicEnvironmentService.createEnvironment(
                PROJECT_NAME, ENV_NAME, SYSTEM_NAME, connection);

        assertEquals(ResponseType.SUCCESS, response.getType());
        verify(dynamicEnvironmentRepository).save(any(DynamicEnvironment.class));
        verify(dynamicSystemRepository).save(any(DynamicSystem.class));
    }

    @Test
    void deleteEnvironment_envFound_deletesAllRows() {
        when(environmentsService.getLazyProjectByName(PROJECT_NAME)).thenReturn(lazyProject);
        when(dynamicEnvironmentRepository.findByEnvNameAndProjectId(ENV_NAME, lazyProject.getId()))
                .thenReturn(Optional.of(envRecord));

        ResponseMessage response = dynamicEnvironmentService.deleteEnvironment(PROJECT_NAME, ENV_NAME, null);

        assertEquals(ResponseType.SUCCESS, response.getType());
        verify(dynamicEnvironmentRepository).deleteByEnvNameAndProjectId(ENV_NAME, lazyProject.getId());
    }

    @Test
    void deleteEnvironment_envNotFoundInDb_throwsNotFound() {
        when(environmentsService.getLazyProjectByName(PROJECT_NAME)).thenReturn(lazyProject);
        when(dynamicEnvironmentRepository.findByEnvNameAndProjectId(ENV_NAME, lazyProject.getId()))
                .thenReturn(Optional.empty());

        assertThrows(EnvironmentNotFoundException.class,
                () -> dynamicEnvironmentService.deleteEnvironment(PROJECT_NAME, ENV_NAME, null));

        verify(dynamicEnvironmentRepository, never()).deleteByEnvNameAndProjectId(anyString(), any());
    }

    @Test
    void deleteEnvironment_envNotFoundInDb_withSystemName_throwsNotFound() {
        when(environmentsService.getLazyProjectByName(PROJECT_NAME)).thenReturn(lazyProject);
        when(dynamicEnvironmentRepository.findByEnvNameAndProjectId(ENV_NAME, lazyProject.getId()))
                .thenReturn(Optional.empty());

        assertThrows(EnvironmentNotFoundException.class,
                () -> dynamicEnvironmentService.deleteEnvironment(PROJECT_NAME, ENV_NAME, SYSTEM_NAME));

        verify(dynamicEnvironmentRepository, never()).deleteByEnvNameAndProjectId(anyString(), any());
    }

    @Test
    void updateEnvironment_projectNotFound_throwsIllegalArgument() {
        when(environmentsService.getLazyProjectByName(PROJECT_NAME))
                .thenThrow(new IllegalArgumentException("Project [" + PROJECT_NAME + "] not found."));

        assertThrows(IllegalArgumentException.class, () ->
                dynamicEnvironmentService.updateEnvironment(
                        PROJECT_NAME, ENV_NAME, SYSTEM_NAME, connection, null, null));
    }

    @Test
    void updateEnvironment_envNotFound_throwsException() {
        when(environmentsService.getLazyProjectByName(PROJECT_NAME)).thenReturn(lazyProject);

        assertThrows(EnvironmentNotFoundException.class, () ->
                dynamicEnvironmentService.updateEnvironment(
                        PROJECT_NAME, ENV_NAME, SYSTEM_NAME, connection, null, null));
    }

    @Test
    void updateEnvironment_envAndSystemExist_updatesConnection() {
        DynamicSystem sys = new DynamicSystem(envRecord, SYSTEM_NAME, "DB", "DB", "{}");

        when(environmentsService.getLazyProjectByName(PROJECT_NAME)).thenReturn(lazyProject);
        when(dynamicEnvironmentRepository.findByEnvNameAndProjectId(ENV_NAME, lazyProject.getId()))
                .thenReturn(Optional.of(envRecord));
        when(dynamicSystemRepository.findByEnvIdAndSystemName(envRecord.getId(), SYSTEM_NAME))
                .thenReturn(Optional.of(sys));

        ResponseMessage response = dynamicEnvironmentService.updateEnvironment(
                PROJECT_NAME, ENV_NAME, SYSTEM_NAME, connection, null, null);

        assertEquals(ResponseType.SUCCESS, response.getType());
    }

    @Test
    void updateEnvironment_withNewNames_renamesAndUpdatesConnection() {
        String newEnvName = "Renamed Environment";
        String newSystemName = "Renamed System";
        envRecord.setId(UUID.randomUUID());
        DynamicSystem sys = new DynamicSystem(envRecord, SYSTEM_NAME, "DB", "DB", "{}");

        when(environmentsService.getLazyProjectByName(PROJECT_NAME)).thenReturn(lazyProject);
        when(dynamicEnvironmentRepository.existsByEnvNameAndProjectId(newEnvName, lazyProject.getId()))
                .thenReturn(false);
        when(dynamicSystemRepository.existsByEnvIdAndSystemName(envRecord.getId(), newSystemName))
                .thenReturn(false);
        when(dynamicEnvironmentRepository.findByEnvNameAndProjectId(ENV_NAME, lazyProject.getId()))
                .thenReturn(Optional.of(envRecord));
        when(dynamicSystemRepository.findByEnvIdAndSystemName(envRecord.getId(), SYSTEM_NAME))
                .thenReturn(Optional.of(sys));

        ResponseMessage response = dynamicEnvironmentService.updateEnvironment(
                PROJECT_NAME, ENV_NAME, SYSTEM_NAME, connection, newEnvName, newSystemName);

        assertEquals(ResponseType.SUCCESS, response.getType());
    }

    @Test
    void updateEnvironment_newEnvNameAlreadyExists_throwsDuplicate() {
        String newEnvName = "Existing Environment";

        when(environmentsService.getLazyProjectByName(PROJECT_NAME)).thenReturn(lazyProject);

        Exception ex = assertThrows(EnvironmentNotFoundException.class, () ->
                dynamicEnvironmentService.updateEnvironment(
                        PROJECT_NAME, ENV_NAME, SYSTEM_NAME, connection, newEnvName, null));
    }

    @Test
    void updateEnvironment_newSystemNameAlreadyExists_throwsDuplicate() {
        String newSystemName = "Existing System";

        when(environmentsService.getLazyProjectByName(PROJECT_NAME)).thenReturn(lazyProject);

        assertThrows(EnvironmentNotFoundException.class, () ->
                dynamicEnvironmentService.updateEnvironment(
                        PROJECT_NAME, ENV_NAME, SYSTEM_NAME, connection, null, newSystemName));
    }
}

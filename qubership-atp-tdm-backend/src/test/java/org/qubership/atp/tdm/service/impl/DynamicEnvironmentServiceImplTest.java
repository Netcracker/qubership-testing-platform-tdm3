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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.qubership.atp.tdm.env.configurator.model.envgen.YamlEnvironment;
import org.qubership.atp.tdm.env.configurator.exceptions.internal.TdmEnvConvertFullSystemByNameException;
import org.qubership.atp.tdm.env.configurator.exceptions.internal.TdmEnvConvertLazyEnvironmentByNameException;
import org.qubership.atp.tdm.env.configurator.model.LazyEnvironment;
import org.qubership.atp.tdm.env.configurator.model.LazyProject;
import org.qubership.atp.tdm.env.configurator.model.LazySystem;
import org.qubership.atp.tdm.env.configurator.service.EnvironmentsService;
import org.qubership.atp.tdm.exceptions.internal.EnvironmentNotFoundException;
import org.qubership.atp.tdm.model.DynamicEnvironment;
import org.qubership.atp.tdm.model.rest.ResponseMessage;
import org.qubership.atp.tdm.model.rest.ResponseType;
import org.qubership.atp.tdm.model.rest.requests.EnvironmentConnectionRequest;
import org.qubership.atp.tdm.repo.CatalogRepository;
import org.qubership.atp.tdm.repo.DynamicEnvironmentRepository;

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
    private CatalogRepository catalogRepository;

    @InjectMocks
    private DynamicEnvironmentServiceImpl dynamicEnvironmentService;

    private LazyProject lazyProject;
    private LazyEnvironment lazyEnvironment;
    private LazySystem lazySystem;
    private EnvironmentConnectionRequest connection;

    @BeforeEach
    void setUp() {
        UUID projectId = UUID.randomUUID();
        UUID environmentId = UUID.randomUUID();
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

        verify(environmentsService, never()).registerEnvironmentInCache(
                any(), anyString(), anyString(), anyString(), anyString(), anyMap());
        verify(dynamicEnvironmentRepository, never()).save(any());
    }

    @Test
    void createEnvironment_envAndSystemFound_returnsDuplicateError() {
        when(environmentsService.getLazyProjectByName(PROJECT_NAME)).thenReturn(lazyProject);
        when(environmentsService.getLazyEnvironmentByName(lazyProject.getId(), ENV_NAME))
                .thenReturn(lazyEnvironment);
        when(environmentsService.getLazySystemByName(
                lazyProject.getId(), lazyEnvironment.getId(), SYSTEM_NAME)).thenReturn(lazySystem);

        assertThrows(IllegalArgumentException.class, () -> dynamicEnvironmentService.createEnvironment(
                PROJECT_NAME, ENV_NAME, SYSTEM_NAME, connection));

        verify(environmentsService, never()).addSystemToEnvironment(
                any(), any(), anyString(), anyString(), anyString(), anyMap());
        verify(dynamicEnvironmentRepository, never()).save(any());
    }

    @Test
    void createEnvironment_envFoundSystemNotFound_addsSystemAndSaves() {
        when(environmentsService.getLazyProjectByName(PROJECT_NAME)).thenReturn(lazyProject);
        when(environmentsService.getLazyEnvironmentByName(lazyProject.getId(), ENV_NAME))
                .thenReturn(lazyEnvironment);
        when(environmentsService.getLazySystemByName(
                lazyProject.getId(), lazyEnvironment.getId(), SYSTEM_NAME))
                .thenThrow(new TdmEnvConvertFullSystemByNameException(SYSTEM_NAME))
                .thenReturn(lazySystem);

        ResponseMessage response = dynamicEnvironmentService.createEnvironment(
                PROJECT_NAME, ENV_NAME, SYSTEM_NAME, connection);

        assertEquals(ResponseType.SUCCESS, response.getType());
        verify(environmentsService).addSystemToEnvironment(
                eq(lazyProject.getId()), eq(lazyEnvironment.getId()), eq(SYSTEM_NAME),
                eq(connection.getName()), eq(connection.getType()), eq(connection.getParameters()));
        verify(dynamicEnvironmentRepository).save(any(DynamicEnvironment.class));
    }

    @Test
    void createEnvironment_envNotFound_registersEnvironmentAndSaves() {
        when(environmentsService.getLazyProjectByName(PROJECT_NAME)).thenReturn(lazyProject);
        when(environmentsService.getLazyEnvironmentByName(lazyProject.getId(), ENV_NAME))
                .thenThrow(new TdmEnvConvertLazyEnvironmentByNameException(ENV_NAME, lazyProject.getId().toString()));
        when(environmentsService.registerEnvironmentInCache(
                eq(lazyProject.getId()), eq(ENV_NAME), eq(SYSTEM_NAME),
                eq(connection.getName()), eq(connection.getType()), eq(connection.getParameters())))
                .thenReturn(lazyEnvironment);

        ResponseMessage response = dynamicEnvironmentService.createEnvironment(
                PROJECT_NAME, ENV_NAME, SYSTEM_NAME, connection);

        assertEquals(ResponseType.SUCCESS, response.getType());
        verify(environmentsService).registerEnvironmentInCache(
                eq(lazyProject.getId()), eq(ENV_NAME), eq(SYSTEM_NAME),
                eq(connection.getName()), eq(connection.getType()), eq(connection.getParameters()));
        verify(dynamicEnvironmentRepository).save(any(DynamicEnvironment.class));
        verify(environmentsService, never()).addSystemToEnvironment(
                any(), any(), anyString(), anyString(), anyString(), anyMap());
    }

    @Test
    void deleteEnvironment_envFound_removesFromCacheAndDeletesAllRows() {
        DynamicEnvironment record = new DynamicEnvironment(
                lazyEnvironment.getId(), lazyProject.getId(), ENV_NAME, SYSTEM_NAME,
                "DB", "DB", "{}");

        when(environmentsService.getLazyProjectByName(PROJECT_NAME)).thenReturn(lazyProject);
        when(environmentsService.getLazyEnvironmentByName(lazyProject.getId(), ENV_NAME)).thenReturn(lazyEnvironment);
        when(dynamicEnvironmentRepository.findAllByEnvNameAndProjectId(ENV_NAME, lazyProject.getId()))
                .thenReturn(Collections.singletonList(record));

        ResponseMessage response = dynamicEnvironmentService.deleteEnvironment(PROJECT_NAME, ENV_NAME, null);

        assertEquals(ResponseType.SUCCESS, response.getType());
        verify(environmentsService).removeEnvironmentFromCache(lazyEnvironment.getId());
        verify(dynamicEnvironmentRepository).deleteByEnvNameAndProjectId(ENV_NAME, lazyProject.getId());
    }

    @Test
    void deleteEnvironment_thenRecreate_registersFreshEnvironment() {
        DynamicEnvironment record = new DynamicEnvironment(
                lazyEnvironment.getId(), lazyProject.getId(), ENV_NAME, SYSTEM_NAME,
                "DB", "DB", "{}");

        when(environmentsService.getLazyProjectByName(PROJECT_NAME)).thenReturn(lazyProject);
        when(dynamicEnvironmentRepository.findAllByEnvNameAndProjectId(ENV_NAME, lazyProject.getId()))
                .thenReturn(Collections.singletonList(record))
                .thenReturn(Collections.emptyList());
        when(environmentsService.getLazyEnvironmentByName(lazyProject.getId(), ENV_NAME))
                .thenReturn(lazyEnvironment)
                .thenThrow(new TdmEnvConvertLazyEnvironmentByNameException(ENV_NAME, lazyProject.getId().toString()));

        when(environmentsService.registerEnvironmentInCache(
                eq(lazyProject.getId()), eq(ENV_NAME), eq(SYSTEM_NAME),
                eq(connection.getName()), eq(connection.getType()), eq(connection.getParameters())))
                .thenReturn(lazyEnvironment);

        dynamicEnvironmentService.deleteEnvironment(PROJECT_NAME, ENV_NAME, null);
        ResponseMessage response = dynamicEnvironmentService.createEnvironment(
                PROJECT_NAME, ENV_NAME, SYSTEM_NAME, connection);

        verify(environmentsService).removeEnvironmentFromCache(lazyEnvironment.getId());
        verify(environmentsService, times(1)).registerEnvironmentInCache(
                eq(lazyProject.getId()), eq(ENV_NAME), eq(SYSTEM_NAME),
                eq(connection.getName()), eq(connection.getType()), eq(connection.getParameters()));
        verify(environmentsService, never()).addSystemToEnvironment(
                any(), any(), anyString(), anyString(), anyString(), anyMap());
        verify(dynamicEnvironmentRepository, times(1)).save(any(DynamicEnvironment.class));
        assertEquals(ResponseType.SUCCESS, response.getType());
    }

    @Test
    void deleteEnvironment_envNotFoundInDb_skipsCacheAndDbOps() {
        when(environmentsService.getLazyProjectByName(PROJECT_NAME)).thenReturn(lazyProject);

        assertThrows(EnvironmentNotFoundException.class,
                () -> dynamicEnvironmentService.deleteEnvironment(PROJECT_NAME, ENV_NAME, SYSTEM_NAME));

        verify(environmentsService, never()).removeEnvironmentFromCache(any());
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
        when(environmentsService.getLazyEnvironmentByName(lazyProject.getId(), ENV_NAME))
                .thenThrow(new TdmEnvConvertLazyEnvironmentByNameException(ENV_NAME, lazyProject.getId().toString()));

        assertThrows(TdmEnvConvertLazyEnvironmentByNameException.class, () ->
                dynamicEnvironmentService.updateEnvironment(
                        PROJECT_NAME, ENV_NAME, SYSTEM_NAME, connection, null, null));
    }

    @Test
    void updateEnvironment_envAndSystemExist_updatesConnection() {
        DynamicEnvironment record = new DynamicEnvironment(
                lazySystem.getId(), lazyProject.getId(), ENV_NAME, SYSTEM_NAME,
                "DB", "DB", "{}");

        when(environmentsService.getLazyProjectByName(PROJECT_NAME)).thenReturn(lazyProject);
        when(environmentsService.getLazyEnvironmentByName(lazyProject.getId(), ENV_NAME))
                .thenReturn(lazyEnvironment);
        when(dynamicEnvironmentRepository.findByEnvNameAndSystemNameAndProjectId(ENV_NAME, SYSTEM_NAME, lazyProject.getId()))
                .thenReturn(Optional.of(record));

        ResponseMessage response = dynamicEnvironmentService.updateEnvironment(
                PROJECT_NAME, ENV_NAME, SYSTEM_NAME, connection, null, null);

        assertEquals(ResponseType.SUCCESS, response.getType());
        verify(environmentsService).updateConnectionInCache(
                eq(lazyEnvironment.getId()), eq(SYSTEM_NAME),
                eq(connection.getName()), eq(connection.getType()), eq(connection.getParameters()));

        ArgumentCaptor<DynamicEnvironment> captor = ArgumentCaptor.forClass(DynamicEnvironment.class);
        verify(dynamicEnvironmentRepository).save(captor.capture());
        assertTrue(captor.getValue().getConnectionParameters().contains("localhost"));
    }

    @Test
    void updateEnvironment_withNewNames_renamesAndUpdatesConnection() {
        String newEnvName = "Renamed Environment";
        String newSystemName = "Renamed System";
        DynamicEnvironment record = new DynamicEnvironment(
                lazySystem.getId(), lazyProject.getId(), ENV_NAME, SYSTEM_NAME,
                "DB", "DB", "{}");

        when(environmentsService.getLazyProjectByName(PROJECT_NAME)).thenReturn(lazyProject);
        when(environmentsService.getLazyEnvironmentByName(lazyProject.getId(), ENV_NAME))
                .thenReturn(lazyEnvironment);
        when(environmentsService.getLazyEnvironmentByName(lazyProject.getId(), newEnvName))
                .thenThrow(new TdmEnvConvertLazyEnvironmentByNameException(newEnvName, lazyProject.getId().toString()))
                .thenReturn(lazyEnvironment);
        when(environmentsService.getLazySystemByName(
                lazyProject.getId(), lazyEnvironment.getId(), newSystemName))
                .thenThrow(new TdmEnvConvertFullSystemByNameException(newSystemName));
        when(dynamicEnvironmentRepository.findAllByEnvNameAndProjectId(ENV_NAME, lazyProject.getId()))
                .thenReturn(Collections.singletonList(record));

        ResponseMessage response = dynamicEnvironmentService.updateEnvironment(
                PROJECT_NAME, ENV_NAME, SYSTEM_NAME, connection, newEnvName, newSystemName);

        assertEquals(ResponseType.SUCCESS, response.getType());
        verify(environmentsService).renameSystemInCache(
                lazyEnvironment.getId(), SYSTEM_NAME, newSystemName);
        verify(environmentsService).renameEnvironmentInCache(lazyEnvironment.getId(), newEnvName);

        ArgumentCaptor<DynamicEnvironment> captor = ArgumentCaptor.forClass(DynamicEnvironment.class);
        verify(dynamicEnvironmentRepository).save(captor.capture());
        DynamicEnvironment saved = captor.getValue();
        assertEquals(newEnvName, saved.getEnvName());
        assertEquals(newSystemName, saved.getSystemName());
        assertEquals(YamlEnvironment.composeSystemId(newEnvName, newSystemName), saved.getId());
    }

    @Test
    void updateEnvironment_newEnvNameAlreadyExists_throwsDuplicate() {
        String newEnvName = "Existing Environment";
        LazyEnvironment existingEnv = new LazyEnvironment();
        existingEnv.setId(UUID.randomUUID());
        existingEnv.setName(newEnvName);

        when(environmentsService.getLazyProjectByName(PROJECT_NAME)).thenReturn(lazyProject);
        when(environmentsService.getLazyEnvironmentByName(lazyProject.getId(), ENV_NAME))
                .thenReturn(lazyEnvironment);
        when(environmentsService.getLazyEnvironmentByName(lazyProject.getId(), newEnvName))
                .thenReturn(existingEnv);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                dynamicEnvironmentService.updateEnvironment(
                        PROJECT_NAME, ENV_NAME, SYSTEM_NAME, connection, newEnvName, null));
        assertTrue(ex.getMessage().contains(newEnvName));
        verify(environmentsService, never()).updateConnectionInCache(
                any(), anyString(), anyString(), anyString(), anyMap());
    }

    @Test
    void updateEnvironment_newSystemNameAlreadyExists_throwsDuplicate() {
        String newSystemName = "Existing System";
        LazySystem existingSystem = new LazySystem();
        existingSystem.setId(UUID.randomUUID());
        existingSystem.setName(newSystemName);

        when(environmentsService.getLazyProjectByName(PROJECT_NAME)).thenReturn(lazyProject);
        when(environmentsService.getLazyEnvironmentByName(lazyProject.getId(), ENV_NAME))
                .thenReturn(lazyEnvironment);
        when(environmentsService.getLazySystemByName(
                lazyProject.getId(), lazyEnvironment.getId(), newSystemName))
                .thenReturn(existingSystem);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                dynamicEnvironmentService.updateEnvironment(
                        PROJECT_NAME, ENV_NAME, SYSTEM_NAME, connection, null, newSystemName));
        assertTrue(ex.getMessage().contains(newSystemName));
        verify(environmentsService, never()).updateConnectionInCache(
                any(), anyString(), anyString(), anyString(), anyMap());
    }

    @Test
    void deleteEnvironment_noSystemsInDb_envInCache_removesFromCache() {
        when(environmentsService.getLazyProjectByName(PROJECT_NAME)).thenReturn(lazyProject);
        when(dynamicEnvironmentRepository.findAllByEnvNameAndProjectId(ENV_NAME, lazyProject.getId()))
                .thenReturn(Collections.emptyList());
        when(environmentsService.getLazyEnvironmentByName(lazyProject.getId(), ENV_NAME))
                .thenReturn(lazyEnvironment);

        ResponseMessage response = dynamicEnvironmentService.deleteEnvironment(PROJECT_NAME, ENV_NAME, null);

        assertEquals(ResponseType.SUCCESS, response.getType());
        verify(environmentsService).removeEnvironmentFromCache(lazyEnvironment.getId());
        verify(dynamicEnvironmentRepository).deleteByEnvNameAndProjectId(ENV_NAME, lazyProject.getId());
    }
}

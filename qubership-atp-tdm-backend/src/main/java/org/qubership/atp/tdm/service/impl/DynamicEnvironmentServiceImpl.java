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

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.qubership.atp.tdm.env.configurator.exceptions.internal.TdmEnvConvertFullSystemByNameException;
import org.qubership.atp.tdm.env.configurator.exceptions.internal.TdmEnvConvertLazyEnvironmentByNameException;
import org.qubership.atp.tdm.env.configurator.model.LazyEnvironment;
import org.qubership.atp.tdm.env.configurator.model.LazyProject;
import org.qubership.atp.tdm.env.configurator.model.LazySystem;
import org.qubership.atp.tdm.env.configurator.model.envgen.ConnectionType;
import org.qubership.atp.tdm.env.configurator.model.envgen.YamlEnvironment;
import org.qubership.atp.tdm.env.configurator.service.EnvironmentsService;
import org.qubership.atp.tdm.model.DynamicEnvironment;
import org.qubership.atp.tdm.model.rest.ResponseMessage;
import org.qubership.atp.tdm.model.rest.ResponseType;
import org.qubership.atp.tdm.model.rest.requests.EnvironmentConnectionRequest;
import org.qubership.atp.tdm.repo.DynamicEnvironmentRepository;
import org.qubership.atp.tdm.service.DynamicEnvironmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class DynamicEnvironmentServiceImpl implements DynamicEnvironmentService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final EnvironmentsService environmentsService;
    private final DynamicEnvironmentRepository dynamicEnvironmentRepository;

    @Autowired
    public DynamicEnvironmentServiceImpl(@Nonnull EnvironmentsService environmentsService,
                                         @Nonnull DynamicEnvironmentRepository dynamicEnvironmentRepository) {
        this.environmentsService = environmentsService;
        this.dynamicEnvironmentRepository = dynamicEnvironmentRepository;
    }

    @Override
    public ResponseMessage createEnvironment(@Nonnull String projectName, @Nonnull String envName,
                                             @Nonnull String systemName,
                                             @Nonnull EnvironmentConnectionRequest connection) {
        log.info("Creating dynamic environment [{}] with system [{}] for project [{}].",
                envName, systemName, projectName);
        validateConnection(connection);

        LazyProject lazyProject;
        try {
            lazyProject = environmentsService.getLazyProjectByName(projectName);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(String.format("Project [%s] not found.", projectName));
        }
        UUID projectId = lazyProject.getId();

        try {
            LazyEnvironment lazyEnvironment = environmentsService.getLazyEnvironmentByName(projectId, envName);
            UUID envId = lazyEnvironment.getId();
            try {
                environmentsService.getLazySystemByName(projectId, envId, systemName);
                throw new IllegalArgumentException(
                        String.format("System [%s] already exists in environment [%s]. Use PUT to update.",
                                systemName, envName)
                );
            } catch (TdmEnvConvertFullSystemByNameException e) {
                LazySystem lazySystem = createSystemFromConnection(projectId, envId, systemName, connection);
                String parametersJson = serializeParameters(connection.getParameters(), envName);
                DynamicEnvironment record = new DynamicEnvironment(
                        lazySystem.getId(), projectId, envName, systemName,
                        connection.getName(), connection.getType(), parametersJson);
                dynamicEnvironmentRepository.save(record);
                log.info("System [{}] added to dynamic environment [{}] and persisted to H2.",
                        systemName, envName);
                return new ResponseMessage(ResponseType.SUCCESS,
                        String.format("Environment [%s] created successfully.", envName));
            }
        } catch (TdmEnvConvertLazyEnvironmentByNameException e) {
            LazyEnvironment lazyEnvironment = environmentsService.registerEnvironmentInCache(
                    projectId, envName, systemName,
                    connection.getName(), connection.getType(), connection.getParameters());
            String parametersJson = serializeParameters(connection.getParameters(), envName);
            DynamicEnvironment record = new DynamicEnvironment(
                    lazyEnvironment.getId(), projectId, envName, systemName,
                    connection.getName(), connection.getType(), parametersJson);
            dynamicEnvironmentRepository.save(record);
            log.info("Dynamic environment [{}] created with id [{}].", envName, lazyEnvironment.getId());
            return new ResponseMessage(ResponseType.SUCCESS,
                    String.format("Environment [%s] created successfully.", envName));
        }
    }

    @Override
    @Transactional
    public ResponseMessage updateEnvironment(@Nonnull String projectName, @Nonnull String envName,
                                             @Nonnull String systemName,
                                             @Nonnull EnvironmentConnectionRequest connection,
                                             @Nullable String newEnvName, @Nullable String newSystemName) {
        log.info("Updating connection for environment [{}] system [{}] in project [{}].",
                envName, systemName, projectName);
        if (connection.getParameters() == null || connection.getParameters().isEmpty()) {
            throw new IllegalArgumentException(
                    "Connection 'parameters' must not be null or empty for update.");
        }

        LazyProject lazyProject = environmentsService.getLazyProjectByName(projectName);
        UUID projectId = lazyProject.getId();
        LazyEnvironment lazyEnvironment = environmentsService.getLazyEnvironmentByName(projectId, envName);
        UUID envId = lazyEnvironment.getId();

        environmentsService.getLazySystemByName(projectId, envId, systemName);

        if (StringUtils.isNotBlank(newEnvName) && !newEnvName.equals(envName)) {
            try {
                environmentsService.getLazyEnvironmentByName(projectId, newEnvName);
                throw new IllegalArgumentException(
                        String.format("Environment [%s] already exists.", newEnvName));
            } catch (TdmEnvConvertLazyEnvironmentByNameException ignored) {
                // new name is available
            }
        }

        if (StringUtils.isNotBlank(newSystemName) && !newSystemName.equals(systemName)) {
            try {
                environmentsService.getLazySystemByName(projectId, envId, newSystemName);
                throw new IllegalArgumentException(
                        String.format("System [%s] already exists in environment [%s].",
                                newSystemName, envName));
            } catch (TdmEnvConvertFullSystemByNameException ignored) {
                // new name is available
            }
        }

        environmentsService.updateConnectionInCache(envId, systemName,
                connection.getName(), connection.getType(), connection.getParameters());

        Optional<DynamicEnvironment> recordOpt = dynamicEnvironmentRepository.findByEnvNameAndProjectId(envName, projectId);

        if (StringUtils.isNotBlank(newSystemName)) {
            environmentsService.renameSystemInCache(envId, systemName, newSystemName);
        }
        if (StringUtils.isNotBlank(newEnvName)) {
            environmentsService.renameEnvironmentInCache(envId, newEnvName);
        }

        String finalEnvName = StringUtils.isNotBlank(newEnvName) ? newEnvName : envName;
        String finalSystemName = StringUtils.isNotBlank(newSystemName) ? newSystemName : systemName;
        UUID finalSystemId = YamlEnvironment.composeSystemId(finalEnvName, finalSystemName);

        recordOpt.ifPresent(record -> {
            String parametersJson = serializeParameters(connection.getParameters(), finalEnvName);
            String resolvedConnectionName = connection.getName() != null
                    ? connection.getName() : record.getConnectionName();
            String resolvedConnectionType = connection.getType() != null
                    ? connection.getType() : record.getConnectionType();

            if (StringUtils.isNotBlank(newEnvName) || StringUtils.isNotBlank(newSystemName)) {
                dynamicEnvironmentRepository.delete(record);
                dynamicEnvironmentRepository.flush();
                DynamicEnvironment updated = new DynamicEnvironment(
                        finalSystemId, record.getProjectId(), finalEnvName, finalSystemName,
                        resolvedConnectionName, resolvedConnectionType, parametersJson);
                dynamicEnvironmentRepository.save(updated);
            } else {
                record.setConnectionParameters(parametersJson);
                record.setConnectionName(resolvedConnectionName);
                record.setConnectionType(resolvedConnectionType);
                dynamicEnvironmentRepository.save(record);
            }
            log.info("Updated dynamic environment record in H2 for [{}].", finalEnvName);
        });

        return new ResponseMessage(ResponseType.SUCCESS,
                String.format("Environment [%s] updated successfully.", finalEnvName));
    }

    @Override
    @Transactional
    public ResponseMessage deleteEnvironment(@Nonnull String projectName, @Nonnull String envName) {
        log.info("Deleting dynamic environment [{}] for project [{}].", envName, projectName);

        LazyProject lazyProject = environmentsService.getLazyProjectByName(projectName);
        UUID projectId = lazyProject.getId();

        dynamicEnvironmentRepository.findByEnvNameAndProjectId(envName, projectId).ifPresent(record -> {
            environmentsService.removeEnvironmentFromCache(record.getId());
            dynamicEnvironmentRepository.deleteByEnvNameAndProjectId(envName, projectId);
            log.info("Dynamic environment [{}] deleted from H2 and cache.", envName);
        });

        return new ResponseMessage(ResponseType.SUCCESS,
                String.format("Environment [%s] deleted successfully.", envName));
    }

    private LazySystem createSystemFromConnection(@Nonnull UUID projectId, @Nonnull UUID envId,
                                                  @Nonnull String systemName,
                                                  @Nonnull EnvironmentConnectionRequest connection) {
        log.info("Adding system [{}] to existing environment [{}] for project [{}].", systemName, envId, projectId);

        environmentsService.addSystemToEnvironment(projectId, envId, systemName,
                connection.getName(), connection.getType(), connection.getParameters());

        return environmentsService.getLazySystemByName(projectId, envId, systemName);
    }

    private void validateConnection(@Nonnull EnvironmentConnectionRequest connection) {
        if (StringUtils.isBlank(connection.getName())) {
            throw new IllegalArgumentException("Connection 'name' must not be blank.");
        }
        if (StringUtils.isBlank(connection.getType())) {
            throw new IllegalArgumentException("Connection 'type' must not be blank.");
        }
        try {
            ConnectionType.fromValue(connection.getType());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    String.format("Connection 'type' value [%s] is not valid.", connection.getType()));
        }
        if (connection.getParameters() == null || connection.getParameters().isEmpty()) {
            throw new IllegalArgumentException("Connection 'parameters' must not be null or empty.");
        }
    }

    private String serializeParameters(Map<String, String> parameters, String envName) {
        try {
            return OBJECT_MAPPER.writeValueAsString(parameters);
        } catch (JsonProcessingException ex) {
            log.warn("Failed to serialize connection parameters for env [{}], storing as empty.", envName, ex);
            return "{}";
        }
    }
}

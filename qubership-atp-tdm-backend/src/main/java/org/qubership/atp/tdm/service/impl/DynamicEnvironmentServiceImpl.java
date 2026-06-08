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
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.qubership.atp.tdm.env.configurator.model.LazyEnvironment;
import org.qubership.atp.tdm.env.configurator.model.LazyProject;
import org.qubership.atp.tdm.env.configurator.model.LazySystem;
import org.qubership.atp.tdm.env.configurator.model.envgen.ConnectionType;
import org.qubership.atp.tdm.env.configurator.service.EnvironmentsService;
import org.qubership.atp.tdm.model.DynamicEnvironment;
import org.qubership.atp.tdm.model.rest.ResponseMessage;
import org.qubership.atp.tdm.model.rest.ResponseType;
import org.qubership.atp.tdm.model.rest.requests.EnvironmentConnectionRequest;
import org.qubership.atp.tdm.repo.DynamicEnvironmentRepository;
import org.qubership.atp.tdm.service.DynamicEnvironmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Nonnull;
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

        LazyProject lazyProject = environmentsService.getLazyProjectByName(projectName);
        UUID projectId = lazyProject.getId();

        LazyEnvironment lazyEnvironment = environmentsService.registerEnvironmentInCache(
                projectId, envName, systemName,
                connection.getName(), connection.getType(), connection.getParameters());

        if (!dynamicEnvironmentRepository.existsByEnvNameAndProjectId(envName, projectId)) {
            String parametersJson = serializeParameters(connection.getParameters(), envName);
            DynamicEnvironment record = new DynamicEnvironment(
                    lazyEnvironment.getId(), projectId, envName, systemName,
                    connection.getName(), connection.getType(), parametersJson);
            dynamicEnvironmentRepository.save(record);
            log.info("Dynamic environment [{}] persisted to H2.", envName);
        }

        log.info("Dynamic environment [{}] created with id [{}].", envName, lazyEnvironment.getId());
        return new ResponseMessage(ResponseType.SUCCESS,
                String.format("Environment [%s] created successfully.", envName));
    }

    @Override
    public ResponseMessage updateEnvironment(@Nonnull String projectName, @Nonnull String envName,
                                             @Nonnull String systemName,
                                             @Nonnull EnvironmentConnectionRequest connection) {
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

        environmentsService.updateConnectionInCache(envId, systemName,
                connection.getName(), connection.getType(), connection.getParameters());

        dynamicEnvironmentRepository.findByEnvNameAndProjectId(envName, projectId).ifPresent(record -> {
            String parametersJson = serializeParameters(connection.getParameters(), envName);
            record.setConnectionParameters(parametersJson);
            if (connection.getName() != null) {
                record.setConnectionName(connection.getName());
            }
            if (connection.getType() != null) {
                record.setConnectionType(connection.getType());
            }
            dynamicEnvironmentRepository.save(record);
            log.info("Updated connection parameters in H2 for dynamic environment [{}].", envName);
        });

        return new ResponseMessage(ResponseType.SUCCESS,
                String.format("Environment [%s] updated successfully.", envName));
    }

    @Override
    public ResponseMessage deleteEnvironment(@Nonnull String projectName, @Nonnull String envName) {
        log.info("Deleting dynamic environment [{}] for project [{}].", envName, projectName);

        LazyProject lazyProject = environmentsService.getLazyProjectByName(projectName);
        UUID projectId = lazyProject.getId();

        dynamicEnvironmentRepository.findByEnvNameAndProjectId(envName, projectId).ifPresent(record -> {
            environmentsService.removeEnvironmentFromCache(record.getId());
            dynamicEnvironmentRepository.delete(record);
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

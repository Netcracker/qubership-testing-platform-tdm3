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
import org.qubership.atp.tdm.env.configurator.model.ConnectionType;
import org.qubership.atp.tdm.exceptions.internal.EnvironmentNotFoundException;
import org.qubership.atp.tdm.env.configurator.model.LazyProject;
import org.qubership.atp.tdm.env.configurator.service.EnvironmentsService;
import org.qubership.atp.tdm.exceptions.internal.SystemNotFoundException;
import org.qubership.atp.tdm.model.DynamicEnvironment;
import org.qubership.atp.tdm.model.DynamicSystem;
import org.qubership.atp.tdm.model.rest.ResponseMessage;
import org.qubership.atp.tdm.model.rest.ResponseType;
import org.qubership.atp.tdm.model.rest.requests.EnvironmentConnectionRequest;
import org.qubership.atp.tdm.repo.DynamicEnvironmentRepository;
import org.qubership.atp.tdm.repo.DynamicSystemRepository;
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
    private final DynamicSystemRepository dynamicSystemRepository;

    @Autowired
    public DynamicEnvironmentServiceImpl(@Nonnull EnvironmentsService environmentsService,
                                         @Nonnull DynamicEnvironmentRepository dynamicEnvironmentRepository,
                                         @Nonnull DynamicSystemRepository dynamicSystemRepository) {
        this.environmentsService = environmentsService;
        this.dynamicEnvironmentRepository = dynamicEnvironmentRepository;
        this.dynamicSystemRepository = dynamicSystemRepository;
    }

    @Override
    @Transactional
    public ResponseMessage createEnvironment(@Nonnull String projectName, @Nonnull String envName,
                                             @Nonnull String systemName,
                                             @Nonnull EnvironmentConnectionRequest connection) {
        log.info("Creating dynamic environment [{}] with system [{}] for project [{}].",
                envName, systemName, projectName);
        validateConnection(connection);

        UUID projectId = getLazyProjectCatch(projectName).getId();
        String parametersJson = serializeParameters(connection.getParameters(), envName);

        Optional<DynamicEnvironment> envRecordOpt = dynamicEnvironmentRepository.findByEnvNameAndProjectId(envName, projectId);

        if (envRecordOpt.isPresent()) {
            DynamicEnvironment envRecord = envRecordOpt.get();
            if (dynamicSystemRepository.existsByEnvIdAndSystemName(envRecord.getId(), systemName)) {
                throw new IllegalArgumentException(
                        String.format("System [%s] already exists in environment [%s]. Use PUT to update.",
                                systemName, envName));
            }
            DynamicSystem systemRecord = new DynamicSystem(envRecord, systemName,
                    connection.getName(), connection.getType(), parametersJson);
            dynamicSystemRepository.save(systemRecord);
            log.info("System [{}] added to dynamic environment [{}] and persisted.", systemName, envName);
        } else {
            DynamicEnvironment envRecord = new DynamicEnvironment(projectId, envName);
            dynamicEnvironmentRepository.save(envRecord);
            DynamicSystem systemRecord = new DynamicSystem(envRecord, systemName,
                    connection.getName(), connection.getType(), parametersJson);
            dynamicSystemRepository.save(systemRecord);
            log.info("Dynamic environment [{}] created.", envName);
        }

        return new ResponseMessage(ResponseType.SUCCESS,
                String.format("Environment [%s] created successfully.", envName));
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
        validateConnection(connection);

        UUID projectId = getLazyProjectCatch(projectName).getId();
        DynamicEnvironment env = dynamicEnvironmentRepository.findByEnvNameAndProjectId(envName, projectId)
                .orElseThrow(() -> new EnvironmentNotFoundException(envName, projectName));
        DynamicSystem system = dynamicSystemRepository.findByEnvIdAndSystemName(env.getId(), systemName)
                .orElseThrow(() -> new SystemNotFoundException(systemName, envName, projectName));

        String finalEnvName = StringUtils.isNotBlank(newEnvName) ? newEnvName : envName;
        String finalSystemName = StringUtils.isNotBlank(newSystemName) ? newSystemName : systemName;

        if (!finalEnvName.equals(envName)) {
            if (dynamicEnvironmentRepository.existsByEnvNameAndProjectId(finalEnvName, projectId)) {
                throw new IllegalArgumentException(
                        String.format("Environment [%s] already exists.", finalEnvName));
            }
        }

        if (!finalSystemName.equals(systemName)) {
            if (dynamicSystemRepository.existsByEnvIdAndSystemName(env.getId(), finalSystemName)) {
                throw new IllegalArgumentException(
                        String.format("System [%s] already exists in environment [%s].",
                                finalSystemName, envName));
            }
        }

        env.setEnvName(finalEnvName);
        system.setSystemName(finalSystemName);
        system.setConnectionName(connection.getName());
        system.setConnectionType(connection.getType());
        system.setConnectionParameters(serializeParameters(connection.getParameters(), finalEnvName));

        return new ResponseMessage(ResponseType.SUCCESS,
                String.format("Environment [%s] updated successfully.", finalEnvName));
    }

    @Override
    @Transactional
    public ResponseMessage deleteEnvironment(@Nonnull String projectName, @Nonnull String envName,
                                             @Nullable String systemName) {
        log.info("Deleting dynamic environment [{}] system [{}] for project [{}].", envName, systemName, projectName);

        UUID projectId = getLazyProjectCatch(projectName).getId();
        Optional<DynamicEnvironment> envRecordOpt = dynamicEnvironmentRepository.findByEnvNameAndProjectId(envName, projectId);

        if (StringUtils.isNotBlank(systemName)) {
            if (!envRecordOpt.isPresent()) {
                throw new EnvironmentNotFoundException(envName, projectName);
            }
            DynamicEnvironment envRecord = envRecordOpt.get();
            DynamicSystem sys = dynamicSystemRepository
                    .findByEnvIdAndSystemName(envRecord.getId(), systemName)
                    .orElseThrow(() -> new EnvironmentNotFoundException(envName, projectName));
            dynamicSystemRepository.delete(sys);
            dynamicSystemRepository.flush();
            log.info("System [{}] deleted from environment [{}] in H2.", systemName, envName);
        } else {
            if (!envRecordOpt.isPresent()) {
                throw new EnvironmentNotFoundException(envName, projectName);
            }
            dynamicEnvironmentRepository.deleteByEnvNameAndProjectId(envName, projectId);
            log.info("Dynamic environment [{}] deleted from H2.", envName);
        }

        return new ResponseMessage(ResponseType.SUCCESS,
                String.format("Environment [%s] deleted successfully.", envName));
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

    private LazyProject getLazyProjectCatch(String projectName) {
        try {
            return environmentsService.getLazyProjectByName(projectName);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(String.format("Project [%s] not found.", projectName));
        }
    }
}

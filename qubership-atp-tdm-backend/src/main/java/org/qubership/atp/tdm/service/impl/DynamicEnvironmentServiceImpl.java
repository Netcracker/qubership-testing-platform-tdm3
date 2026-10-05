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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.qubership.atp.tdm.exceptions.internal.EnvironmentNotFoundException;
import org.qubership.atp.tdm.env.configurator.model.LazyProject;
import org.qubership.atp.tdm.env.configurator.service.EnvironmentsService;
import org.qubership.atp.tdm.exceptions.internal.SystemNotFoundException;
import org.qubership.atp.tdm.model.DynamicConnection;
import org.qubership.atp.tdm.model.DynamicEnvironment;
import org.qubership.atp.tdm.model.DynamicSystem;
import org.qubership.atp.tdm.model.rest.EnvironmentConnectionsResponse;
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
import com.fasterxml.jackson.core.type.TypeReference;
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
        return createEnvironment(projectName, envName, systemName, List.of(connection));
    }

    @Override
    @Transactional
    public ResponseMessage createEnvironment(@Nonnull String projectName, @Nonnull String envName,
                                             @Nonnull String systemName,
                                             @Nonnull List<EnvironmentConnectionRequest> connections) {
        validateRequiredFields(projectName, envName, systemName);
        log.info("Creating dynamic environment [{}] with system [{}] ({} connection(s)) for project [{}].",
                envName, systemName, connections == null ? 0 : connections.size(), projectName);
        validateConnections(connections);

        UUID projectId = getLazyProjectCatch(projectName).getId();
        Optional<DynamicEnvironment> envRecordOpt = dynamicEnvironmentRepository.findByEnvNameAndProjectId(envName, projectId);

        DynamicEnvironment envRecord;
        if (envRecordOpt.isPresent()) {
            envRecord = envRecordOpt.get();
            if (dynamicSystemRepository.existsByEnvIdAndSystemName(envRecord.getId(), systemName)) {
                throw new IllegalArgumentException(
                        String.format("System [%s] already exists in environment [%s]. Use PUT to update.",
                                systemName, envName));
            }
            log.info("System [{}] added to dynamic environment [{}] and persisted.", systemName, envName);
        } else {
            envRecord = new DynamicEnvironment(projectId, envName);
            dynamicEnvironmentRepository.save(envRecord);
            log.info("Dynamic environment [{}] created.", envName);
        }

        DynamicSystem systemRecord = new DynamicSystem(envRecord, systemName);
        addConnections(systemRecord, connections, envName);
        dynamicSystemRepository.save(systemRecord);

        return new ResponseMessage(ResponseType.SUCCESS,
                String.format("Environment [%s] created successfully.", envName));
    }

    @Override
    @Transactional
    public ResponseMessage updateEnvironment(@Nonnull String projectName, @Nonnull String envName,
                                             @Nonnull String systemName,
                                             @Nonnull EnvironmentConnectionRequest connection,
                                             @Nullable String newEnvName, @Nullable String newSystemName) {
        return updateEnvironment(projectName, envName, systemName, List.of(connection), newEnvName, newSystemName);
    }

    @Override
    @Transactional
    public ResponseMessage updateEnvironment(@Nonnull String projectName, @Nonnull String envName,
                                             @Nonnull String systemName,
                                             @Nonnull List<EnvironmentConnectionRequest> connections,
                                             @Nullable String newEnvName, @Nullable String newSystemName) {
        validateRequiredFields(projectName, envName, systemName);
        log.info("Updating {} connection(s) for environment [{}] system [{}] in project [{}].",
                connections == null ? 0 : connections.size(), envName, systemName, projectName);
        validateConnections(connections);

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
        upsertConnections(system, connections, finalEnvName);

        return new ResponseMessage(ResponseType.SUCCESS,
                String.format("Environment [%s] updated successfully.", finalEnvName));
    }

    @Override
    @Transactional(readOnly = true)
    public EnvironmentConnectionsResponse getConnections(@Nonnull String projectName, @Nonnull String envName,
                                                         @Nullable String systemName) {
        if (StringUtils.isBlank(projectName) || StringUtils.isBlank(envName)) {
            throw new IllegalArgumentException("projectName and envName are required.");
        }
        log.info("Reading connections for environment [{}] system [{}] in project [{}].",
                envName, systemName, projectName);

        UUID projectId = getLazyProjectCatch(projectName).getId();
        DynamicEnvironment env = dynamicEnvironmentRepository.findByEnvNameAndProjectId(envName, projectId)
                .orElseThrow(() -> new EnvironmentNotFoundException(envName, projectName));

        List<DynamicSystem> systems;
        if (StringUtils.isNotBlank(systemName)) {
            DynamicSystem system = dynamicSystemRepository.findByEnvIdAndSystemName(env.getId(), systemName)
                    .orElseThrow(() -> new SystemNotFoundException(systemName, envName, projectName));
            systems = List.of(system);
        } else {
            systems = dynamicSystemRepository.findAllByEnvId(env.getId());
        }

        List<EnvironmentConnectionsResponse.SystemConnections> views = new ArrayList<>();
        for (DynamicSystem system : systems) {
            views.add(new EnvironmentConnectionsResponse.SystemConnections(
                    system.getSystemName(), toMaskedConnections(system)));
        }
        return new EnvironmentConnectionsResponse(projectName, envName, views);
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

    private void validateRequiredFields(String projectName, String envName, String systemName) {
        if (StringUtils.isBlank(projectName) || StringUtils.isBlank(envName) || StringUtils.isBlank(systemName)) {
            throw new IllegalArgumentException("projectName, envName, and systemName are required.");
        }
    }

    private void validateConnections(List<EnvironmentConnectionRequest> connections) {
        if (connections == null || connections.isEmpty()) {
            throw new IllegalArgumentException("At least one connection is required.");
        }
        Set<String> types = new HashSet<>();
        for (EnvironmentConnectionRequest connection : connections) {
            if (connection == null) {
                throw new IllegalArgumentException("Connection entries must not be null.");
            }
            validateConnection(connection);
            if (!types.add(connection.getType())) {
                throw new IllegalArgumentException(
                        String.format("Duplicate connection type [%s] in the request.", connection.getType()));
            }
        }
    }

    private void validateConnection(@Nonnull EnvironmentConnectionRequest connection) {
        if (StringUtils.isBlank(connection.getType())) {
            throw new IllegalArgumentException("Connection 'type' must not be blank.");
        }
        connection.setType(canonicalType(connection.getType()));
        if (connection.getParameters() == null || connection.getParameters().isEmpty()) {
            throw new IllegalArgumentException("Connection 'parameters' must not be null or empty.");
        }
    }

    private String canonicalType(String type) {
        if ("DB".equalsIgnoreCase(type)) {
            return "DB";
        }
        if ("HTTP".equalsIgnoreCase(type)) {
            return "HTTP";
        }
        throw new IllegalArgumentException(String.format(
                "Connection 'type' value [%s] is not valid. Allowed types: DB, HTTP.", type));
    }

    private void addConnections(DynamicSystem system, List<EnvironmentConnectionRequest> connections, String envName) {
        for (EnvironmentConnectionRequest connection : connections) {
            system.addConnection(connection.getType(), serializeParameters(connection.getParameters(), envName));
        }
    }

    private void upsertConnections(DynamicSystem system, List<EnvironmentConnectionRequest> connections,
                                   String envName) {
        if (system.getConnections() == null) {
            system.setConnections(new ArrayList<>());
        }
        for (EnvironmentConnectionRequest connection : connections) {
            String parametersJson = serializeParameters(connection.getParameters(), envName);
            DynamicConnection existing = system.getConnections().stream()
                    .filter(stored -> connection.getType().equals(stored.getConnectionType()))
                    .findFirst()
                    .orElse(null);
            if (existing == null) {
                system.addConnection(connection.getType(), parametersJson);
            } else {
                existing.setConnectionParameters(parametersJson);
            }
        }
    }

    private List<EnvironmentConnectionsResponse.Connection> toMaskedConnections(DynamicSystem system) {
        List<EnvironmentConnectionsResponse.Connection> result = new ArrayList<>();
        if (system.getConnections() == null) {
            return result;
        }
        for (DynamicConnection stored : system.getConnections()) {
            result.add(new EnvironmentConnectionsResponse.Connection(stored.getConnectionType(),
                    SensitiveParameterMask.mask(deserializeParameters(stored.getConnectionParameters()))));
        }
        return result;
    }

    private Map<String, String> deserializeParameters(String json) {
        if (json == null || json.isEmpty()) {
            return new HashMap<>();
        }
        try {
            return OBJECT_MAPPER.readValue(json, new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            log.warn("Failed to deserialize connection parameters: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    private String serializeParameters(Map<String, String> parameters, String envName) {
        try {
            return OBJECT_MAPPER.writeValueAsString(parameters);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException(
                    String.format("Failed to serialize connection parameters for environment [%s].", envName), ex);
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

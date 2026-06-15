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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.qubership.atp.tdm.env.configurator.exceptions.internal.TdmEnvConvertFullSystemByNameException;
import org.qubership.atp.tdm.env.configurator.exceptions.internal.TdmEnvConvertLazyEnvironmentByNameException;
import org.qubership.atp.tdm.exceptions.internal.EnvironmentNotFoundException;
import org.qubership.atp.tdm.env.configurator.model.LazyEnvironment;
import org.qubership.atp.tdm.env.configurator.model.LazyProject;
import org.qubership.atp.tdm.env.configurator.model.envgen.ConnectionType;
import org.qubership.atp.tdm.env.configurator.model.envgen.YamlEnvironment;
import org.qubership.atp.tdm.env.configurator.service.EnvironmentsService;
import org.qubership.atp.tdm.model.DynamicEnvironment;
import org.qubership.atp.tdm.model.DynamicSystem;
import org.qubership.atp.tdm.model.rest.ResponseMessage;
import org.qubership.atp.tdm.model.rest.ResponseType;
import org.qubership.atp.tdm.model.rest.requests.EnvironmentConnectionRequest;
import org.qubership.atp.tdm.repo.CatalogRepository;
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
    private final CatalogRepository catalogRepository;

    @Autowired
    public DynamicEnvironmentServiceImpl(@Nonnull EnvironmentsService environmentsService,
                                         @Nonnull DynamicEnvironmentRepository dynamicEnvironmentRepository,
                                         @Nonnull DynamicSystemRepository dynamicSystemRepository,
                                         @Nonnull CatalogRepository catalogRepository) {
        this.environmentsService = environmentsService;
        this.dynamicEnvironmentRepository = dynamicEnvironmentRepository;
        this.dynamicSystemRepository = dynamicSystemRepository;
        this.catalogRepository = catalogRepository;
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
                environmentsService.addSystemToEnvironment(projectId, envId, systemName,
                        connection.getName(), connection.getType(), connection.getParameters());

                DynamicEnvironment envRecord = dynamicEnvironmentRepository.findByEnvNameAndProjectId(envName, projectId)
                        .orElseGet(() -> dynamicEnvironmentRepository.save(new DynamicEnvironment(envId, projectId, envName)));

                UUID systemId = YamlEnvironment.composeSystemId(envName, systemName);
                DynamicSystem systemRecord = new DynamicSystem(systemId, envRecord, systemName,
                        connection.getName(), connection.getType(), parametersJson);
                dynamicSystemRepository.save(systemRecord);
                log.info("System [{}] added to dynamic environment [{}] and persisted.", systemName, envName);
                return new ResponseMessage(ResponseType.SUCCESS,
                        String.format("Environment [%s] created successfully.", envName));
            }
        } catch (TdmEnvConvertLazyEnvironmentByNameException e) {
            LazyEnvironment lazyEnvironment = environmentsService.registerEnvironmentInCache(
                    projectId, envName, systemName,
                    connection.getName(), connection.getType(), connection.getParameters());

            DynamicEnvironment envRecord = new DynamicEnvironment(lazyEnvironment.getId(), projectId, envName);
            dynamicEnvironmentRepository.save(envRecord);

            UUID systemId = YamlEnvironment.composeSystemId(envName, systemName);
            DynamicSystem systemRecord = new DynamicSystem(systemId, envRecord, systemName,
                    connection.getName(), connection.getType(), parametersJson);
            dynamicSystemRepository.save(systemRecord);
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

        UUID projectId = getLazyProjectCatch(projectName).getId();
        LazyEnvironment lazyEnvironment = environmentsService.getLazyEnvironmentByName(projectId, envName);
        UUID envId = lazyEnvironment.getId();

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

        if (StringUtils.isNotBlank(newSystemName)) {
            environmentsService.renameSystemInCache(envId, systemName, newSystemName);
        }
        if (StringUtils.isNotBlank(newEnvName)) {
            environmentsService.renameEnvironmentInCache(envId, newEnvName);
        }

        String finalEnvName = StringUtils.isNotBlank(newEnvName) ? newEnvName : envName;
        String finalSystemName = StringUtils.isNotBlank(newSystemName) ? newSystemName : systemName;

        Optional<DynamicEnvironment> envRecordOpt = dynamicEnvironmentRepository.findByEnvNameAndProjectId(envName, projectId);

        if (StringUtils.isNotBlank(newEnvName)) {
            if (envRecordOpt.isPresent()) {
                DynamicEnvironment envRecord = envRecordOpt.get();
                List<DynamicSystem> systems = dynamicSystemRepository.findAllByEnvId(envRecord.getId());

                // Update env_name on the environment row
                envRecord.setEnvName(finalEnvName);
                dynamicEnvironmentRepository.save(envRecord);
                dynamicEnvironmentRepository.flush();

                // Reinsert each system with recomputed IDs
                for (DynamicSystem sys : systems) {
                    String rowSystemName = sys.getSystemName().equals(systemName) ? finalSystemName : sys.getSystemName();
                    UUID newSystemId = YamlEnvironment.composeSystemId(finalEnvName, rowSystemName);
                    String parametersJson = sys.getSystemName().equals(systemName)
                            ? serializeParameters(connection.getParameters(), finalEnvName)
                            : sys.getConnectionParameters();
                    String resolvedConnectionName = sys.getSystemName().equals(systemName) && connection.getName() != null
                            ? connection.getName() : sys.getConnectionName();
                    String resolvedConnectionType = sys.getSystemName().equals(systemName) && connection.getType() != null
                            ? connection.getType() : sys.getConnectionType();

                    dynamicSystemRepository.delete(sys);
                    dynamicSystemRepository.flush();
                    dynamicSystemRepository.save(new DynamicSystem(
                            newSystemId, envRecord, rowSystemName,
                            resolvedConnectionName, resolvedConnectionType, parametersJson));
                }
                log.info("Cascaded H2 rename for all systems in env [{}] -> [{}].", envName, finalEnvName);

                // Update catalog IDs after rename
                UUID newEnvId = environmentsService.getLazyEnvironmentByName(projectId, finalEnvName).getId();
                for (DynamicSystem sys : systems) {
                    String rowSystemName = sys.getSystemName().equals(systemName) ? finalSystemName : sys.getSystemName();
                    UUID oldSystemId = YamlEnvironment.composeSystemId(envName, sys.getSystemName());
                    UUID newSystemId = YamlEnvironment.composeSystemId(finalEnvName, rowSystemName);
                    catalogRepository.updateSystemAndEnvironmentId(oldSystemId, newSystemId, newEnvId);
                }
                log.info("Cascaded catalog IDs for env rename [{}] -> [{}].", envName, finalEnvName);
            }
        } else {
            // Only connection or system rename on the targeted system row
            if (envRecordOpt.isPresent()) {
                DynamicEnvironment envRecord = envRecordOpt.get();
                Optional<DynamicSystem> sysOpt = dynamicSystemRepository
                        .findByEnvIdAndSystemName(envRecord.getId(), systemName);
                sysOpt.ifPresent(sys -> {
                    String parametersJson = serializeParameters(connection.getParameters(), finalEnvName);
                    String resolvedConnectionName = connection.getName() != null
                            ? connection.getName() : sys.getConnectionName();
                    String resolvedConnectionType = connection.getType() != null
                            ? connection.getType() : sys.getConnectionType();

                    if (StringUtils.isNotBlank(newSystemName)) {
                        UUID oldSystemId = YamlEnvironment.composeSystemId(envName, systemName);
                        UUID newSystemId = YamlEnvironment.composeSystemId(finalEnvName, finalSystemName);
                        dynamicSystemRepository.delete(sys);
                        dynamicSystemRepository.flush();
                        dynamicSystemRepository.save(new DynamicSystem(
                                newSystemId, envRecord, finalSystemName,
                                resolvedConnectionName, resolvedConnectionType, parametersJson));
                        catalogRepository.updateSystemAndEnvironmentId(oldSystemId, newSystemId, envId);
                    } else {
                        sys.setConnectionParameters(parametersJson);
                        sys.setConnectionName(resolvedConnectionName);
                        sys.setConnectionType(resolvedConnectionType);
                        dynamicSystemRepository.save(sys);
                    }
                    log.info("Updated dynamic environment record for [{}].", finalEnvName);
                });
            }
        }

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
            LazyEnvironment lazyEnvironment = environmentsService.getLazyEnvironmentByName(projectId, envName);
            environmentsService.removeSystemFromCache(lazyEnvironment.getId(), systemName);
            log.info("System [{}] deleted from environment [{}] in H2 and cache.", systemName, envName);
        } else {
            LazyEnvironment lazyEnvironment = null;
            try {
                lazyEnvironment = environmentsService.getLazyEnvironmentByName(projectId, envName);
            } catch (TdmEnvConvertLazyEnvironmentByNameException e) {
                // not in cache — fall through to 404 check below
            }
            if (!envRecordOpt.isPresent() && lazyEnvironment == null) {
                throw new EnvironmentNotFoundException(envName, projectName);
            }
            if (lazyEnvironment != null) {
                environmentsService.removeEnvironmentFromCache(lazyEnvironment.getId());
            }
            // Cascade via FK deletes all dynamic_system rows for this env
            dynamicEnvironmentRepository.deleteByEnvNameAndProjectId(envName, projectId);
            log.info("Dynamic environment [{}] deleted from H2 and cache.", envName);
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

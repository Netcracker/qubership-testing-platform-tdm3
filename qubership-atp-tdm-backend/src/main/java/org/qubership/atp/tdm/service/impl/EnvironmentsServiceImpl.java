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

import static java.lang.String.format;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.qubership.atp.tdm.env.configurator.exceptions.internal.TdmEnvConvertFullProjectByIdException;
import org.qubership.atp.tdm.env.configurator.exceptions.internal.TdmEnvConvertFullSystemByNameException;
import org.qubership.atp.tdm.env.configurator.exceptions.internal.TdmEnvConvertFullSystemBySysIdException;
import org.qubership.atp.tdm.env.configurator.exceptions.internal.TdmEnvConvertLazyEnvironmentByEnvIdtException;
import org.qubership.atp.tdm.env.configurator.exceptions.internal.TdmEnvConvertLazyEnvironmentByNameException;
import org.qubership.atp.tdm.env.configurator.exceptions.internal.TdmEnvConvertLazyEnvironmentsException;
import org.qubership.atp.tdm.env.configurator.exceptions.internal.TdmEnvConvertLazyProjectsException;
import org.qubership.atp.tdm.env.configurator.exceptions.internal.TdmEnvConvertLazySystemBySysIdException;
import org.qubership.atp.tdm.env.configurator.exceptions.internal.TdmEnvConvertLazySystemsByEnvIdException;
import org.qubership.atp.tdm.env.configurator.exceptions.internal.TdmEnvConvertLazySystemsByProjectIdException;
import org.qubership.atp.tdm.env.configurator.exceptions.internal.TdmEnvDbConnectionException;
import org.qubership.atp.tdm.env.configurator.model.AbstractConfiguratorModel;
import org.qubership.atp.tdm.env.configurator.model.Connection;
import org.qubership.atp.tdm.env.configurator.model.Environment;
import org.qubership.atp.tdm.env.configurator.model.LazyEnvironment;
import org.qubership.atp.tdm.env.configurator.model.LazyProject;
import org.qubership.atp.tdm.env.configurator.model.LazySystem;
import org.qubership.atp.tdm.env.configurator.model.Project;
import org.qubership.atp.tdm.env.configurator.model.System;
import org.qubership.atp.tdm.env.configurator.service.EnvironmentsService;
import org.qubership.atp.tdm.model.DynamicEnvironment;
import org.qubership.atp.tdm.model.DynamicSystem;
import org.qubership.atp.tdm.repo.DynamicEnvironmentRepository;
import org.qubership.atp.tdm.repo.DynamicSystemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class EnvironmentsServiceImpl implements EnvironmentsService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Value("#{${projects.info}}")
    private Map<UUID, String> projects;

    private final DynamicEnvironmentRepository dynamicEnvironmentRepository;
    private final DynamicSystemRepository dynamicSystemRepository;

    @Autowired
    public EnvironmentsServiceImpl(DynamicEnvironmentRepository dynamicEnvironmentRepository,
                                   DynamicSystemRepository dynamicSystemRepository) {
        this.dynamicEnvironmentRepository = dynamicEnvironmentRepository;
        this.dynamicSystemRepository = dynamicSystemRepository;
    }

    // ── Project methods ───────────────────────────────────────────────────────
    /**
     * Project:
     * Get full project by ID.
     */
    @Override
    public Project getFullProject(@Nonnull UUID projectId) {
        log.info("Loading project by id: [{}]", projectId);
        Project project;
        try {
            project = new Project();
            project.setId(projectId);
            project.setName(projects.get(projectId));

            List<Environment> environments = getLazyEnvironments(projectId).stream()
                    .map(lazyEnvironment -> {
                        List<DynamicSystem> systems = dynamicSystemRepository.findAllByEnvId(lazyEnvironment.getId());
                        List<Connection> connections = systems.stream()
                                .flatMap(s -> buildConnectionsForSystem(s).stream())
                                .collect(Collectors.toList());
                        System system = new System();
                        system.setId(systems.isEmpty() ? null : systems.get(0).getId());
                        system.setConnections(connections);
                        return Environment.of(lazyEnvironment, Collections.singletonList(system));
                    }).collect(Collectors.toList());
            project.setEnvironments(environments);
        } catch (Exception e) {
            log.error(format(TdmEnvConvertFullProjectByIdException.DEFAULT_MESSAGE, projectId), e);
            throw new TdmEnvConvertFullProjectByIdException(projectId.toString());
        }
        log.info("Project successfully loaded.");
        return project;
    }

    /**
     * Get lazy project by ID.
     */
    @Override
    public LazyProject getLazyProjectById(@Nonnull UUID projectId) {
        log.info("Loading lazy project by Id.");
        LazyProject lazyProject = new LazyProject(projectId, projects.get(projectId));
        log.info("Lazy project by Id successfully loaded.");
        return lazyProject;
    }

    /**
     * Get lazy project by name.
     */
    @Override
    public LazyProject getLazyProjectByName(@Nonnull String projectName) {
        log.info("Loading lazy project by name: {}.", projectName);
        for (Map.Entry<UUID, String> entry : projects.entrySet()) {
            if (projectName.equals(entry.getValue())) {
                log.info("Lazy project by name successfully loaded.");
                return new LazyProject(entry.getKey(), entry.getValue());
            }
        }
        throw new IllegalArgumentException("Project [" + projectName + "] not found.");
    }

    @Override
    public List<LazyProject> getLazyProjects() {
        log.info("Loading lazy projects.");
        List<LazyProject> lazyProjects;
        try {
            lazyProjects = projects.entrySet().stream()
                    .map(entry -> new LazyProject(entry.getKey(), entry.getValue()))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error(TdmEnvConvertLazyProjectsException.DEFAULT_MESSAGE, e);
            throw new TdmEnvConvertLazyProjectsException();
        }
        log.info("Lazy projects successfully loaded.");
        return lazyProjects;
    }

    // ── Environment read methods ──────────────────────────────────────────────
    /**
     * Environment:
     * Get lazy environment by ID.
     */
    @Override
    public LazyEnvironment getLazyEnvironment(@Nonnull UUID environmentId) {
        log.info("Loading lazy environment by environment id: [{}]", environmentId);
        DynamicEnvironment env = dynamicEnvironmentRepository.findById(environmentId)
                .orElseThrow(() -> new TdmEnvConvertLazyEnvironmentByEnvIdtException(environmentId.toString()));
        return tolazyEnvironment(env);
    }

    /**
     * Get env name by environment ID.
     */
    @Override
    public String getEnvNameById(@Nonnull UUID environmentId) {
        log.info("Loading environment name by environment id: [{}]", environmentId);
        return dynamicEnvironmentRepository.findById(environmentId)
                .map(DynamicEnvironment::getEnvName)
                .orElse(null);
    }

    /**
     * Get lazy environments by project ID.
     */
    @Override
    public List<LazyEnvironment> getLazyEnvironments(@Nonnull UUID projectId) {
        log.info("Loading lazy environments by project id: [{}]", projectId);
        try {
            return dynamicEnvironmentRepository.findAllByProjectId(projectId).stream()
                    .map(this::tolazyEnvironmentWithSystems)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error(format(TdmEnvConvertLazyEnvironmentsException.DEFAULT_MESSAGE, projectId), e);
            throw new TdmEnvConvertLazyEnvironmentsException(projectId.toString());
        }
    }

    /**
     * Get lazy environments by project ID - from cache only.
     */
    @Override
    public List<LazyEnvironment> getLazyEnvironmentsFromCache(@Nonnull UUID projectId) {
        return getLazyEnvironments(projectId);
    }

    /**
     * Environments are now sourced exclusively from H2-persisted DynamicEnvironment records (loaded on
     * startup). There is no Git backend to refresh from, so this returns the current cached list.
     */
    @Override
    public List<LazyEnvironment> getLazyEnvironmentsRefresh(@Nonnull UUID projectId) {
        log.info("getLazyEnvironmentsRefresh called for project [{}] - reading from H2.", projectId);
        return getLazyEnvironments(projectId);
    }

    /**
     * Get lazy environment by project and environment name.
     */
    @Override
    public LazyEnvironment getLazyEnvironmentByName(@Nonnull UUID projectId, @Nonnull String environmentName) {
        log.info("Loading lazy environment by project [{}] and name [{}].", projectId, environmentName);
        return dynamicEnvironmentRepository.findByEnvNameAndProjectId(environmentName, projectId)
                .map(this::tolazyEnvironmentWithSystems)
                .orElseThrow(() -> new TdmEnvConvertLazyEnvironmentByNameException(environmentName, projectId.toString()));
    }

    // ── System read methods ───────────────────────────────────────────────────

    /**
     * Get connections by system ID.
     */
    @Override
    public List<Connection> getConnectionsSystemById(UUID environmentId, UUID systemId) {
        log.info("Loading connections by system ID: {}", systemId);
        try {
            Optional<DynamicSystem> systemOpt = dynamicSystemRepository.findById(systemId);
            if (!systemOpt.isPresent() && environmentId != null) {
                systemOpt = dynamicSystemRepository.findById(systemId);
            }
            if (!systemOpt.isPresent()) {
                systemOpt = dynamicSystemRepository.findAllByEnvId(
                        environmentId != null ? environmentId : UUID.randomUUID())
                        .stream().filter(s -> s.getId().equals(systemId)).findFirst();
            }
            if (systemOpt.isPresent()) {
                return buildConnectionsForSystem(systemOpt.get());
            }
            return new ArrayList<>();
        } catch (Exception e) {
            log.error(format(TdmEnvConvertFullSystemBySysIdException.DEFAULT_MESSAGE, systemId), e);
            throw new TdmEnvConvertFullSystemBySysIdException(systemId.toString());
        }
    }

    /**
     * Get lazy system by ID.
     */
    @Override
    public LazySystem getLazySystemById(@Nonnull UUID environmentId, @Nonnull UUID systemId) {
        log.info("Loading lazy system by system ID: {}", systemId);
        DynamicSystem sys = dynamicSystemRepository.findById(systemId)
                .orElseThrow(() -> new TdmEnvConvertLazySystemBySysIdException(systemId.toString()));
        return toLazySystem(sys);
    }

    /**
     * Get lazy system by project ID, environment ID, name.
     */
    @Override
    public LazySystem getLazySystemByName(@Nonnull UUID projectId, @Nonnull UUID environmentId,
                                          @Nonnull String systemName) {
        log.info("Loading lazy system for env [{}] by name [{}].", environmentId, systemName);
        DynamicSystem sys = dynamicSystemRepository.findByEnvIdAndSystemName(environmentId, systemName)
                .orElseThrow(() -> new TdmEnvConvertFullSystemByNameException(systemName));
        return toLazySystem(sys);
    }

    /**
     * Get lazy systems by env Id.
     * @param environmentId ATP projectId
     * @return list of LazySystem's
     */
    @Override
    public List<LazySystem> getLazySystems(@Nonnull UUID environmentId) {
        log.info("Loading lazy systems by env ID: [{}].", environmentId);
        try {
            return dynamicSystemRepository.findAllByEnvId(environmentId).stream()
                    .map(this::toLazySystem)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error(format(TdmEnvConvertLazySystemsByEnvIdException.DEFAULT_MESSAGE, environmentId), e);
            throw new TdmEnvConvertLazySystemsByEnvIdException(environmentId);
        }
    }

    @Override
    public List<LazySystem> getLazySystemsByProjectWithEnvIds(@Nonnull UUID projectId) {
        log.info("Loading lazy systems by OO project ID: [{}]", projectId);
        try {
            Map<UUID, LazySystem.LazySystemBuilder> builders = new HashMap<>();
            for (DynamicEnvironment env : dynamicEnvironmentRepository.findAllByProjectId(projectId)) {
                for (DynamicSystem sys : dynamicSystemRepository.findAllByEnvId(env.getId())) {
                    LazySystem.LazySystemBuilder builder = builders.computeIfAbsent(
                            sys.getId(),
                            id -> LazySystem.builder()
                                    .id(sys.getId())
                                    .name(sys.getSystemName())
                                    .connections(getListConnections(sys))
                                    .environmentIds(new ArrayList<>())
                    );
                    List<UUID> envIds = new ArrayList<>(builder.build().getEnvironmentIds());
                    envIds.add(env.getId());
                    builder.environmentIds(envIds);
                }
            }
            return builders.values().stream()
                    .map(LazySystem.LazySystemBuilder::build)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error(format(TdmEnvConvertLazySystemsByProjectIdException.DEFAULT_MESSAGE, projectId), e);
            throw new TdmEnvConvertLazySystemsByProjectIdException(projectId.toString());
        }
    }

    /**
     * Get all systems from cache by project id.
     * @param projectId ATP projectId
     * @return list of LazySystem's
     */
    @Override
    public List<LazySystem> getLazySystemsByProjectIdWithConnections(@Nonnull UUID projectId) {
        log.info("Loading lazy systems by project ID: [{}]", projectId);
        try {
            return dynamicEnvironmentRepository.findAllByProjectId(projectId).stream()
                    .flatMap(env -> dynamicSystemRepository.findAllByEnvId(env.getId()).stream()
                            .map(this::toLazySystem))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error(format(TdmEnvConvertLazySystemsByProjectIdException.DEFAULT_MESSAGE, projectId), e);
            throw new TdmEnvConvertLazySystemsByProjectIdException(projectId.toString());
        }
    }

    @Override
    public System getFullSystemByName(@Nonnull UUID environmentId, @Nonnull String systemName) {
        log.info("Loading full system by name [{}] for environment id [{}].", systemName, environmentId);
        DynamicSystem sys = dynamicSystemRepository.findByEnvIdAndSystemName(environmentId, systemName)
                .orElseThrow(() -> new TdmEnvConvertFullSystemByNameException(systemName));

        List<Connection> connections = buildConnectionsForSystem(sys);
        boolean hasDbConnection = connections.stream()
                .anyMatch(c -> "DB".equalsIgnoreCase(c.getConnectionType()));
        if (!hasDbConnection) {
            log.error("No connection named DB under system [{}] for environment id [{}].", systemName, environmentId);
            throw new TdmEnvDbConnectionException("DB");
        }

        System system = System.builder()
                .environmentId(environmentId)
                .connections(connections)
                .build();
        system.setId(sys.getId());
        system.setName(systemName.toLowerCase());
        return system;
    }

    @Override
    public boolean resetCaches() {
        log.info("resetCaches called — no cache layer; no-op.");
        return true;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private LazyEnvironment tolazyEnvironment(DynamicEnvironment env) {
        return LazyEnvironment.builder()
                .id(env.getId())
                .name(env.getEnvName())
                .projectId(env.getProjectId())
                .build();
    }

    private LazyEnvironment tolazyEnvironmentWithSystems(DynamicEnvironment env) {
        List<String> systemIds = dynamicSystemRepository.findAllByEnvId(env.getId()).stream()
                .map(s -> s.getId().toString())
                .collect(Collectors.toList());
        return LazyEnvironment.builder()
                .id(env.getId())
                .name(env.getEnvName())
                .projectId(env.getProjectId())
                .systems(systemIds)
                .build();
    }

    private LazySystem toLazySystem(DynamicSystem sys) {
        return LazySystem.builder()
                .id(sys.getId())
                .name(sys.getSystemName())
                .connections(getListConnections(sys))
                .build();
    }

    private List<Connection> buildConnectionsForSystem(DynamicSystem sys) {
        Map<String, String> parameters = deserializeParameters(sys.getConnectionParameters());
        Connection connection = new Connection();
        connection.setId(sys.getId());
        connection.setName(sys.getConnectionName());
        connection.setSystemId(sys.getId());
        connection.setConnectionType(sys.getConnectionType());
        connection.setParameters(parameters);
        return Collections.singletonList(connection);
    }

    private List<Connection> buildConnectionsForEnv(UUID envId) {
        return dynamicSystemRepository.findAllByEnvId(envId).stream()
                .flatMap(sys -> buildConnectionsForSystem(sys).stream())
                .collect(Collectors.toList());
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

    private String serializeParameters(Map<String, String> parameters) {
        try {
            return OBJECT_MAPPER.writeValueAsString(parameters);
        } catch (Exception e) {
            log.warn("Failed to serialize connection parameters.", e);
            return "{}";
        }
    }

    public List<String> getListConnections(DynamicSystem sys) {
        return buildConnectionsForSystem(sys).stream()
                            .map(AbstractConfiguratorModel::getId)
                            .map(UUID::toString)
                            .collect(Collectors.toList());
    }
}

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

package org.qubership.atp.tdm.env.configurator.service.impl;

import static java.lang.String.format;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.qubership.atp.auth.springbootstarter.exceptions.AtpException;
import org.qubership.atp.tdm.env.configurator.exceptions.internal.TdmEnvConvertFullProjectByIdException;
import org.qubership.atp.tdm.env.configurator.exceptions.internal.TdmEnvConvertFullSystemByNameException;
import org.qubership.atp.tdm.env.configurator.exceptions.internal.TdmEnvConvertFullSystemBySysIdException;
import org.qubership.atp.tdm.env.configurator.exceptions.internal.TdmEnvConvertLazyEnvironmentByEnvIdtException;
import org.qubership.atp.tdm.env.configurator.exceptions.internal.TdmEnvConvertLazyEnvironmentByNameException;
import org.qubership.atp.tdm.env.configurator.exceptions.internal.TdmEnvConvertLazyEnvironmentsException;
import org.qubership.atp.tdm.env.configurator.exceptions.internal.TdmEnvConvertLazyProjectsException;
import org.qubership.atp.tdm.env.configurator.exceptions.internal.TdmEnvConvertLazySystemBySysIdException;
import org.qubership.atp.tdm.env.configurator.exceptions.internal.TdmEnvConvertLazySystemsByEnvIdByNameException;
import org.qubership.atp.tdm.env.configurator.exceptions.internal.TdmEnvConvertLazySystemsByEnvIdException;
import org.qubership.atp.tdm.env.configurator.exceptions.internal.TdmEnvConvertLazySystemsByProjectIdException;
import org.qubership.atp.tdm.env.configurator.exceptions.internal.TdmEnvResetCachesException;
import org.qubership.atp.tdm.env.configurator.exceptions.internal.TdmEnvDbConnectionException;
import org.qubership.atp.tdm.env.configurator.model.Connection;
import org.qubership.atp.tdm.env.configurator.model.Environment;
import org.qubership.atp.tdm.env.configurator.model.LazyEnvironment;
import org.qubership.atp.tdm.env.configurator.model.LazyProject;
import org.qubership.atp.tdm.env.configurator.model.LazySystem;
import org.qubership.atp.tdm.env.configurator.model.Project;
import org.qubership.atp.tdm.env.configurator.model.System;
import org.qubership.atp.tdm.env.configurator.model.envgen.ConnectionType;
import org.qubership.atp.tdm.env.configurator.model.envgen.YamlConnection;
import org.qubership.atp.tdm.env.configurator.model.envgen.YamlEnvironment;
import org.qubership.atp.tdm.env.configurator.model.envgen.YamlSystem;
import org.qubership.atp.tdm.env.configurator.service.CacheService;
import org.qubership.atp.tdm.env.configurator.service.EnvironmentsService;
import org.qubership.atp.tdm.env.configurator.utils.CacheNames;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class EnvironmentsServiceImpl implements EnvironmentsService {

    @Value("#{${projects.info}}")
    private Map<UUID, String> projects;

    private final CacheService cacheService;
    private final CacheManager cacheManager;

    public EnvironmentsServiceImpl(CacheService cacheService, CacheManager cacheManager) {
        this.cacheService = cacheService;
        this.cacheManager = cacheManager;
    }

    /**
     * Project:
     * Get full project by ID.
     */
    @Override
    @Cacheable(value = CacheNames.TDM_FULL_PROJECT_CACHE)
    public Project getFullProject(@Nonnull UUID projectId) {
        log.info("Loading project by id: [{}]", projectId);
        Project project;
        try {
            project = new Project();
            project.setId(projectId);
            project.setName(projects.get(projectId));

            List<Environment> environments = getLazyEnvironmentsFromCache(projectId).stream()
                    .map(lazyEnvironment -> {
                        YamlEnvironment yamlEnv = cacheService.get(lazyEnvironment.getId());
                        List<Connection> connections = new ArrayList<>();
                        if (yamlEnv != null) {
                            for (YamlSystem yamlSystem : yamlEnv.getYamlSystems()) {
                                for (YamlConnection yamlConn : yamlSystem.getConnections()) {
                                    Connection conn = new Connection();
                                    conn.setId(yamlConn.getId());
                                    conn.setName(yamlConn.getName());
                                    conn.setSystemId(yamlSystem.getId());
                                    conn.setConnectionType(yamlConn.getType().toString());
                                    conn.setParameters(yamlConn.getParameters());
                                    connections.add(conn);
                                }
                            }
                        }
                        System system = new System();
                        system.setId(yamlEnv != null && !yamlEnv.getYamlSystems().isEmpty()
                                ? yamlEnv.getYamlSystems().get(0).getId() : null);
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
    @Cacheable(value = CacheNames.TDM_LAZY_PROJECT_CACHE)
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
    @Cacheable(value = CacheNames.TDM_LAZY_PROJECT_BY_NAME_CACHE)
    public LazyProject getLazyProjectByName(@Nonnull String projectName) {
        log.info("Loading lazy project by name: {}.", projectName);
        LazyProject lazyProject = null;
        for (Map.Entry<UUID, String> entry : projects.entrySet()) {
            if (projectName.equals(entry.getValue())) {
                lazyProject = new LazyProject(entry.getKey(), entry.getValue());
                break;
            }
        }
        log.info("Lazy project by name successfully loaded.");
        return lazyProject;
    }

    /**
     * Get lazy projects.
     */
    @Override
    @Cacheable(value = CacheNames.TDM_LAZY_PROJECTS_CACHE)
    public List<LazyProject> getLazyProjects() {
        log.info("Loading lazy projects.");
        List<LazyProject> lazyProjects;
        try {
            lazyProjects = projects.entrySet()
                    .stream()
                    .map(entry -> new LazyProject(entry.getKey(), entry.getValue()))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error(TdmEnvConvertLazyProjectsException.DEFAULT_MESSAGE, e);
            throw new TdmEnvConvertLazyProjectsException();
        }
        log.info("Lazy projects successfully loaded.");
        return lazyProjects;
    }

    /**
     * Environment:
     * Get lazy environment by ID.
     */
    @Override
    @Cacheable(value = CacheNames.TDM_LAZY_ENVIRONMENT_BY_ID_CACHE)
    public LazyEnvironment getLazyEnvironment(@Nonnull UUID environmentId) {
        log.info("Loading lazy environment by environment id: [{}]", environmentId);
        LazyEnvironment environment;
        try {
            YamlEnvironment yamlEnvironment = cacheService.get(environmentId);
            if (yamlEnvironment == null) {
                log.warn("Environment not found in cache for ID: {}", environmentId);
                return null;
            }
            environment = LazyEnvironment.builder()
                    .id(yamlEnvironment.getId())
                    .name(yamlEnvironment.getName())
                    .clusterName(yamlEnvironment.getClusterName())
                    .projectId(yamlEnvironment.getProjectId())
                    .build();
        } catch (Exception e) {
            log.error(format(TdmEnvConvertLazyEnvironmentByEnvIdtException.DEFAULT_MESSAGE,
                    environmentId), e);
            throw new TdmEnvConvertLazyEnvironmentByEnvIdtException(environmentId.toString());
        }
        log.info("Lazy environment successfully loaded.");
        return environment;
    }

    /**
     * Get env name by environment ID.
     */
    @Override
    @Cacheable(value = CacheNames.TDM_ENV_NAME_BY_ENVIRONMENT_ID_CACHE)
    public String getEnvNameById(@Nonnull UUID environmentId) {
        log.info("Loading environment name by environment id: [{}]", environmentId);
        YamlEnvironment yamlEnvironment = cacheService.get(environmentId);
        return yamlEnvironment != null ? yamlEnvironment.getName() : null;
    }

    /**
     * Get lazy environments by project ID.
     */
    @Override
    public List<LazyEnvironment> getLazyEnvironments(@Nonnull UUID projectId) {
        log.info("Loading lazy environments by project id: [{}]", projectId);
        List<LazyEnvironment> lazyEnvironments;
        try {
            lazyEnvironments = getLazyEnvironmentsFromCache(projectId);
        } catch (Exception e) {
            log.error(format(TdmEnvConvertLazyEnvironmentsException.DEFAULT_MESSAGE, projectId), e);
            throw new TdmEnvConvertLazyEnvironmentsException(projectId.toString());
        }
        log.info("Lazy environments successfully loaded.");
        return lazyEnvironments;
    }

    /**
     * Get lazy environments by project ID - from cache only.
     */
    @Override
    public List<LazyEnvironment> getLazyEnvironmentsFromCache(@Nonnull UUID projectId) {
        log.info("Getting lazy environments from cache by project id: [{}]", projectId);
        List<LazyEnvironment> cachedEnvironments = new ArrayList<>();

        for (YamlEnvironment yamlEnv : cacheService.getEnvironments()) {
            if (yamlEnv.getProjectId() != null && yamlEnv.getProjectId().equals(projectId)) {
                LazyEnvironment lazyEnv = LazyEnvironment.builder()
                        .id(yamlEnv.getId())
                        .name(yamlEnv.getName())
                        .clusterName(yamlEnv.getClusterName())
                        .projectId(projectId)
                        .systems(yamlEnv.getYamlSystems() != null
                                ? yamlEnv.getYamlSystems().stream()
                                .map(system -> UUID.nameUUIDFromBytes(String.format("%s/%s",
                                        yamlEnv.getName(), system.getName()).getBytes()).toString())
                                .collect(Collectors.toList()) : new ArrayList<>())
                        .build();
                cachedEnvironments.add(lazyEnv);
            }
        }

        log.info("Retrieved {} cached environments for project: {}", cachedEnvironments.size(), projectId);
        return cachedEnvironments;
    }

    /**
     * Environments are now sourced exclusively from H2-persisted DynamicEnvironment records (loaded on
     * startup). There is no Git backend to refresh from, so this returns the current cached list.
     */
    @Override
    public List<LazyEnvironment> getLazyEnvironmentsRefresh(@Nonnull UUID projectId) {
        log.info("getLazyEnvironmentsRefresh called for project [{}] - returning current cache (no Git backend).",
                projectId);
        return getLazyEnvironmentsFromCache(projectId);
    }

    /**
     * Get lazy environment by project and environment name.
     */
    @Override
    @Cacheable(value = CacheNames.TDM_LAZY_ENVIRONMENT_BY_NAME_CACHE)
    public LazyEnvironment getLazyEnvironmentByName(@Nonnull UUID projectId, @Nonnull String environmentName) {
        LazyEnvironment lazyEnvironment;
        try {
            YamlEnvironment yamlEnvironment = cacheService.get(UUID.nameUUIDFromBytes(environmentName.getBytes()));
            lazyEnvironment = LazyEnvironment.builder()
                    .id(yamlEnvironment.getId())
                    .name(yamlEnvironment.getName())
                    .clusterName(yamlEnvironment.getClusterName())
                    .projectId(yamlEnvironment.getProjectId())
                    .build();
        } catch (Exception e) {
            log.error(format(TdmEnvConvertLazyEnvironmentByNameException.DEFAULT_MESSAGE,
                    environmentName, projectId), e);
            throw new TdmEnvConvertLazyEnvironmentByNameException(environmentName, projectId.toString());
        }
        return lazyEnvironment;
    }

    /**
     * Get connections by system ID.
     */
    @Override
    @Cacheable(value = CacheNames.TDM_CONNECTIONS_BY_SYSTEM_ID_CACHE)
    public List<Connection> getConnectionsSystemById(UUID environmentId, UUID systemId) {
        log.info("Loading connections by system ID: {}", systemId);
        List<Connection> connections;
        try {
            YamlSystem yamlSystem = null;
            if (environmentId != null) {
                YamlEnvironment yamlEnvironment = cacheService.get(environmentId);
                if (yamlEnvironment != null) {
                    yamlSystem = yamlEnvironment.getSystemById(systemId);
                }
            }
            if (yamlSystem == null) {
                Optional<YamlEnvironment> systemEnv = cacheService.getEnvironments().stream()
                        .filter(yamlEnvironment -> yamlEnvironment.getSystemById(systemId) != null)
                        .findAny();
                if (systemEnv.isPresent()) {
                    yamlSystem = systemEnv.get().getSystemById(systemId);
                }
            }
            if (yamlSystem != null) {
                YamlSystem finalYamlSystem = yamlSystem;
                connections = yamlSystem.getConnections().stream().map(yamlConnection -> {
                    Connection connection = new Connection();
                    connection.setId(yamlConnection.getId());
                    connection.setName(yamlConnection.getName());
                    connection.setSystemId(finalYamlSystem.getId());
                    connection.setConnectionType(yamlConnection.getType().toString());
                    connection.setParameters(yamlConnection.getParameters());
                    return connection;
                }).collect(Collectors.toList());
            } else {
                connections = new ArrayList<>();
            }
        } catch (Exception e) {
            log.error(format(TdmEnvConvertFullSystemBySysIdException.DEFAULT_MESSAGE, systemId), e);
            throw new TdmEnvConvertFullSystemBySysIdException(systemId.toString());
        }
        log.info("Full systems by system ID successfully loaded.");
        return connections;
    }

    /**
     * Get lazy system by ID.
     */
    @Override
    @Cacheable(value = CacheNames.TDM_LAZY_SYSTEM_CACHE)
    public LazySystem getLazySystemById(@Nonnull UUID environmentId, @Nonnull UUID systemId) {
        log.info("Loading lazy system by system ID: {}", systemId);
        LazySystem lazySystem;
        try {
            YamlEnvironment yamlEnvironment = cacheService.get(environmentId);
            YamlSystem yamlSystem = yamlEnvironment.getSystemById(systemId);
            lazySystem = LazySystem.builder()
                    .id(yamlSystem.getId())
                    .name(yamlSystem.getName())
                    .connections(yamlSystem.getListConnections())
                    .build();
        } catch (Exception e) {
            log.error(format(TdmEnvConvertLazySystemBySysIdException.DEFAULT_MESSAGE, systemId), e);
            throw new TdmEnvConvertLazySystemBySysIdException(systemId.toString());
        }
        log.info("Lazy systems by system ID successfully loaded.");
        return lazySystem;
    }

    /**
     * Get lazy system by project ID, environment ID, name.
     */
    @Override
    @Cacheable(value = CacheNames.TDM_LAZY_SYSTEM_BY_NAME_CACHE)
    public LazySystem getLazySystemByName(@Nonnull UUID projectId, @Nonnull UUID environmentId,
                                          @Nonnull String systemName) {
        log.info("Loading lazy systems for project id: [{}] by environment id: [{}] and systemName: [{}]", projectId,
                environmentId, systemName);
        LazySystem lazySystem;
        try {
            YamlEnvironment yamlEnvironment = cacheService.get(environmentId);
            YamlSystem yamlSystem = yamlEnvironment.getSystemByName(systemName);
            lazySystem = LazySystem.builder()
                    .id(yamlSystem.getId())
                    .name(yamlSystem.getName())
                    .connections(yamlSystem.getListConnections())
                    .build();
        } catch (Exception e) {
            log.error(format(TdmEnvConvertFullSystemByNameException.DEFAULT_MESSAGE, systemName), e);
            throw new TdmEnvConvertFullSystemByNameException(systemName);
        }
        log.info("Full systems by name successfully loaded.");
        return lazySystem;
    }

    /**
     * Get lazy systems by env Id.
     * @param environmentId ATP projectId
     * @return list of LazySystem's
     */
    @Override
    @Cacheable(value = CacheNames.TDM_LAZY_SYSTEMS_CACHE)
    public List<LazySystem> getLazySystems(@Nonnull UUID environmentId) {
        log.info("Loading lazy systems by env ID: [{}].", environmentId);
        List<LazySystem> systems;
        try {
            YamlEnvironment yamlEnvironment = cacheService.get(environmentId);
            if (yamlEnvironment == null) {
                log.warn("Environment not found in cache for ID: {}", environmentId);
                return new ArrayList<>();
            }
            systems = yamlEnvironment.getYamlSystems().stream()
                    .map(yamlSystem -> LazySystem.builder()
                            .id(yamlSystem.getId())
                            .name(yamlSystem.getName())
                            .connections(yamlSystem.getListConnections())
                            .build())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error(format(TdmEnvConvertLazySystemsByEnvIdByNameException.DEFAULT_MESSAGE, environmentId), e);
            throw new TdmEnvConvertLazySystemsByEnvIdException(environmentId);
        }
        log.info("Lazy systems by envId and name successfully loaded.");
        return systems;
    }

    @Override
    @Cacheable(value = CacheNames.TDM_ALL_SHORT_LAZY_SYSTEMS_BY_PROJECT_CACHE)
    public List<LazySystem> getLazySystemsByProjectWithEnvIds(@Nonnull UUID projectId) {
        log.info("Loading lazy systems by project ID: [{}]", projectId);
        List<LazySystem> lazySystems;
        try {
            Map<UUID, LazySystem.LazySystemBuilder> systemBuilders = new HashMap<>();
            for (YamlEnvironment yamlEnvironment : cacheService.getEnvironments()) {
                for (YamlSystem yamlSystem : yamlEnvironment.getYamlSystems()) {
                    LazySystem.LazySystemBuilder builder = systemBuilders.computeIfAbsent(
                            yamlSystem.getId(),
                            id -> LazySystem.builder()
                                    .id(yamlSystem.getId())
                                    .name(yamlSystem.getName())
                                    .connections(yamlSystem.getListConnections())
                                    .environmentIds(new ArrayList<>())
                    );
                    List<UUID> envIds = new ArrayList<>(builder.build().getEnvironmentIds());
                    envIds.add(yamlEnvironment.getId());
                    builder.environmentIds(envIds);
                }
            }
            lazySystems = systemBuilders.values().stream()
                    .map(LazySystem.LazySystemBuilder::build)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error(format(TdmEnvConvertLazySystemsByProjectIdException.DEFAULT_MESSAGE, projectId), e);
            throw new TdmEnvConvertLazySystemsByProjectIdException(projectId.toString());
        }
        log.info("Lazy systems by project ID successfully loaded.");
        return lazySystems;
    }

    /**
     * Get all systems from cache by project id.
     * @param projectId ATP projectId
     * @return list of LazySystem's
     */
    @Override
    @Cacheable(value = CacheNames.TDM_LAZY_SYSTEMS_BY_PROJECT_CACHE)
    public List<LazySystem> getLazySystemsByProjectIdWithConnections(@Nonnull UUID projectId) {
        log.info("Loading lazy systems by project ID: [{}]", projectId);
        List<LazySystem> systems;
        try {
            systems = cacheService.getEnvironments().stream()
                    .flatMap(yamlEnvironment -> yamlEnvironment.getYamlSystems().stream()
                            .map(yamlSystem -> LazySystem.builder()
                                    .id(yamlSystem.getId())
                                    .name(yamlSystem.getName())
                                    .connections(yamlSystem.getListConnections())
                                    .build()))
                    .collect(Collectors.toList());
        } catch (AtpException ae) {
            throw ae;
        } catch (Exception e) {
            log.error(format(TdmEnvConvertLazySystemsByProjectIdException.DEFAULT_MESSAGE, projectId), e);
            throw new TdmEnvConvertLazySystemsByProjectIdException(projectId.toString());
        }
        log.info("Lazy systems by project ID successfully loaded");
        return systems;
    }

    @Override
    public System getFullSystemByName(@Nonnull UUID environmentId, @Nonnull String systemName) {
        log.info("Loading full system by name [{}] for environment id [{}].", systemName, environmentId);
        YamlEnvironment yamlEnvironment = cacheService.get(environmentId);
        YamlSystem yamlSystem = yamlEnvironment.getSystemByName(systemName);

        List<Connection> connections = yamlSystem.getConnections().stream().map(yamlConnection -> {
            Connection connection = new Connection();
            connection.setId(yamlConnection.getId());
            connection.setName(yamlConnection.getName());
            connection.setSystemId(yamlSystem.getId());
            connection.setConnectionType(yamlConnection.getType().toString());
            connection.setParameters(yamlConnection.getParameters());
            return connection;
        }).collect(Collectors.toList());

        boolean hasDbConnection = connections.stream()
                .anyMatch(connection -> "DB".equalsIgnoreCase(connection.getName()));
        if (!hasDbConnection) {
            throw new TdmEnvDbConnectionException("DB");
        }

        System system = System.builder()
                .environmentId(environmentId)
                .connections(connections).build();
        system.setId(yamlSystem.getId());
        system.setName(systemName);
        return system;
    }

    @Override
    public LazyEnvironment registerEnvironmentInCache(@Nonnull UUID projectId, @Nonnull String envName,
                                                      @Nonnull String systemName, @Nonnull String connectionName,
                                                      @Nonnull String connectionType,
                                                      @Nonnull Map<String, String> parameters) {
        log.info("Registering dynamic environment in cache. Env: [{}], System: [{}]", envName, systemName);

        YamlConnection yamlConnection = new YamlConnection();
        yamlConnection.setName(connectionName);
        yamlConnection.setType(ConnectionType.fromValue(connectionType));
        yamlConnection.setParameters(parameters);

        YamlSystem yamlSystem = new YamlSystem();
        yamlSystem.setName(systemName);
        yamlSystem.setProjectId(projectId);
        yamlSystem.setConnections(Collections.singletonList(yamlConnection));

        YamlEnvironment yamlEnvironment = new YamlEnvironment(envName);
        yamlEnvironment.setProjectId(projectId);
        yamlEnvironment.setYamlSystems(Collections.singletonList(yamlSystem));

        cacheService.put(yamlEnvironment);
        log.info("Dynamic environment [{}] registered in cache with id [{}].", envName, yamlEnvironment.getId());

        return LazyEnvironment.builder()
                .id(yamlEnvironment.getId())
                .name(yamlEnvironment.getName())
                .projectId(projectId)
                .systems(Collections.singletonList(
                        yamlEnvironment.getYamlSystems().get(0).getId().toString()))
                .build();
    }

    @Override
    public boolean resetCaches() {
        log.info("Reset caches.");
        try {
            Field[] fields = CacheNames.class.getDeclaredFields();
            for (Field field : fields) {
                Cache cache = cacheManager.getCache(field.get(String.class).toString());
                if (Objects.nonNull(cache)) {
                    cache.clear();
                }
            }
        } catch (Exception e) {
            log.error(TdmEnvResetCachesException.DEFAULT_MESSAGE, e);
            throw new TdmEnvResetCachesException();
        }
        log.info("Environment caches have been cleared.");
        return true;
    }
}

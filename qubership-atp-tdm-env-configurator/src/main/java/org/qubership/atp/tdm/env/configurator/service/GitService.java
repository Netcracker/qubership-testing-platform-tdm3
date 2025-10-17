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

package org.qubership.atp.tdm.env.configurator.service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.models.RepositoryFile;
import org.qubership.atp.tdm.env.configurator.model.Connection;
import org.qubership.atp.tdm.env.configurator.model.Environment;
import org.qubership.atp.tdm.env.configurator.model.LazyEnvironment;
import org.qubership.atp.tdm.env.configurator.model.LazyProject;
import org.qubership.atp.tdm.env.configurator.model.LazySystem;
import org.qubership.atp.tdm.env.configurator.model.Project;
import org.qubership.atp.tdm.env.configurator.model.System;
import org.qubership.atp.tdm.env.configurator.model.envgen.Configuration;
import org.qubership.atp.tdm.env.configurator.model.envgen.EnvGenProperty;
import org.qubership.atp.tdm.env.configurator.model.envgen.YamlConfiguration;
import org.qubership.atp.tdm.env.configurator.model.envgen.YamlEnvironment;
import org.qubership.atp.tdm.env.configurator.model.envgen.YamlSystem;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class GitService {

    @Value("${git.url}")
    private String gitUrl;

    @Value("${git.token}")
    private String gitToken;

    @Value("${git.environments.ref}")
    private String ref;

    @Value("${git.environments.project.path}")
    private String pathToGitProject;

    @Value("${git.environments.topology.parameters.path}")
    private String pathToFileTopologyParameters;

    @Value("${git.environments.parameters.path}")
    private String pathToFileParameters;

    @Value("${git.environments.credentials.path}")
    private String pathToFileCredentials;

    @Value("#{${projects.info}}")
    private Map<UUID, String> projects;

    private CacheService cacheService;
    private ObjectMapper enfConfObjectMapper;

    {
        enfConfObjectMapper = new YAMLMapper();
        enfConfObjectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        enfConfObjectMapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
    }

    @Autowired
    public GitService(CacheService cacheService) {
        this.cacheService = cacheService;
    }

    public LazyProject getLazyProjectByName(String projectName) {
        LazyProject lazyProject = null;
        for (Map.Entry<UUID, String> entry : projects.entrySet()) {
            if (projectName.equals(entry.getValue())) {
                lazyProject = new LazyProject(entry.getKey(), entry.getValue());
                break;
            }
        }
        return lazyProject;
    }

    public Project getFullProject(UUID projectId) throws Exception {
        Project project = new Project();
        project.setId(projectId);
        project.setName(projects.get(projectId));

        List<Environment> environments = getLazyEnvironments(projectId).stream()
                .map(lazyEnvironment -> {
                    try {
                        return Environment.of(lazyEnvironment, getFullSystems(lazyEnvironment));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }).collect(Collectors.toList());
        project.setEnvironments(environments);

        return project;
    }

    public LazyProject getLazyProjectById(UUID projectId) {
        return new LazyProject(projectId, projects.get(projectId));
    }

    public List<LazyProject> getLazyProjects() {
        return projects.entrySet()
                .stream()
                .map(entry -> new LazyProject(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
    }

    public String getEnvNameById(UUID environmentId) {
        return cacheService.get(environmentId).getName();
    }

    public List<LazyEnvironment> getLazyEnvironments(UUID projectId) throws Exception {
        String endpoint = gitEndpointToGetTopologyParametersFile();
        RepositoryFile file = getGitFile(endpoint);
        File tempFile = performRepositoryFile(file);
        Map<String, Object> environments =
                checkEnvironmentConfiguration(enfConfObjectMapper.readValue(tempFile, Map.class), endpoint);

        List<LazyEnvironment> lazyEnvironments = new ArrayList<>();
        for (Map.Entry<String, Object> entry : environments.entrySet()) {

            String[] fullEnvName = entry.getKey().split("/");
            LazyEnvironment lazyEnvironment = LazyEnvironment.builder()
                    .id(UUID.nameUUIDFromBytes(fullEnvName[1].getBytes()))
                    .name(fullEnvName[1])
                    .clusterName(fullEnvName[0])
                    .projectId(projectId)
                    .systems(getSystems(fullEnvName[0], fullEnvName[1]))
                    .build();
            lazyEnvironments.add(lazyEnvironment);

            YamlEnvironment yamlEnvironment = new YamlEnvironment(fullEnvName[1]);
            yamlEnvironment.setClusterName(fullEnvName[0]);
            yamlEnvironment.setProjectId(projectId);
            yamlEnvironment.setParameters(entry.getValue());
            cacheService.put(yamlEnvironment);
        }
        return lazyEnvironments;
    }

    private List<String> getSystems(String clusterName, String name) throws Exception {
        String endpoint = gitEndpointToGetParametersFile(clusterName, name);
        Configuration configuration = getYamlConfiguration(endpoint).getConfiguration();
        return configuration.getSystems()
                .stream()
                .map(system -> {
                    return UUID.nameUUIDFromBytes(String.format("%s/%s", name, system.getName()).getBytes()).toString();
                }).collect(Collectors.toList());
    }

    public List<LazySystem> getLazySystems(UUID environmentId) throws Exception {
        YamlEnvironment yamlEnvironment = cacheService.get(environmentId);
        String environmentName = yamlEnvironment.getName();

        String endpoint = gitEndpointToGetParametersFile(yamlEnvironment.getClusterName(), environmentName);
        Configuration configuration = getYamlConfiguration(endpoint).getConfiguration();

        endpoint = gitEndpointToGetCredentialsFile(yamlEnvironment.getClusterName(), environmentName);
        Configuration credentials = getYamlConfiguration(endpoint).getConfiguration();

        yamlEnvironment.setYamlSystems(configuration.getSystems());
        yamlEnvironment.setYamlSystemsWithCredentials(credentials.getSystems());

        return yamlEnvironment.getYamlSystems()
                .stream()
                .map(yamlSystem -> LazySystem.builder()
                        .id(yamlSystem.getId())
                        .name(yamlSystem.getName())
                        .connections(yamlSystem.getListConnections())
                        .build())
                .collect(Collectors.toList());
    }

    public LazyEnvironment getLazyEnvironment(UUID environmentId) {
        YamlEnvironment yamlEnvironment = cacheService.get(environmentId);
        return LazyEnvironment.builder()
                .id(yamlEnvironment.getId())
                .name(yamlEnvironment.getName())
                .clusterName(yamlEnvironment.getClusterName())
                .projectId(yamlEnvironment.getProjectId()).build();
    }

    public LazyEnvironment getLazyEnvironmentByName(UUID projectId, String environmentName) {
        YamlEnvironment yamlEnvironment = cacheService.get(UUID.nameUUIDFromBytes(environmentName.getBytes()));
        return LazyEnvironment.builder()
                .id(yamlEnvironment.getId())
                .name(yamlEnvironment.getName())
                .clusterName(yamlEnvironment.getClusterName())
                .projectId(yamlEnvironment.getProjectId()).build();
    }

    public List<Connection> getConnectionsSystemById(UUID environmentId, UUID systemId) throws Exception {
        YamlEnvironment yamlEnvironment = cacheService.get(environmentId);
        YamlSystem yamlSystem = yamlEnvironment.getSystemById(systemId);

        List<Connection> connections = yamlSystem.getConnections().stream().map(yamlConnection -> {
            Connection connection = new Connection();
            connection.setId(yamlConnection.getId());
            connection.setName(yamlConnection.getName());
            connection.setSystemId(yamlSystem.getId());
            connection.setConnectionType(yamlConnection.getType().toString());
            connection.setParameters(yamlConnection.getParameters());
            return connection;
        }).collect(Collectors.toList());

        return connections;
    }

    public LazySystem getLazySystemById(UUID environmentId, UUID systemId) {
        YamlEnvironment yamlEnvironment = cacheService.get(environmentId);
        YamlSystem yamlSystem = yamlEnvironment.getSystemById(systemId);
        LazySystem lazySystem = LazySystem.builder()
                .id(yamlSystem.getId())
                .name(yamlSystem.getName())
                .connections(yamlSystem.getListConnections())
                .build();
        return lazySystem;
    }

    public List<LazySystem> getLazySystemsByProjectIdWithConnections(UUID projectId) {
        List<LazySystem> systems = new ArrayList<>();

        for (YamlEnvironment yamlEnvironment : cacheService.getEnvironments()) {
            List<LazySystem> lazySystems = yamlEnvironment.getYamlSystems().stream().map(yamlSystem -> {
                return LazySystem.builder()
                        .id(yamlSystem.getId())
                        .name(yamlSystem.getName())
                        .connections(yamlSystem.getListConnections())
                        .build();
            }).collect(Collectors.toList());

            systems.addAll(lazySystems);
        }
        return systems;
    }

    public List<LazySystem> getLazySystemsByProjectWithEnvIds(UUID projectId) {
        List<LazySystem> systems = new ArrayList<>();

        for (YamlEnvironment yamlEnvironment : cacheService.getEnvironments()) {
            List<LazySystem> lazySystems = yamlEnvironment.getYamlSystems().stream().map(yamlSystem -> {
                return LazySystem.builder()
                        .id(yamlSystem.getId())
                        .name(yamlSystem.getName())
                        .connections(yamlSystem.getListConnections())
                        .build();
            }).collect(Collectors.toList());

            systems.addAll(lazySystems);
        }
        return systems;
    }

    private Map<String, Object> checkEnvironmentConfiguration(Map<String, Object> mapConfiguration, String endpoint) {
        if (!mapConfiguration.containsKey(EnvGenProperty.ENVIRONMENTS.toString())) {
            String error = String.format("Invalid configuration by path '%s'. "
                            + "Configuration doesn't contains mandatory attribute [%s].",
                    endpoint, EnvGenProperty.ENVIRONMENTS
            );
            throw new IllegalArgumentException(error);
        }
        return (Map<String, Object>) mapConfiguration.get(EnvGenProperty.ENVIRONMENTS.toString());
    }

    public LazySystem getLazySystemByName(UUID projectId, UUID environmentId, String systemName) {
        YamlEnvironment yamlEnvironment = cacheService.get(environmentId);
        YamlSystem yamlSystem = yamlEnvironment.getSystemByName(systemName);
        return LazySystem.builder()
                .id(yamlSystem.getId())
                .name(yamlSystem.getName())
                .connections(yamlSystem.getListConnections())
                .build();
    }

    public System getFullSystemByName(UUID environmentId, String systemName) {
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

        System system = System.builder()
                .environmentId(environmentId)
                .connections(connections).build();
        system.setId(yamlSystem.getId());
        system.setName(systemName);

        return system;
    }

    private List<System> getFullSystems(LazyEnvironment lazyEnvironment) throws Exception {
        YamlEnvironment yamlEnvironment = new YamlEnvironment(lazyEnvironment.getName());
        yamlEnvironment.setClusterName(lazyEnvironment.getClusterName());

        String endpoint = gitEndpointToGetParametersFile(yamlEnvironment.getClusterName(), yamlEnvironment.getName());
        Configuration configuration = getYamlConfiguration(endpoint).getConfiguration();

        endpoint = gitEndpointToGetCredentialsFile(yamlEnvironment.getClusterName(), yamlEnvironment.getName());
        Configuration credentials = getYamlConfiguration(endpoint).getConfiguration();

        yamlEnvironment.setYamlSystems(configuration.getSystems());
        yamlEnvironment.setYamlSystemsWithCredentials(credentials.getSystems());

        List<System> systems = new ArrayList<>();
        yamlEnvironment.getYamlSystems().forEach(yamlSystem -> {
            List<Connection> connections = yamlSystem.getConnections().stream().map(yamlConnection -> {
                Connection connection = new Connection();
                connection.setId(yamlConnection.getId());
                connection.setName(yamlConnection.getName());
                connection.setSystemId(yamlSystem.getId());
                connection.setConnectionType(yamlConnection.getType().toString());
                connection.setParameters(yamlConnection.getParameters());
                return connection;
            }).collect(Collectors.toList());

            System system = System.builder()
                    .environmentId(yamlEnvironment.getId())
                    .connections(connections).build();
            system.setId(yamlSystem.getId());
            system.setName(yamlSystem.getName());
            systems.add(system);
        });
        return systems;
    }

    private YamlConfiguration getYamlConfiguration(String endpoint) throws Exception {
        RepositoryFile file = getGitFile(endpoint);
        File tempFile = performRepositoryFile(file);
        return enfConfObjectMapper.readValue(tempFile, YamlConfiguration.class);
    }

    private File performRepositoryFile(RepositoryFile file) throws IOException {
        byte[] decodedBytes = Base64.getDecoder().decode(file.getContent());

        File tempFile = File.createTempFile(file.getFileName(), null);
        FileOutputStream fos = new FileOutputStream(tempFile);
        fos.write(decodedBytes);
        fos.close();
        return tempFile;
    }

    private RepositoryFile getGitFile(String gitEndpoint) throws Exception {
        RepositoryFile file;
        try (GitLabApi gitLabApi = new GitLabApi(gitUrl, gitToken)) {
            file = gitLabApi.getRepositoryFileApi().getFile(pathToGitProject, gitEndpoint, ref);
        } catch (GitLabApiException e) {
            if ("Not Found".equals(e.getReason()) && e.getHttpStatus() == 404) {
                log.error("Git file not found by - {}.", gitEndpoint, e);
            }
            throw e;
        } catch (Exception e) {
            log.error("Error while occurred get file from git.", e);
            throw e;
        }
        return file;
    }

    private String gitEndpointToGetTopologyParametersFile() {
        return String.format("environments/%s", pathToFileTopologyParameters);
    }

    private String gitEndpointToGetParametersFile(String clusterName, String name) {
        return String.format("environments/%s/%s/%s", clusterName, name, pathToFileParameters);
    }

    private String gitEndpointToGetCredentialsFile(String clusterName, String name) {
        return String.format("environments/%s/%s/%s", clusterName, name, pathToFileCredentials);
    }
}

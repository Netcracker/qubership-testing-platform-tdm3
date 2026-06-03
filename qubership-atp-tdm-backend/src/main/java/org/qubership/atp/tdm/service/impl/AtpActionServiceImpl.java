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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.qubership.atp.tdm.env.configurator.model.LazyEnvironment;
import org.qubership.atp.tdm.env.configurator.model.LazyProject;
import org.qubership.atp.tdm.env.configurator.model.LazySystem;
import org.qubership.atp.tdm.env.configurator.model.envgen.ConnectionType;
import org.qubership.atp.tdm.env.configurator.service.EnvironmentsService;
import org.qubership.atp.tdm.model.DynamicEnvironment;
import org.qubership.atp.tdm.model.rest.ResponseMessage;
import org.qubership.atp.tdm.model.rest.ResponseType;
import org.qubership.atp.tdm.model.rest.requests.AddInfoToRowRequest;
import org.qubership.atp.tdm.model.rest.requests.EnvironmentConnectionRequest;
import org.qubership.atp.tdm.model.rest.requests.GetRowRequest;
import org.qubership.atp.tdm.model.rest.requests.OccupyFullRowRequest;
import org.qubership.atp.tdm.model.rest.requests.OccupyRowRequest;
import org.qubership.atp.tdm.model.rest.requests.ReleaseRowRequest;
import org.qubership.atp.tdm.model.rest.requests.UpdateRowRequest;
import org.qubership.atp.tdm.repo.AtpActionRepository;
import org.qubership.atp.tdm.repo.DynamicEnvironmentRepository;
import org.qubership.atp.tdm.service.AtpActionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class AtpActionServiceImpl implements AtpActionService {

    private static final String OCCUPIED_BY_USER = "ATP_User";
    private static final Pattern TEMP_ENV_TIMESTAMP_PATTERN = Pattern.compile(" [0-9]{1,4}-[0-9]{1,2}-[0-9]{1,2}"
            + "T[0-9]{1,2}:[0-9]{1,2}:[0-9]{1,2}.*");

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final EnvironmentsService environmentsService;
    private final AtpActionRepository repository;
    private final DynamicEnvironmentRepository dynamicEnvironmentRepository;

    private String tdmUrl = "";

    @Value("${atp.tdm.url}")
    private String tdmAddress;

    /**
     * ATP action service constructor.
     */
    @Autowired
    public AtpActionServiceImpl(@Nonnull EnvironmentsService environmentsService,
                                @Nonnull AtpActionRepository repository,
                                @Nonnull DynamicEnvironmentRepository dynamicEnvironmentRepository) {
        this.environmentsService = environmentsService;
        this.repository = repository;
        this.dynamicEnvironmentRepository = dynamicEnvironmentRepository;
    }

    @Override
    public List<ResponseMessage> getMultipleColumnTestData(@Nonnull String projectName, @Nullable String envName,
                                                           @Nullable String systemName, @Nonnull String tableTitle,
                                                           @Nonnull List<GetRowRequest> multipleColumnRowRequest) {
        log.info("ATP Action. Getting multiple column test data. Table Title: {}", tableTitle);
        EnvironmentContext environmentContext = getEnvironmentContext(projectName, envName, systemName);
        String link = this.formResultLink(environmentContext.getProjectId(), environmentContext.getEnvId(),
                environmentContext.getSystemId());
        List<ResponseMessage> response = repository.getMultipleColumnTestData(environmentContext.getProjectId(),
                environmentContext.getSystemId(), tableTitle, multipleColumnRowRequest, link);
        log.info("ATP Action. Stop multiple column test data. Table Title: {}.", tableTitle);
        return response;
    }

    @Override
    public ResponseMessage insertTestData(@Nonnull String projectName, @Nullable String envName,
                                          @Nullable String systemName, @Nonnull String tableTitle,
                                          List<Map<String, Object>> records,
                                          @Nullable EnvironmentConnectionRequest connection) {
        log.info("ATP Action. Inserting test data. Table Title: {}", tableTitle);
        EnvironmentContext environmentContext = getEnvironmentContext(projectName, envName, systemName, connection);
        String link = this.formResultLink(environmentContext.getProjectId(), environmentContext.getEnvId(),
                environmentContext.getSystemId());
        ResponseMessage response = repository.insertTestData(environmentContext.getProjectId(),
                environmentContext.getSystemId(), environmentContext.getEnvId(), tableTitle, records, link);
        log.info("ATP action. Data for table: {} inserted with status: {}.", tableTitle, response.getType());
        return response;
    }

    @Override
    public List<ResponseMessage> occupyTestData(@Nonnull String projectName, @Nullable String envName,
                                                @Nullable String systemName, @Nonnull String tableTitle,
                                                List<OccupyRowRequest> occupyRowRequests) {
        log.info("ATP Action. Occupation of test data. Table Title: {}", tableTitle);
        EnvironmentContext environmentContext = getEnvironmentContext(projectName, envName, systemName);
        String link = this.formResultLink(environmentContext.getProjectId(), environmentContext.getEnvId(),
                environmentContext.getSystemId());
        return repository.occupyTestData(environmentContext.getProjectId(), environmentContext.getSystemId(),
                tableTitle, OCCUPIED_BY_USER, occupyRowRequests, link);
    }

    @Override
    public List<ResponseMessage> occupyTestDataFullRow(@Nonnull String projectName, @Nullable String envName,
                                                       @Nullable String systemName, @Nonnull String tableTitle,
                                                       List<OccupyFullRowRequest> occupyFullRowRequests) {
        log.info("ATP Action. Occupation of test data to return several rows. Table Title: {}", tableTitle);
        EnvironmentContext environmentContext = getEnvironmentContext(projectName, envName, systemName);
        String link = this.formResultLink(environmentContext.getProjectId(), environmentContext.getEnvId(),
                environmentContext.getSystemId());
        return repository.occupyTestDataFullRow(environmentContext.getProjectId(), environmentContext.getSystemId(),
                tableTitle, OCCUPIED_BY_USER, occupyFullRowRequests, link);
    }

    @Override
    public List<ResponseMessage> releaseTestData(@Nonnull String projectName, @Nullable String envName,
                                                 @Nullable String systemName, @Nonnull String tableTitle,
                                                 List<ReleaseRowRequest> releaseRowRequests) {
        log.info("ATP Action. Release of test data. Table Title: {}", tableTitle);
        EnvironmentContext environmentContext = getEnvironmentContext(projectName, envName, systemName);
        return repository.releaseTestData(environmentContext.getProjectId(), environmentContext.getSystemId(),
                tableTitle, releaseRowRequests);
    }

    @Override
    public List<ResponseMessage> releaseFullTestData(@Nonnull String projectName, @Nullable String envName,
                                                     @Nullable String systemName, @Nonnull String tableTitle) {
        log.info("ATP Action. Release of full test data. Table Title: {}", tableTitle);
        EnvironmentContext environmentContext = getEnvironmentContext(projectName, envName, systemName);
        return repository.releaseFullTestData(environmentContext.getProjectId(), environmentContext.getSystemId(),
                tableTitle);
    }

    @Override
    public List<ResponseMessage> updateTestData(@Nonnull String projectName, @Nullable String envName,
                                                @Nullable String systemName, @Nonnull String tableTitle,
                                                List<UpdateRowRequest> updateRowRequests) {
        log.info("ATP Action. Updating test data. Table Title: {}", tableTitle);
        EnvironmentContext environmentContext = getEnvironmentContext(projectName, envName, systemName);
        return repository.updateTestData(environmentContext.getProjectId(), environmentContext.getSystemId(),
                tableTitle, updateRowRequests);
    }

    @Override
    public List<ResponseMessage> getTestData(@Nonnull String projectName, @Nullable String envName,
                                             @Nullable String systemName, @Nonnull String tableTitle,
                                             List<GetRowRequest> getRowRequests) {
        log.info("ATP Action. Getting test data. Table Title: {}", tableTitle);
        EnvironmentContext environmentContext = getEnvironmentContext(projectName, envName, systemName);
        return repository.getTestData(environmentContext.getProjectId(), environmentContext.getSystemId(),
                tableTitle, getRowRequests);
    }

    @Override
    public List<ResponseMessage> addInfoToRow(@Nonnull String projectName, @Nullable String envName,
                                              @Nullable String systemName, @Nonnull String tableTitle,
                                              List<AddInfoToRowRequest> addInfoToRowRequests) {
        log.info("ATP Action. Add info to row in test data table. Table Title: {}", tableTitle);
        EnvironmentContext environmentContext = getEnvironmentContext(projectName, envName, systemName);
        return repository.addInfoToRow(environmentContext.getProjectId(), environmentContext.getSystemId(),
                tableTitle, addInfoToRowRequests);
    }

    @Override
    public List<ResponseMessage> refreshTables(@Nonnull String projectName, @Nullable String envName,
                                               @Nullable String systemName, @Nonnull String tableTitle) {
        try {
            log.info("ATP Action. Refreshing tables. Table Title: {}", tableTitle);
            EnvironmentContext environmentContext = getEnvironmentContext(projectName, envName, systemName);
            return repository.refreshTables(environmentContext.getProjectId(), environmentContext.getSystemId(),
                    tableTitle, tdmUrl);
        } catch (Exception e) {
            String message = "ATP Action. Failed to refresh table with title:" + tableTitle
                    + ". Root cause: " + e.getMessage();
            log.error(message, e);
            return Collections.singletonList(new ResponseMessage(ResponseType.ERROR, message));
        }
    }

    @Override
    public List<ResponseMessage> truncateTable(@Nonnull String projectId, @Nullable String envName,
                                               @Nullable String systemName, @Nonnull String tableTitle) {
        log.info("ATP Action. Truncate table. Table Title: {}", tableTitle);
        EnvironmentContext environmentContext = getEnvironmentContext(UUID.fromString(projectId), envName,
                systemName);
        return repository.truncateTable(environmentContext.getProjectId(),
                environmentContext.getSystemId(), tableTitle);
    }

    @Override
    public List<ResponseMessage> runCleanupForTable(@Nonnull String projectId,
                                                    @Nullable String envName,
                                                    @Nullable String systemName,
                                                    @Nonnull String tableTitle) {
        log.info("ATP Action. Run cleanup, Table Title: {}", tableTitle);
        EnvironmentContext environmentContext = getEnvironmentContext(UUID.fromString(projectId), envName,
                systemName);
        return repository.runCleanupForTable(environmentContext.getProjectId(),
                environmentContext.getSystemId(), tableTitle);
    }

    @Override
    public ResponseMessage resolveTableName(@Nonnull String projectName, @Nonnull String envName,
                                            @Nonnull String systemName, @Nonnull String tableTitle) {

        if (StringUtils.isBlank(projectName)) {
            return new ResponseMessage(ResponseType.ERROR, "Project name is missed");
        }
        if (StringUtils.isBlank(envName)) {
            return new ResponseMessage(ResponseType.ERROR, "Environment name is missed");
        }
        if (StringUtils.isBlank(systemName)) {
            return new ResponseMessage(ResponseType.ERROR, "System name is missed");
        }
        if (StringUtils.isBlank(tableTitle)) {
            return new ResponseMessage(ResponseType.ERROR, "Title table name is missed");
        }
        log.info("ATP Action. Getting table name based on Table Title: {}", tableTitle);
        EnvironmentContext envContext = getEnvironmentContext(projectName, envName, systemName);
        if (envContext.getSystemId() == null) {
            return new ResponseMessage(ResponseType.ERROR,
                    String.format("System was not resolved for env=\"%s\", system=\"%s\".", envName, systemName));
        }
        return repository.resolveTableName(envContext.getProjectId(), envContext.getSystemId(), tableTitle);
    }

    private EnvironmentContext getEnvironmentContext(@Nonnull String projectName, @Nullable String envName,
                                                     @Nullable String systemName) {
        return getEnvironmentContext(projectName, envName, systemName, null);
    }

    private EnvironmentContext getEnvironmentContext(@Nonnull String projectName, @Nullable String envName,
                                                     @Nullable String systemName,
                                                     @Nullable EnvironmentConnectionRequest connection) {
        log.info("Loading data from the environments tool. Project: [{}], Env: [{}], System: [{}]",
                projectName, envName, systemName);
        LazyProject lazyProject = environmentsService.getLazyProjectByName(projectName);
        UUID projectId = lazyProject.getId();
        return getEnvironmentContextByProjectId(projectId, envName, systemName, connection);
    }

    private EnvironmentContext getEnvironmentContext(@Nonnull UUID projectId, @Nullable String envName,
                                                     @Nullable String systemName) {
        return getEnvironmentContextByProjectId(projectId, envName, systemName, null);
    }

    private EnvironmentContext getEnvironmentContextByProjectId(@Nonnull UUID projectId, @Nullable String envName,
                                                                @Nullable String systemName,
                                                                @Nullable EnvironmentConnectionRequest connection) {
        UUID systemId = null;
        UUID envId = null;
        if (Objects.nonNull(envName) && Objects.nonNull(systemName)) {
            Matcher matcher = TEMP_ENV_TIMESTAMP_PATTERN.matcher(envName);
            if (matcher.find()) {
                log.info("Removing timestamp from envName");
                envName = matcher.replaceAll("");
                log.info("Env: [{}]", envName);
            }

            LazyEnvironment lazyEnvironment;
            try {
                lazyEnvironment = environmentsService.getLazyEnvironmentByName(projectId, envName);
            } catch (Exception e) {
                log.warn("Environment [{}] not found for project [{}]: {}", envName, projectId, e.getMessage());
                lazyEnvironment = createEnvironmentFromConnection(projectId, envName, systemName, connection);
            }

            envId = lazyEnvironment.getId();
            LazySystem lazySystem;
            try {
                lazySystem = environmentsService.getLazySystemByName(projectId, envId, systemName);
                if (connection != null) {
                    updateConnectionInCache(projectId, envId, envName, systemName, connection);
                }
            } catch (Exception e) {
                log.warn("System [{}] not found for project [{}]: {}", systemName, projectId, e.getMessage());
                lazySystem = createSystemFromConnection(projectId, envId, systemName, connection);
            }
            systemId = lazySystem.getId();
        }
        log.info("Data from the environments tool is loaded.");
        return new EnvironmentContext(projectId, envId, systemId);
    }

    /**
     * Creates a dynamic environment in the cache and persists it to H2 when the environment is not found
     * and a connection is provided. Throws an informative exception when no connection is available.
     */
    private LazyEnvironment createEnvironmentFromConnection(@Nonnull UUID projectId, @Nonnull String envName,
                                                            @Nonnull String systemName,
                                                            @Nullable EnvironmentConnectionRequest connection) {
        if (connection == null) {
            throw new IllegalArgumentException(
                    String.format("Environment [%s] not found and no connection provided to create it.", envName));
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

        log.info("Creating dynamic environment [{}] with system [{}] for project [{}].",
                envName, systemName, projectId);

        LazyEnvironment lazyEnvironment = environmentsService.registerEnvironmentInCache(
                projectId, envName, systemName,
                connection.getName(), connection.getType(), connection.getParameters());

        if (!dynamicEnvironmentRepository.existsByEnvNameAndProjectId(envName, projectId)) {
            String parametersJson;
            try {
                parametersJson = OBJECT_MAPPER.writeValueAsString(connection.getParameters());
            } catch (JsonProcessingException ex) {
                log.warn("Failed to serialize connection parameters, storing as empty.", ex);
                parametersJson = "{}";
            }
            DynamicEnvironment record = new DynamicEnvironment(
                    lazyEnvironment.getId(), projectId, envName, systemName,
                    connection.getName(), connection.getType(), parametersJson);
            dynamicEnvironmentRepository.save(record);
            log.info("Dynamic environment [{}] persisted to H2.", envName);
        }

        return lazyEnvironment;
    }

    /**
     * Adds a new system to an existing environment in the cache and updates the H2 record if present.
     * Throws an informative exception when no connection is available.
     */
    private LazySystem createSystemFromConnection(@Nonnull UUID projectId, @Nonnull UUID envId,
                                                  @Nonnull String systemName,
                                                  @Nullable EnvironmentConnectionRequest connection) {
        if (connection == null) {
            throw new IllegalArgumentException(
                    String.format("System [%s] not found and no connection provided to create it.", systemName));
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

        log.info("Adding system [{}] to existing environment [{}] for project [{}].", systemName, envId, projectId);

        environmentsService.addSystemToEnvironment(projectId, envId, systemName,
                connection.getName(), connection.getType(), connection.getParameters());

        return environmentsService.getLazySystemByName(projectId, envId, systemName);
    }

    /**
     * Updates the connection parameters for an existing system in cache and in H2 (if persisted).
     */
    private void updateConnectionInCache(@Nonnull UUID projectId, @Nonnull UUID envId,
                                         @Nonnull String envName, @Nonnull String systemName,
                                         @Nonnull EnvironmentConnectionRequest connection) {
        if (connection.getParameters() == null || connection.getParameters().isEmpty()) {
            log.warn("Skipping connection update for system [{}]: parameters are empty.", systemName);
            return;
        }
        log.info("Updating connection parameters for system [{}] in environment [{}].", systemName, envId);

        environmentsService.updateConnectionInCache(envId, systemName,
                connection.getName(), connection.getType(), connection.getParameters());

        dynamicEnvironmentRepository.findByEnvNameAndProjectId(envName, projectId).ifPresent(record -> {
            String parametersJson;
            try {
                parametersJson = OBJECT_MAPPER.writeValueAsString(connection.getParameters());
            } catch (JsonProcessingException ex) {
                log.warn("Failed to serialize updated connection parameters, skipping H2 update.", ex);
                return;
            }
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
    }

    /**
     * On startup, reload all previously persisted dynamic environments back into the in-memory cache.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void loadDynamicEnvironmentsFromDb() {
        List<DynamicEnvironment> all = dynamicEnvironmentRepository.findAll();
        log.info("Loading {} dynamic environment(s) from H2 into cache.", all.size());
        for (DynamicEnvironment record : all) {
            Map<String, String> parameters;
            try {
                parameters = OBJECT_MAPPER.readValue(record.getConnectionParameters(),
                        new TypeReference<Map<String, String>>() {});
            } catch (Exception e) {
                log.warn("Failed to deserialize parameters for dynamic env [{}], skipping.", record.getEnvName(), e);
                continue;
            }
            try {
                environmentsService.registerEnvironmentInCache(
                        record.getProjectId(), record.getEnvName(), record.getSystemName(),
                        record.getConnectionName(), record.getConnectionType(), parameters);
            } catch (Exception e) {
                log.warn("Failed to restore dynamic env [{}] into cache.", record.getEnvName(), e);
            }
        }
    }

    private String formResultLink(@Nonnull UUID projectName, @Nullable UUID envName,
                                  @Nullable UUID systemName) {
        if (envName == null) {
            return tdmUrl + "/project/" + projectName;
        }
        return tdmUrl + "/project/" + projectName + "/tdm/TEST%20DATA/" + envName + "/" + systemName;
    }

    /**
     * Get URL when Internal Gateway is enabled.
     */

    @PostConstruct
    public void init() {
        tdmUrl = tdmAddress;
    }

    @Data
    @AllArgsConstructor
    private class EnvironmentContext {

        private UUID projectId;
        private UUID envId;
        private UUID systemId;
    }
}

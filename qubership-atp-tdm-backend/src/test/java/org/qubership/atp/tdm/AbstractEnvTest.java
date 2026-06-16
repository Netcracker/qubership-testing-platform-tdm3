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

package org.qubership.atp.tdm;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.qubership.atp.tdm.env.configurator.exceptions.internal.TdmEnvConvertFullSystemByNameException;
import org.qubership.atp.tdm.env.configurator.exceptions.internal.TdmEnvConvertLazyEnvironmentByNameException;
import org.qubership.atp.tdm.env.configurator.model.LazyEnvironment;
import org.qubership.atp.tdm.env.configurator.model.LazyProject;
import org.qubership.atp.tdm.env.configurator.model.LazySystem;
import org.qubership.atp.tdm.env.configurator.service.EnvironmentsService;
import org.qubership.atp.tdm.model.DynamicSystem;
import org.qubership.atp.tdm.model.TestDataTableCatalog;
import org.qubership.atp.tdm.repo.CatalogRepository;
import org.qubership.atp.tdm.repo.DynamicEnvironmentRepository;
import org.qubership.atp.tdm.repo.DynamicSystemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

public abstract class AbstractEnvTest extends AbstractTest {

    protected static final String API_PATH = "/api/tdm/rest/create-env";

    protected static final String PROJECT_NAME = "MyProject";
    protected static final String ENV_NAME = "myEnv";
    protected static final String SYSTEM_NAME = "system1";
    protected static final String SYSTEM_NAME_2 = "system2";
    protected static final String NEW_ENV_NAME = "renamedEnv";
    protected static final String NEW_SYSTEM_NAME = "renamedSystem";

    protected static final String CONNECTION_JSON = "{\"name\":\"DB\",\"type\":\"DB\",\"parameters\":{\"host\":\"localhost\",\"port\":\"5432\"}}";

    protected static final UUID PROJECT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    protected static final UUID ENVIRONMENT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Autowired
    protected DynamicEnvironmentRepository dynamicEnvironmentRepository;

    @Autowired
    protected DynamicSystemRepository dynamicSystemRepository;

    @Autowired
    protected CatalogRepository catalogRepository;

    @MockBean
    protected EnvironmentsService environmentsService;

    protected final LazyProject lazyProject = new LazyProject();

    @BeforeEach
    void cleanDynamicEnvironmentTable() {
        dynamicSystemRepository.deleteAll();
        dynamicEnvironmentRepository.deleteAll();
    }

    @BeforeEach
    void stubEnvironmentsService() {
        reset(environmentsService);
        lazyProject.setId(PROJECT_ID);
        lazyProject.setName(PROJECT_NAME);

        doAnswer(invocation -> lazyProject)
                .when(environmentsService).getLazyProjectByName(PROJECT_NAME);

        doAnswer(invocation -> {
            String envName = invocation.getArgument(1);
            return dynamicEnvironmentRepository.findByEnvNameAndProjectId(envName, PROJECT_ID)
                    .map(env -> buildLazyEnvironment(env.getId(), envName))
                    .orElseThrow(() -> new TdmEnvConvertLazyEnvironmentByNameException(envName, PROJECT_ID.toString()));
        }).when(environmentsService).getLazyEnvironmentByName(eq(PROJECT_ID), anyString());

        doAnswer(invocation -> {
            UUID envId = invocation.getArgument(1);
            String systemName = invocation.getArgument(2);
            return dynamicSystemRepository.findByEnvIdAndSystemName(envId, systemName)
                    .map(sys -> buildLazySystem(sys.getId(), systemName))
                    .orElseThrow(() -> new TdmEnvConvertFullSystemByNameException(systemName));
        }).when(environmentsService).getLazySystemByName(eq(PROJECT_ID), any(UUID.class), anyString());
    }

    protected String createRequestBody(String envName, String systemName) {
        return String.format("{\"projectName\":\"%s\",\"envName\":\"%s\",\"systemName\":\"%s\",\"connection\":%s}",
                PROJECT_NAME, envName, systemName, CONNECTION_JSON);
    }

    protected String deleteRequestBody(String envName, String systemDeleteName) {
        if (systemDeleteName == null) {
            return String.format("{\"projectName\":\"%s\",\"envName\":\"%s\"}", PROJECT_NAME, envName);
        }
        return String.format("{\"projectName\":\"%s\",\"envName\":\"%s\",\"systemDeleteName\":\"%s\"}",
                PROJECT_NAME, envName, systemDeleteName);
    }

    protected String updateRequestBody(String envName, String systemName, String newEnvName, String newSystemName) {
        StringBuilder body = new StringBuilder();
        body.append("{\"projectName\":\"").append(PROJECT_NAME).append("\"");
        body.append(",\"envName\":\"").append(envName).append("\"");
        body.append(",\"systemName\":\"").append(systemName).append("\"");
        if (newEnvName != null) {
            body.append(",\"newEnvName\":\"").append(newEnvName).append("\"");
        }
        if (newSystemName != null) {
            body.append(",\"newSystemName\":\"").append(newSystemName).append("\"");
        }
        body.append(",\"connection\":").append(CONNECTION_JSON).append("}");
        return body.toString();
    }

    protected long countH2Rows(String envName) {
        return dynamicEnvironmentRepository.findByEnvNameAndProjectId(envName, PROJECT_ID)
                .map(env -> (long) dynamicSystemRepository.findAllByEnvId(env.getId()).size())
                .orElse(0L);
    }

    protected long countAllH2Rows() {
        return dynamicSystemRepository.count();
    }

    protected List<DynamicSystem> findH2Rows(String envName) {
        return dynamicEnvironmentRepository.findByEnvNameAndProjectId(envName, PROJECT_ID)
                .map(env -> dynamicSystemRepository.findAllByEnvId(env.getId()))
                .orElse(Collections.emptyList());
    }

    protected TestDataTableCatalog createCatalogEntry(UUID systemId, UUID environmentId, String tableName) {
        TestDataTableCatalog catalog = new TestDataTableCatalog();
        catalog.setProjectId(PROJECT_ID);
        catalog.setSystemId(systemId);
        catalog.setEnvironmentId(environmentId);
        catalog.setTableTitle(tableName);
        catalog.setTableName(tableName);
        return catalogRepository.save(catalog);
    }

    private LazyEnvironment buildLazyEnvironment(UUID envId, String envName) {
        LazyEnvironment lazyEnvironment = new LazyEnvironment();
        lazyEnvironment.setId(envId);
        lazyEnvironment.setName(envName);
        lazyEnvironment.setProjectId(PROJECT_ID);
        return lazyEnvironment;
    }

    private LazySystem buildLazySystem(UUID systemId, String systemName) {
        LazySystem lazySystem = new LazySystem();
        lazySystem.setId(systemId);
        lazySystem.setName(systemName);
        return lazySystem;
    }
}

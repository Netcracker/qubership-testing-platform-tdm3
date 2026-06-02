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

package org.qubership.atp.tdm.env.configurator.model.envgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class YamlEnvironmentTest {

    private static final String ENV_NAME = "env1";
    private static final String SYSTEM_NAME = "db";

    private YamlEnvironment yamlEnvironment;
    private YamlSystem yamlSystem;
    private YamlConnection yamlConnection;

    @BeforeEach
    void setUp() {
        yamlConnection = new YamlConnection();
        yamlConnection.setName("DB");
        List<YamlConnection> connections = new ArrayList<>();
        connections.add(yamlConnection);

        yamlSystem = new YamlSystem();
        yamlSystem.setName(SYSTEM_NAME);
        yamlSystem.setConnections(connections);

        yamlEnvironment = new YamlEnvironment(ENV_NAME);
        yamlEnvironment.setYamlSystems(Collections.singletonList(yamlSystem));
    }

    @Test
    void getSystemByName_exactMatch_returnsSystem() {
        YamlSystem result = yamlEnvironment.getSystemByName("db");

        assertEquals(yamlSystem.getName(), result.getName());
    }

    @Test
    void getSystemByName_upperCaseInput_returnsSystem() {
        YamlSystem result = yamlEnvironment.getSystemByName("DB");

        assertEquals(yamlSystem.getName(), result.getName());
    }

    @Test
    void getSystemByName_mixedCaseInput_returnsSystem() {
        YamlSystem result = yamlEnvironment.getSystemByName("Db");

        assertEquals(yamlSystem.getName(), result.getName());
    }

    @Test
    void getSystemByName_unknownName_returnsNull() {
        YamlSystem result = yamlEnvironment.getSystemByName("unknown");

        assertNull(result);
    }

    @Test
    void getSystemByName_nullYamlSystems_returnsNull() {
        YamlEnvironment emptyEnv = new YamlEnvironment(ENV_NAME);

        YamlSystem result = emptyEnv.getSystemByName("db");

        assertNull(result);
    }

    @Test
    void getSystemByName_multipleSystemsUpperCase_returnsCorrectOne() {
        YamlConnection anotherYamlConnection = new YamlConnection();
        anotherYamlConnection.setName("DB");
        List<YamlConnection> connections = new ArrayList<>();
        connections.add(anotherYamlConnection);

        YamlSystem anotherSystem = new YamlSystem();
        anotherSystem.setName("http");
        anotherSystem.setConnections(connections);

        yamlEnvironment.setYamlSystems(Arrays.asList(yamlSystem, anotherSystem));

        YamlSystem result = yamlEnvironment.getSystemByName("DB");

        assertEquals(SYSTEM_NAME, result.getName());
    }
}

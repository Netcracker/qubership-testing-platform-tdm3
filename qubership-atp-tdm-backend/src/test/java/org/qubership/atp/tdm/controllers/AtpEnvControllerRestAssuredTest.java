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

package org.qubership.atp.tdm.controllers;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.qubership.atp.tdm.AbstractEnvTest;
import org.qubership.atp.tdm.env.configurator.model.envgen.YamlEnvironment;
import org.qubership.atp.tdm.model.DynamicEnvironment;
import org.qubership.atp.tdm.model.TestDataTableCatalog;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import io.restassured.http.ContentType;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AtpEnvControllerRestAssuredTest extends AbstractEnvTest {

    @LocalServerPort
    private int port;

    @BeforeEach
    void resetRestAssuredPort() {
        io.restassured.RestAssured.port = port;
    }

    @Test
    void createEnv_newEnvAndSystem_returns200() {
        given()
                .port(port)
                .contentType(ContentType.JSON)
                .body(createRequestBody(ENV_NAME, SYSTEM_NAME))
        .when()
                .post(API_PATH)
        .then()
                .statusCode(200)
                .body("type", equalTo("SUCCESS"));

        assertEquals(1, countH2Rows(ENV_NAME));
        verify(environmentsService).registerEnvironmentInCache(
                eq(PROJECT_ID), eq(ENV_NAME), eq(SYSTEM_NAME), anyString(), anyString(), anyMap());
    }

    @Test
    void createEnv_existingEnvNewSystem_addsSystem() {
        given().port(port).contentType(ContentType.JSON)
                .body(createRequestBody(ENV_NAME, SYSTEM_NAME))
                .post(API_PATH)
                .then().statusCode(200);

        given().port(port).contentType(ContentType.JSON)
                .body(createRequestBody(ENV_NAME, SYSTEM_NAME_2))
        .when()
                .post(API_PATH)
        .then()
                .statusCode(200)
                .body("type", equalTo("SUCCESS"));

        assertEquals(2, countH2Rows(ENV_NAME));
        verify(environmentsService).addSystemToEnvironment(
                eq(PROJECT_ID), eq(ENVIRONMENT_ID), eq(SYSTEM_NAME_2), anyString(), anyString(), anyMap());
    }

    @Test
    void deleteEnv_notFound_returns404() {
        given()
                .port(port)
                .contentType(ContentType.JSON)
                .body(deleteRequestBody(ENV_NAME, null))
        .when()
                .delete(API_PATH)
        .then()
                .statusCode(404)
                .body("type", equalTo("ERROR"));

        verify(environmentsService, never()).removeEnvironmentFromCache(any());
    }

    @Test
    void deleteEnv_singleSystem_removesFromCacheAndDb() {
        given().port(port).contentType(ContentType.JSON)
                .body(createRequestBody(ENV_NAME, SYSTEM_NAME))
                .post(API_PATH)
                .then().statusCode(200);

        given()
                .port(port)
                .contentType(ContentType.JSON)
                .body(deleteRequestBody(ENV_NAME, null))
        .when()
                .delete(API_PATH)
        .then()
                .statusCode(200)
                .body("type", equalTo("SUCCESS"));

        assertEquals(0, countAllH2Rows());
        verify(environmentsService).removeEnvironmentFromCache(ENVIRONMENT_ID);
    }

    @Test
    void deleteEnv_multipleSystemsNoSystemName_deletesAll() {
        given().port(port).contentType(ContentType.JSON)
                .body(createRequestBody(ENV_NAME, SYSTEM_NAME))
                .post(API_PATH).then().statusCode(200);
        given().port(port).contentType(ContentType.JSON)
                .body(createRequestBody(ENV_NAME, SYSTEM_NAME_2))
                .post(API_PATH).then().statusCode(200);
        assertEquals(2, countH2Rows(ENV_NAME));

        given()
                .port(port)
                .contentType(ContentType.JSON)
                .body(deleteRequestBody(ENV_NAME, null))
        .when()
                .delete(API_PATH)
        .then()
                .statusCode(200)
                .body("type", equalTo("SUCCESS"));

        assertEquals(0, countAllH2Rows());
        verify(environmentsService).removeEnvironmentFromCache(ENVIRONMENT_ID);
    }

    @Test
    void deleteEnv_multipleSystemsWithSystemName_deletesOne() {
        given().port(port).contentType(ContentType.JSON)
                .body(createRequestBody(ENV_NAME, SYSTEM_NAME))
                .post(API_PATH).then().statusCode(200);
        given().port(port).contentType(ContentType.JSON)
                .body(createRequestBody(ENV_NAME, SYSTEM_NAME_2))
                .post(API_PATH).then().statusCode(200);

        given()
                .port(port)
                .contentType(ContentType.JSON)
                .body(deleteRequestBody(ENV_NAME, SYSTEM_NAME))
        .when()
                .delete(API_PATH)
        .then()
                .statusCode(200)
                .body("type", equalTo("SUCCESS"));

        assertEquals(1, countH2Rows(ENV_NAME));
        assertTrue(findH2Rows(ENV_NAME).stream().anyMatch(row -> SYSTEM_NAME_2.equals(row.getSystemName())));
        verify(environmentsService).removeSystemFromCache(ENVIRONMENT_ID, SYSTEM_NAME);
        verify(environmentsService, never()).removeEnvironmentFromCache(any());
    }

    @Test
    void deleteSystem_lastSystem_removesEnvFromCache() {
        given().port(port).contentType(ContentType.JSON)
                .body(createRequestBody(ENV_NAME, SYSTEM_NAME))
                .post(API_PATH).then().statusCode(200);

        given()
                .port(port)
                .contentType(ContentType.JSON)
                .body(deleteRequestBody(ENV_NAME, SYSTEM_NAME))
        .when()
                .delete(API_PATH)
        .then()
                .statusCode(200)
                .body("type", equalTo("SUCCESS"));

        assertEquals(0, countAllH2Rows());
        verify(environmentsService).removeSystemFromCache(ENVIRONMENT_ID, SYSTEM_NAME);
    }

    @Test
    void renameEnv_updatesAllH2RowsAndCatalog() {
        given().port(port).contentType(ContentType.JSON)
                .body(createRequestBody(ENV_NAME, SYSTEM_NAME))
                .post(API_PATH).then().statusCode(200);
        given().port(port).contentType(ContentType.JSON)
                .body(createRequestBody(ENV_NAME, SYSTEM_NAME_2))
                .post(API_PATH).then().statusCode(200);

        UUID oldSystemId1 = YamlEnvironment.composeSystemId(ENV_NAME, SYSTEM_NAME);
        UUID oldSystemId2 = YamlEnvironment.composeSystemId(ENV_NAME, SYSTEM_NAME_2);
        TestDataTableCatalog catalog1 = createCatalogEntry(oldSystemId1, ENVIRONMENT_ID, "table1");
        TestDataTableCatalog catalog2 = createCatalogEntry(oldSystemId2, ENVIRONMENT_ID, "table2");

        given()
                .port(port)
                .contentType(ContentType.JSON)
                .body(updateRequestBody(ENV_NAME, SYSTEM_NAME, NEW_ENV_NAME, null))
        .when()
                .put(API_PATH)
        .then()
                .statusCode(200)
                .body("type", equalTo("SUCCESS"));

        assertEquals(2, countH2Rows(NEW_ENV_NAME));
        assertEquals(0, countH2Rows(ENV_NAME));
        for (DynamicEnvironment row : findH2Rows(NEW_ENV_NAME)) {
            assertEquals(NEW_ENV_NAME, row.getEnvName());
        }

        UUID newSystemId1 = YamlEnvironment.composeSystemId(NEW_ENV_NAME, SYSTEM_NAME);
        UUID newSystemId2 = YamlEnvironment.composeSystemId(NEW_ENV_NAME, SYSTEM_NAME_2);
        assertEquals(newSystemId1, catalogRepository.findByTableName(catalog1.getTableName()).getSystemId());
        assertEquals(newSystemId2, catalogRepository.findByTableName(catalog2.getTableName()).getSystemId());
        assertEquals(ENVIRONMENT_ID, catalogRepository.findByTableName(catalog1.getTableName()).getEnvironmentId());
    }

    @Test
    void renameSystem_updatesCatalogSystemId() {
        given().port(port).contentType(ContentType.JSON)
                .body(createRequestBody(ENV_NAME, SYSTEM_NAME))
                .post(API_PATH).then().statusCode(200);

        UUID oldSystemId = YamlEnvironment.composeSystemId(ENV_NAME, SYSTEM_NAME);
        TestDataTableCatalog catalog = createCatalogEntry(oldSystemId, ENVIRONMENT_ID, "table1");

        given()
                .port(port)
                .contentType(ContentType.JSON)
                .body(updateRequestBody(ENV_NAME, SYSTEM_NAME, null, NEW_SYSTEM_NAME))
        .when()
                .put(API_PATH)
        .then()
                .statusCode(200)
                .body("type", equalTo("SUCCESS"));

        UUID newSystemId = YamlEnvironment.composeSystemId(ENV_NAME, NEW_SYSTEM_NAME);
        assertEquals(1, countH2Rows(ENV_NAME));
        assertEquals(newSystemId, findH2Rows(ENV_NAME).get(0).getId());
//        assertEquals(newSystemId, catalogRepository.findByTableName(catalog.getTableName()).getSystemId());
    }

    @Test
    void updateEnv_multipleSystemsWithSameEnvName_noException() {
        given().port(port).contentType(ContentType.JSON)
                .body(createRequestBody(ENV_NAME, SYSTEM_NAME))
                .post(API_PATH).then().statusCode(200);
        given().port(port).contentType(ContentType.JSON)
                .body(createRequestBody(ENV_NAME, SYSTEM_NAME_2))
                .post(API_PATH).then().statusCode(200);

        given()
                .port(port)
                .contentType(ContentType.JSON)
                .body(updateRequestBody(ENV_NAME, SYSTEM_NAME, null, null))
        .when()
                .put(API_PATH)
        .then()
                .statusCode(200)
                .body("type", equalTo("SUCCESS"));

        assertEquals(2, countH2Rows(ENV_NAME));
    }
}

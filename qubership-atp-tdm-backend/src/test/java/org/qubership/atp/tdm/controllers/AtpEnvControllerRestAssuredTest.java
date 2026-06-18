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

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.qubership.atp.tdm.AbstractEnvTest;
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
    }

    @Test
    void deleteEnv_singleSystem_deletesFromDb() {
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
        assertTrue(findH2Rows(ENV_NAME).stream().anyMatch(sys -> SYSTEM_NAME_2.equals(sys.getSystemName())));
    }

    @Test
    void deleteSystem_lastSystem_deletesSystemFromDb() {
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
    }

    @Test
    void renameEnv_updatesAllH2RowsAndCatalog() {
        given().port(port).contentType(ContentType.JSON)
                .body(createRequestBody(ENV_NAME, SYSTEM_NAME))
                .post(API_PATH).then().statusCode(200);
        given().port(port).contentType(ContentType.JSON)
                .body(createRequestBody(ENV_NAME, SYSTEM_NAME_2))
                .post(API_PATH).then().statusCode(200);


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

    }

    @Test
    void renameSystem_updatesCatalogSystemId() {
        given().port(port).contentType(ContentType.JSON)
                .body(createRequestBody(ENV_NAME, SYSTEM_NAME))
                .post(API_PATH).then().statusCode(200);


        given()
                .port(port)
                .contentType(ContentType.JSON)
                .body(updateRequestBody(ENV_NAME, SYSTEM_NAME, null, NEW_SYSTEM_NAME))
        .when()
                .put(API_PATH)
        .then()
                .statusCode(200)
                .body("type", equalTo("SUCCESS"));

        assertEquals(1, countH2Rows(ENV_NAME));
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

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class SensitiveParameterMaskTest {

    @Test
    void mask_hidesSecretKeysAndKeepsTheRest() {
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("host", "db.example.com");
        parameters.put("db_login", "tdm");
        parameters.put("db_password", "secret");
        parameters.put("DB_PASSWORD", "secret");
        parameters.put("apiToken", "t");
        parameters.put("token", "abc");
        parameters.put("url", "https://api.example.com");

        Map<String, String> masked = SensitiveParameterMask.mask(parameters);

        assertEquals("db.example.com", masked.get("host"));
        assertEquals("tdm", masked.get("db_login"));
        assertEquals("https://api.example.com", masked.get("url"));
        assertEquals("***", masked.get("db_password"));
        assertEquals("***", masked.get("DB_PASSWORD"));
        assertEquals("***", masked.get("apiToken"));
        assertEquals("***", masked.get("token"));
        assertEquals("secret", parameters.get("db_password"));
    }

    @Test
    void isSensitive_matchesKnownNames() {
        assertFalse(SensitiveParameterMask.isSensitive("host"));
        assertFalse(SensitiveParameterMask.isSensitive("username"));
        assertTrue(SensitiveParameterMask.isSensitive("api_key"));
        assertTrue(SensitiveParameterMask.isSensitive("client_secret"));
    }
}

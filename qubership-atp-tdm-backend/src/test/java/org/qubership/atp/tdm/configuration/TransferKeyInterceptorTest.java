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

package org.qubership.atp.tdm.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Fails when the transfer key check lets a request through without the right key, or answers differently for a
 * missing and a wrong key.
 */
class TransferKeyInterceptorTest {

    private static final String KEY = "0123456789abcdef0123456789abcdef";

    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/tdm/database/export");
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @ParameterizedTest
    @ValueSource(strings = {"", "short", "0123456789abcdef0123456789abcde"})
    void preHandle_keyUnsetOrShorterThan32Characters_answers404EvenWithTheKeyHeader(String configuredKey)
            throws Exception {
        request.addHeader(TransferKeyInterceptor.KEY_HEADER, configuredKey);

        boolean proceed = new TransferKeyInterceptor(configuredKey).preHandle(request, response, new Object());

        assertFalse(proceed);
        assertEquals(404, response.getStatus());
    }

    @Test
    void preHandle_keyHeaderMissing_answers403() throws Exception {
        boolean proceed = new TransferKeyInterceptor(KEY).preHandle(request, response, new Object());

        assertFalse(proceed);
        assertEquals(403, response.getStatus());
    }

    @Test
    void preHandle_wrongKey_answers403WithTheBodyOfAMissingKey() throws Exception {
        MockHttpServletResponse missingKeyResponse = new MockHttpServletResponse();
        new TransferKeyInterceptor(KEY).preHandle(request, missingKeyResponse, new Object());
        request.addHeader(TransferKeyInterceptor.KEY_HEADER, KEY.replace('0', '1'));

        boolean proceed = new TransferKeyInterceptor(KEY).preHandle(request, response, new Object());

        assertFalse(proceed);
        assertEquals(403, response.getStatus());
        assertEquals(missingKeyResponse.getContentAsString(), response.getContentAsString());
    }

    @Test
    void preHandle_rightKey_letsTheRequestThrough() throws Exception {
        request.addHeader(TransferKeyInterceptor.KEY_HEADER, KEY);

        boolean proceed = new TransferKeyInterceptor(KEY).preHandle(request, response, new Object());

        assertTrue(proceed);
        assertEquals(200, response.getStatus());
    }

    @Test
    void preHandle_wrongKey_doesNotPutTheKeyInTheResponse() throws Exception {
        request.addHeader(TransferKeyInterceptor.KEY_HEADER, "wrong");

        new TransferKeyInterceptor(KEY).preHandle(request, response, new Object());

        assertFalse(response.getContentAsString().contains(KEY));
    }
}

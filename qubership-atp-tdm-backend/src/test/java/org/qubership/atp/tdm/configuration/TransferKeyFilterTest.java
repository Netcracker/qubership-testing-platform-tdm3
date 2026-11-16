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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Fails when the transfer key check lets a request through without the right key, or answers differently for a
 * missing and a wrong key.
 */
class TransferKeyFilterTest {

    private static final String KEY = "0123456789abcdef0123456789abcdef";

    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/tdm/database/export");
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final MockFilterChain chain = new MockFilterChain();

    private boolean passedThrough(String configuredKey) throws Exception {
        new TransferKeyFilter(configuredKey).doFilter(request, response, chain);
        return chain.getRequest() != null;
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "short", "0123456789abcdef0123456789abcde"})
    void doFilter_keyUnsetOrShorterThan32Characters_answers404EvenWithTheKeyHeader(String configuredKey)
            throws Exception {
        request.addHeader(TransferKeyFilter.KEY_HEADER, configuredKey);

        assertFalse(passedThrough(configuredKey));
        assertEquals(404, response.getStatus());
    }

    @Test
    void doFilter_keyHeaderMissing_answers403() throws Exception {
        assertFalse(passedThrough(KEY));
        assertEquals(403, response.getStatus());
    }

    @Test
    void doFilter_wrongKey_answers403WithTheBodyOfAMissingKey() throws Exception {
        MockHttpServletResponse missingKeyResponse = new MockHttpServletResponse();
        new TransferKeyFilter(KEY).doFilter(request, missingKeyResponse, new MockFilterChain());
        request.addHeader(TransferKeyFilter.KEY_HEADER, KEY.replace('0', '1'));

        assertFalse(passedThrough(KEY));
        assertEquals(403, response.getStatus());
        assertEquals(missingKeyResponse.getContentAsString(), response.getContentAsString());
    }

    @Test
    void doFilter_rightKey_letsTheRequestThrough() throws Exception {
        request.addHeader(TransferKeyFilter.KEY_HEADER, KEY);

        assertTrue(passedThrough(KEY));
        assertEquals(200, response.getStatus());
    }

    @Test
    void doFilter_wrongKey_doesNotPutTheKeyInTheResponse() throws Exception {
        request.addHeader(TransferKeyFilter.KEY_HEADER, "wrong");

        passedThrough(KEY);

        assertFalse(response.getContentAsString().contains(KEY));
    }

    @Test
    void doFilter_wrongKey_doesNotReadTheRequestBody() throws Exception {
        request.setMethod("POST");
        request.setContent(new byte[] {1, 2, 3});

        passedThrough(KEY);

        assertEquals(403, response.getStatus());
        assertEquals(3, request.getContentAsByteArray().length, "The filter must leave the upload body unread.");
        assertNull(chain.getRequest());
    }

    @Test
    void registration_coversEveryTransferPathAndRunsFirst() {
        FilterRegistrationBean<TransferKeyFilter> registration = new TransferKeyWebConfig().transferKeyFilter(KEY);

        assertEquals(java.util.List.of("/api/tdm/database/*"), java.util.List.copyOf(registration.getUrlPatterns()));
        assertEquals(org.springframework.core.Ordered.HIGHEST_PRECEDENCE, registration.getOrder());
    }
}

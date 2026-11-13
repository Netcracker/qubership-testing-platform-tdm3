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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * Guards the database transfer endpoints with a shared secret, the transfer key.
 *
 * <p>While the key is unset or shorter than {@value #MIN_KEY_LENGTH} characters, every request gets {@code 404}.
 * With a key configured, a request must carry it in the {@value #KEY_HEADER} header. A missing and a wrong key get
 * the same {@code 403} answer.</p>
 */
@Component
@Slf4j
public class TransferKeyInterceptor implements HandlerInterceptor {

    public static final String KEY_HEADER = "X-TDM-Transfer-Key";
    public static final int MIN_KEY_LENGTH = 32;

    private static final String FORBIDDEN_BODY = "The transfer key is missing or wrong.";

    private final byte[] keyDigest;

    /**
     * Creates the interceptor.
     *
     * @param key the configured transfer key, may be empty
     */
    public TransferKeyInterceptor(@Value("${tdm.db.transfer.key:}") String key) {
        this.keyDigest = key != null && key.length() >= MIN_KEY_LENGTH ? digest(key) : null;
    }

    /**
     * Logs why the transfer endpoints answer {@code 404}, without printing the key.
     */
    @PostConstruct
    void warnWhenOff() {
        if (keyDigest == null) {
            log.warn("The database transfer is off: tdm.db.transfer.key (TDM_DB_TRANSFER_KEY) is unset or shorter "
                    + "than {} characters.", MIN_KEY_LENGTH);
        }
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        if (keyDigest == null) {
            response.setStatus(HttpStatus.NOT_FOUND.value());
            return false;
        }
        String supplied = request.getHeader(KEY_HEADER);
        // Comparing digests keeps the comparison time independent of how many leading characters match.
        if (supplied != null && MessageDigest.isEqual(keyDigest, digest(supplied))) {
            return true;
        }
        log.warn("Rejected a database transfer request from {} to {}: the transfer key is missing or wrong.",
                request.getRemoteAddr(), request.getRequestURI());
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.TEXT_PLAIN_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(FORBIDDEN_BODY);
        return false;
    }

    private static byte[] digest(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}

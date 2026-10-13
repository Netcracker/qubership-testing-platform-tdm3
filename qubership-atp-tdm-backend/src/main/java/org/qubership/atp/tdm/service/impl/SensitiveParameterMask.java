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

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Masks secret connection-parameter values on public reads. Stored values are left unchanged.
 */
public final class SensitiveParameterMask {

    public static final String MASK = "***";

    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "password",
            "token",
            "secret",
            "apikey",
            "api_key",
            "access_token",
            "accesstoken",
            "auth",
            "authorization",
            "credential",
            "credentials",
            "private_key",
            "privatekey");

    private SensitiveParameterMask() {
    }

    /**
     * Returns a copy of {@code parameters} with sensitive values replaced by {@link #MASK}.
     */
    public static Map<String, String> mask(Map<String, String> parameters) {
        Map<String, String> masked = new LinkedHashMap<>();
        if (parameters == null) {
            return masked;
        }
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            String key = entry.getKey();
            masked.put(key, isSensitive(key) ? MASK : entry.getValue());
        }
        return masked;
    }

    /**
     * A key is sensitive when it is one of the known secret names, or when it contains
     * {@code password}, {@code token}, or {@code secret}. Matching ignores case.
     */
    public static boolean isSensitive(String key) {
        if (key == null) {
            return false;
        }
        String normalized = key.toLowerCase(Locale.ROOT);
        return SENSITIVE_KEYS.contains(normalized)
                || normalized.contains("password")
                || normalized.contains("token")
                || normalized.contains("secret");
    }
}

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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Masks secret connection-parameter values on public reads. Stored values are left unchanged.
 */
public final class SensitiveParameterMask {

    public static final String MASK = "***";

    private static final List<String> SENSITIVE_FRAGMENTS = List.of(
            "password",
            "token",
            "secret",
            "credential",
            "authentication",
            "authorization",
            "apikey",
            "privatekey");

    private static final Pattern SEPARATORS = Pattern.compile("[-_. ]");

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
     * A key is sensitive when, ignoring case and the separators {@code -}, {@code _}, {@code .} and space, it
     * contains {@code password}, {@code token}, {@code secret}, {@code credential}, {@code authentication},
     * {@code authorization}, {@code apikey} or {@code privatekey}, or when it is {@code auth} or ends with it.
     */
    public static boolean isSensitive(String key) {
        if (key == null) {
            return false;
        }
        String normalized = SEPARATORS.matcher(key.toLowerCase(Locale.ROOT)).replaceAll("");
        return normalized.endsWith("auth") || SENSITIVE_FRAGMENTS.stream().anyMatch(normalized::contains);
    }
}

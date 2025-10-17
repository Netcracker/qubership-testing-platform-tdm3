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

package org.qubership.atp.tdm.repo.impl;

import java.util.List;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;

public enum SystemColumns {
    ROW_ID("ROW_ID"),
    SELECTED("SELECTED"),
    CREATED_WHEN("CREATED_WHEN"),
    OCCUPIED_BY("OCCUPIED_BY"),
    OCCUPIED_DATE("OCCUPIED_DATE");

    private final String name;

    SystemColumns(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public static List<SystemColumns> asList() {
        return Lists.newArrayList(values());
    }

    public static List<String> getColumnNames() {
        return asList().stream()
                .map(SystemColumns::getName)
                .collect(Collectors.toList());
    }
}

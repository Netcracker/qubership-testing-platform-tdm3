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

package org.qubership.atp.tdm.env.configurator.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.qubership.atp.tdm.env.configurator.model.envgen.YamlEnvironment;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class CacheService {
    private final Map<UUID, YamlEnvironment> envGenCache = new ConcurrentHashMap();

    public void put(YamlEnvironment yamlEnvironment) {
        envGenCache.put(yamlEnvironment.getId(), yamlEnvironment);
    }

    public YamlEnvironment get(UUID environmentId) {
        return envGenCache.get(environmentId);
    }

    public List<YamlEnvironment> getEnvironments() {
        return envGenCache.values().stream().collect(Collectors.toList());
    }
}

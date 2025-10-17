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

import java.util.HashMap;
import java.util.Map;

import org.qubership.atp.tdm.repo.CatalogRepository;
import org.qubership.atp.tdm.service.DeploymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class DeploymentServiceImpl implements DeploymentService {

    private final CatalogRepository catalogRepository;

    @Autowired
    public DeploymentServiceImpl(@Nonnull CatalogRepository catalogRepository) {
        this.catalogRepository = catalogRepository;
    }

    /**
     * Liveness probe for openshift.
     *
     * @return - map with liveness status.
     */
    public Map<String, String> liveness() {
        catalogRepository.findAll();
        Map<String, String> map = new HashMap<>();
        map.put("type", "liveness");
        map.put("status", "true");
        log.trace("Liveness probe success.");
        return map;
    }

    /**
     * Readiness probe for openshift.
     *
     * @return - map with readiness status.
     */
    public Map<String, String> readiness() {
        catalogRepository.findAll();
        Map<String, String> map = new HashMap<>();
        map.put("type", "readiness");
        map.put("status", "true");
        log.trace("Readiness probe success.");
        return map;
    }
}

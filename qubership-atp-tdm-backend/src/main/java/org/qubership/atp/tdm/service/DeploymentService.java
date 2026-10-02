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

package org.qubership.atp.tdm.service;

import java.util.Map;

/**
 * Liveness and readiness for the deployment probes, both backed by a read of the table catalog.
 */
public interface DeploymentService {

    /**
     * Reads the table catalog and returns {@code {"type": "liveness", "status": "true"}}, or throws if the catalog
     * is not reachable.
     */
    Map<String, String> liveness();

    /**
     * Reads the table catalog and returns {@code {"type": "readiness", "status": "true"}}, or throws if the catalog
     * is not reachable.
     */
    Map<String, String> readiness();
}

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

import org.qubership.atp.tdm.AbstractTest;
import org.qubership.atp.tdm.service.DeploymentService;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.Map;

public class DeploymentServiceTest extends AbstractTest {

    @Autowired
    private DeploymentService deploymentService;

    @Test
    public void deploymentService_liveness_returnsLiveness() {
        Map<String, String> expected = new HashMap<>();
        expected.put("type", "liveness");
        expected.put("status", "true");

        Map<String, String> actual = deploymentService.liveness();

        Assertions.assertEquals(expected, actual);
    }

    @Test
    public void deploymentService_readiness_returnsReadiness() {
        Map<String, String> expected = new HashMap<>();
        expected.put("type", "readiness");
        expected.put("status", "true");

        Map<String, String> actual = deploymentService.readiness();

        Assertions.assertEquals(expected, actual);
    }
}

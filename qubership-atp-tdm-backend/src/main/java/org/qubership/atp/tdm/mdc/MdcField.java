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

package org.qubership.atp.tdm.mdc;

public enum MdcField {
    PROJECT_ID("projectId"),
    EXECUTION_REQUEST_ID("executionRequestId"),
    TEST_RUN_ID("testRunId"),
    ENVIRONMENT_ID("environmentId"),
    SYSTEM_ID("systemId");


    private String name;

    MdcField(String type) {
        this.name = type;
    }

    @Override
    public String toString() {
        return this.name;
    }
}

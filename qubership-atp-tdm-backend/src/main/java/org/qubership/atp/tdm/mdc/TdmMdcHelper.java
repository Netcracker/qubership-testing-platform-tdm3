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

import org.qubership.atp.integration.configuration.mdc.MdcUtils;
import org.qubership.atp.tdm.model.TestDataTableCatalog;
import org.qubership.atp.tdm.service.notification.environments.EnvironmentEvent;
import org.qubership.atp.tdm.service.notification.systems.SystemEvent;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

@Component
public class TdmMdcHelper {

    public void putConfigFields(TestDataTableCatalog catalog) {
        MdcUtils.put(MdcField.ENVIRONMENT_ID.toString(), catalog.getEnvironmentId());
        MdcUtils.put(MdcField.SYSTEM_ID.toString(), catalog.getSystemId());
    }

    public void removeConfigFields() {
        MDC.remove(MdcField.ENVIRONMENT_ID.toString());
        MDC.remove(MdcField.SYSTEM_ID.toString());
    }

    public void putEnvironmentEventFields(EnvironmentEvent event) {
        MdcUtils.put(MdcField.ENVIRONMENT_ID.toString(), event.getId());
        MdcUtils.put(MdcField.PROJECT_ID.toString(), event.getProjectId());
    }

    public void putSystemEventFields(SystemEvent event) {
        MdcUtils.put(MdcField.SYSTEM_ID.toString(), event.getId());
        MdcUtils.put(MdcField.PROJECT_ID.toString(), event.getProjectId());
    }
}

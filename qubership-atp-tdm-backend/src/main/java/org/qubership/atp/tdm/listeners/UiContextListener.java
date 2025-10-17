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

package org.qubership.atp.tdm.listeners;

import java.util.Map;
import java.util.UUID;

import org.qubership.atp.tdm.model.ProjectInformation;
import org.qubership.atp.tdm.repo.ProjectInformationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class UiContextListener {

    private final ApplicationContext myContext;
    private final ProjectInformationRepository projectInformationRepository;

    @Value("#{${projects.info}}")
    private Map<UUID, String> projects;

    @Value("${projects.time.zone}")
    private String timeZone;
    @Value("${projects.date.format}")
    private String dateFormat;
    @Value("${projects.time.format}")
    private String timeFormat;
    @Value("${projects.expiration.months.timeout}")
    private long expirationMonthsTimeout;

    /**
     * Handles application context refresh events and initializes project information.
     * This method is triggered when the Spring application context is refreshed and
     * ensures that project information is properly inserted into the repository
     * for the current context only.
     *
     * @param event the context refresh event containing information about the refreshed context
     */
    @EventListener
    public void init(ContextRefreshedEvent event) {
        if (event.getSource().equals(myContext)) {
            insertProjectInformation();
        }
    }

    private void insertProjectInformation() {
        for (Map.Entry<UUID, String> entry : projects.entrySet()) {
            projectInformationRepository.save(new ProjectInformation(
                    entry.getKey(), timeZone, dateFormat,
                    timeFormat, expirationMonthsTimeout));
            log.info("Project information inserted successfully [{}/{}].", entry.getKey(), entry.getValue());
        }
    }
}

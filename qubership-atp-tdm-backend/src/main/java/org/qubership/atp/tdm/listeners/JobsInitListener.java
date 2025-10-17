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

import org.qubership.atp.tdm.service.impl.CleanupServiceImpl;
import org.qubership.atp.tdm.service.impl.DataRefreshServiceImpl;
import org.qubership.atp.tdm.service.impl.StatisticsServiceImpl;
import org.qubership.atp.tdm.service.impl.TestDataServiceImpl;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@AllArgsConstructor
@Component
public class JobsInitListener {

    private final StatisticsServiceImpl statisticsService;
    private final DataRefreshServiceImpl dataRefreshService;
    private final CleanupServiceImpl cleanupService;
    private final TestDataServiceImpl testDataService;

    /**
     * Run job init.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void runJobsInit() {
        statisticsService.startUsersStatisticsMonitoring();
        statisticsService.startStatisticsMonitoring();
        dataRefreshService.initSchedules();
        cleanupService.initSchedules();
        statisticsService.startAvailableDataStatsMonitoring();
        testDataService.schedule();
    }
}

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

package org.qubership.atp.tdm.model.scheduler;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.qubership.atp.integration.configuration.mdc.MdcField;
import org.qubership.atp.integration.configuration.mdc.MdcUtils;
import org.qubership.atp.tdm.service.mailsender.StatisticsMailSender;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@DisallowConcurrentExecution
public class StatisticsMailJob implements Job {

    @Autowired
    private StatisticsMailSender statisticsMailSender;

    @Override
    public void execute(JobExecutionContext context) {
        MDC.clear();
        String projectId = context.getJobDetail().getKey().getName();
        try {
            MdcUtils.put(MdcField.PROJECT_ID.toString(), projectId);
            log.info("Running scheduled monitoring statistics job for project: {}", projectId);
            statisticsMailSender.send(projectId);
        } catch (Exception e) {
            log.error("An error occurred while running monitoring statistics job for project: {}", projectId, e);
        }
    }
}

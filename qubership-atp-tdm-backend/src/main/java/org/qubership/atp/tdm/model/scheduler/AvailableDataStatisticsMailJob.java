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

import java.util.UUID;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.qubership.atp.tdm.service.mailsender.AvailableDataStatisticsMailSender;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@DisallowConcurrentExecution
public class AvailableDataStatisticsMailJob implements Job {

    @Autowired
    private AvailableDataStatisticsMailSender mailSender;

    @Override
    public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {
        String[] key = jobExecutionContext.getJobDetail().getKey().getName().split(";", 2);
        try {
            log.info("Sending available data statistic for system: {}.", key[0]);
            mailSender.send(UUID.fromString(key[0]), UUID.fromString(key[1]));
        } catch (Exception e) {
            log.error("An error occurred while running available data monitoring statistics job for system: {}",
                    key[0], e);
        }
    }
}

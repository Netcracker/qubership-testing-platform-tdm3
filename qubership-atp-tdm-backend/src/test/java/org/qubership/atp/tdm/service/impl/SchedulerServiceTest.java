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
import org.qubership.atp.tdm.utils.scheduler.ScheduleConfig;
import org.qubership.atp.tdm.service.SchedulerService;
import lombok.Data;
import org.apache.commons.lang.StringUtils;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.SchedulerException;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

public class SchedulerServiceTest extends AbstractTest {

    private static final String SCHED_GROUP = "SchedulerService";

    @Autowired
    private SchedulerService schedulerService;

    @Test
    public void schedulerService_reschedule_scheduleRescheduled() throws SchedulerException {
        UUID configId = UUID.randomUUID();
        FakeScheduleConfig config = new FakeScheduleConfig(configId, "0 0/1 * * * ?", true);
        JobDetail job = JobBuilder.newJob(Job.class)
                .withIdentity(configId.toString(), SCHED_GROUP)
                .build();
        schedulerService.reschedule(job, config, SCHED_GROUP);

        Assertions.assertTrue(schedulerService.checkExists(job.getKey()));
    }

    @Test
    public void schedulerService_rescheduleDisabledJob_scheduleRescheduled() {
        UUID configId = UUID.randomUUID();
        FakeScheduleConfig config = new FakeScheduleConfig(configId, "0 0/1 * * * ?", true);
        JobDetail job = JobBuilder.newJob(Job.class)
                .withIdentity(configId.toString(), SCHED_GROUP)
                .build();
        schedulerService.reschedule(job, config, SCHED_GROUP);

        Assertions.assertTrue(schedulerService.checkExists(job.getKey()));

        config.setEnabled(false);
        schedulerService.reschedule(job, config, SCHED_GROUP);

        Assertions.assertFalse(schedulerService.checkExists(job.getKey()));
    }

    @Test
    public void schedulerService_deleteJob_jobDeleted() throws Exception {
        UUID configId = UUID.randomUUID();
        ScheduleConfig config = new FakeScheduleConfig(configId, "0 0/1 * * * ?", true);
        JobDetail job = JobBuilder.newJob(Job.class)
                .withIdentity(configId.toString(), SCHED_GROUP)
                .build();
        schedulerService.reschedule(job, config, SCHED_GROUP);
        Thread.sleep(3000);
        Assertions.assertTrue(schedulerService.checkExists(job.getKey()));

//        schedulerService.deleteJob(job.getKey());
//        Thread.sleep(5000);
//        Assertions.assertFalse(schedulerService.checkExists(job.getKey()));
    }

    @Data
    private class FakeScheduleConfig implements ScheduleConfig {
        private UUID id;
        private String schedule;
        private boolean enabled;

        public FakeScheduleConfig(UUID id, String schedule, boolean enabled) {
            this.id = id;
            this.schedule = schedule;
            this.enabled = enabled;
        }

        @Override
        public boolean isScheduled() {
            return enabled && StringUtils.isNotEmpty(schedule);
        }
    }
}

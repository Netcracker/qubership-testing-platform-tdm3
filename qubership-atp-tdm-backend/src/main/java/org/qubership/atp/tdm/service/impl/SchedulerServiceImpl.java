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

import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import org.quartz.CronScheduleBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.quartz.impl.matchers.GroupMatcher;
import org.qubership.atp.tdm.service.SchedulerService;
import org.qubership.atp.tdm.utils.scheduler.ScheduleConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class SchedulerServiceImpl implements SchedulerService {

    private final Scheduler scheduler;

    /**
     * Scheduler service constructor.
     */
    @Autowired
    public SchedulerServiceImpl(@Qualifier("scheduler") Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    /**
     * Reschedules tasks.
     * @param job  job to be scheduled
     * @param config  object with schedule details
     * @param group schedule group
     */

    @Override
    public void reschedule(@Nonnull JobDetail job, @Nonnull ScheduleConfig config, @Nonnull String group,
                           @Nonnull String identityName) {
        Trigger trigger = Optional.of(TriggerBuilder.newTrigger()
                        .withIdentity(identityName, group))
                .map(builder -> config.isScheduled()
                        ? builder.withSchedule(CronScheduleBuilder.cronSchedule(config.getSchedule()))
                        : builder
                )
                .get()
                .build();
        reschedule(job, trigger, config.isScheduled());
        log.info("Job with id [" + identityName + "] and group [" + group + "] was scheduled");
    }

    @Override
    public void reschedule(@Nonnull JobDetail job, @Nonnull ScheduleConfig config, @Nonnull String group) {
        reschedule(job, config, group, config.getId().toString());
    }

    /**
     * Manages tasks (add/update/delete).
     * @param job  job to be scheduled
     * @param trigger  trigger to be added to the job
     * @param turnOn whether this task needs to be added/updated or deleted by scheduler
     */
    @Transactional
    @Override
    public void reschedule(@Nonnull JobDetail job, @Nonnull Trigger trigger, boolean turnOn) {
        if (schedulerIsEnabled()) {
            try {
                if (!scheduler.isStarted() || scheduler.isInStandbyMode()) {
                    scheduler.start();
                }
                if (scheduler.checkExists(job.getKey())) {
                    if (turnOn) {
                        scheduler.rescheduleJob(trigger.getKey(), trigger);
                    } else {
                        scheduler.deleteJob(job.getKey());
                        Set<TriggerKey> triggers = scheduler.getTriggerKeys(GroupMatcher.anyGroup());
                        Predicate<TriggerKey> futureTrigger = (key) -> {
                            try {
                                return scheduler.getTrigger(key).mayFireAgain();
                            } catch (SchedulerException e) {
                                return true;
                            }
                        };
                        if (triggers.isEmpty() || triggers.stream().noneMatch(futureTrigger)) {
                            scheduler.standby();
                        }
                    }
                } else if (turnOn) {
                    scheduler.scheduleJob(job, trigger);
                }
            } catch (SchedulerException e) {
                log.error("An error occurred while rescheduling a job", e);
            }
        } else {
            log.warn("Cannot reschedule a job since scheduler isn't serviceable");
        }
    }

    /**
     * delete job.
     * @param jobKey key of the job
     * */
    @Transactional
    @Override
    public void deleteJob(@Nonnull JobKey jobKey) {
        if (schedulerIsEnabled()) {
            try {
                if (scheduler.checkExists(jobKey)) {
                    scheduler.deleteJob(jobKey);
                }
            } catch (SchedulerException e) {
                log.error("An error occurred while removing a job", e);
            }
        } else {
            log.warn("Cannot remove a job since scheduler isn't serviceable");
        }
    }

    @Override
    public boolean checkExists(@Nonnull JobKey jobKey) {
        if (schedulerIsEnabled()) {
            try {
                return scheduler.checkExists(jobKey);
            } catch (SchedulerException e) {
                log.error("An error occurred while checking a job", e);
            }
        } else {
            log.warn("Cannot check a job since scheduler isn't serviceable");
        }
        return false;
    }

    private boolean schedulerIsEnabled() {
        try {
            return scheduler != null && scheduler.isStarted();
        } catch (SchedulerException e) {
            log.error("Error while scheduler check", e);
            return false;
        }
    }
}

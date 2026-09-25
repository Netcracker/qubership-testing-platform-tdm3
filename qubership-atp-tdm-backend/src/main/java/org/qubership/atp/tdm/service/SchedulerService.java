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

import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Trigger;
import org.qubership.atp.tdm.utils.scheduler.ScheduleConfig;

import jakarta.annotation.Nonnull;

/**
 * Adds, updates, and removes Quartz jobs on behalf of the cleanup, refresh, and statistics schedules.
 */
public interface SchedulerService {

    /**
     * Builds a cron trigger, identified by {@code identityName} and {@code group}, from {@code config}'s schedule,
     * and passes it to {@link #reschedule(JobDetail, Trigger, boolean)} with {@code turnOn} set to
     * {@link ScheduleConfig#isScheduled()}.
     */
    void reschedule(@Nonnull JobDetail job, @Nonnull ScheduleConfig config, @Nonnull String group,
                    @Nonnull String identityName);

    /**
     * Same as {@link #reschedule(JobDetail, ScheduleConfig, String, String)}, identified by
     * {@link ScheduleConfig#getId()} instead of an explicit name.
     */
    void reschedule(@Nonnull JobDetail job, @Nonnull ScheduleConfig config, @Nonnull String group);

    /**
     * Adds or updates {@code job} with {@code trigger} in Quartz when {@code turnOn} is set, and removes it
     * otherwise. Does nothing if the scheduler is disabled or not running.
     */
    void reschedule(@Nonnull JobDetail job, @Nonnull Trigger trigger, boolean turnOn);

    /**
     * Removes {@code jobKey}'s job from Quartz if it exists; does nothing otherwise.
     */
    void deleteJob(@Nonnull JobKey jobKey);

    boolean checkExists(@Nonnull JobKey jobKey);
}

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

public interface SchedulerService {

    void reschedule(@Nonnull JobDetail job, @Nonnull ScheduleConfig config, @Nonnull String group,
                    @Nonnull String identityName);

    void reschedule(@Nonnull JobDetail job, @Nonnull ScheduleConfig config, @Nonnull String group);

    void reschedule(@Nonnull JobDetail job, @Nonnull Trigger trigger, boolean turnOn);

    void deleteJob(@Nonnull JobKey jobKey);

    boolean checkExists(@Nonnull JobKey jobKey);
}

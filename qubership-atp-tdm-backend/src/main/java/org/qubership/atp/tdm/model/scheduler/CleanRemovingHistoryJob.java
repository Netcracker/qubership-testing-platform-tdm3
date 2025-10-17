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

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.qubership.atp.tdm.repo.DeletedTablesHistoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class CleanRemovingHistoryJob implements Job {

    @Autowired
    private DeletedTablesHistoryRepository deletedTablesHistoryRepository;

    @Value("${default.clean.removed.tables.months}")
    private int defaultHistoryExpirationMonths;

    private static Date minusMonths(Date dt, long days) {
        LocalDate localDate = dt.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate newDate = localDate.minusMonths(days);
        return Date.from(newDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    @Override
    public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {
        Date dt = new Date();
        deletedTablesHistoryRepository.deleteByDeleteDateBefore(minusMonths(dt, defaultHistoryExpirationMonths));
    }
}

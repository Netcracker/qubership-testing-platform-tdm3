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
import java.util.List;
import java.util.stream.Collectors;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.qubership.atp.tdm.model.DeletedTablesHistory;
import org.qubership.atp.tdm.model.ProjectInformation;
import org.qubership.atp.tdm.model.TestDataTableCatalog;
import org.qubership.atp.tdm.repo.CatalogRepository;
import org.qubership.atp.tdm.repo.DeletedTablesHistoryRepository;
import org.qubership.atp.tdm.repo.ProjectInformationRepository;
import org.qubership.atp.tdm.service.TestDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class TableCleanerJob implements Job {

    @Autowired
    private CatalogRepository catalogRepository;

    @Autowired
    private ProjectInformationRepository projectInformationRepository;

    @Autowired
    private TestDataService testDataService;

    @Autowired
    private DeletedTablesHistoryRepository deletedTablesHistoryRepository;

    @Value("${default.table.expiration.months:1}")
    private int defaultExpirationMonths;

    private static Date minusMonths(Date dt, long month) {
        LocalDate localDate = dt.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate newDate = localDate.minusMonths(month);
        return Date.from(newDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    @Override
    public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {
        Date dt = new Date();
        List<ProjectInformation> projectInformationList = projectInformationRepository.findAll();
        if (projectInformationList.isEmpty()) {
            catalogRepository.findByLastUsageBefore(minusMonths(dt, defaultExpirationMonths))
                    .forEach(table -> deleteTable(table, dt));
        } else {
            projectInformationRepository.findAll().forEach(project -> {
                        long expirationMonths = project.getExpirationMonthsTimeout() == 0
                                ? (long) defaultExpirationMonths : project.getExpirationMonthsTimeout();
                        catalogRepository.findByProjectIdAndLastUsageBefore(project.getProjectId(), minusMonths(dt,
                                expirationMonths)).forEach(table -> deleteTable(table, dt));
                    }
            );
            catalogRepository.findByProjectIdNotInAndLastUsageBefore(
                    projectInformationRepository.findAll().stream().map(ProjectInformation::getProjectId)
                            .collect(Collectors.toList()),
                    minusMonths(dt, defaultExpirationMonths)).forEach(table -> deleteTable(table, dt));
        }
    }

    private void deleteTable(@NonNull TestDataTableCatalog table, Date date) {
        log.info("Delete expired table: name:[{}], title:[{}], env:[{}], system[{}], project[{}], "
                        + "last usage [{}]", table.getTableName(), table.getTableTitle(),
                table.getEnvironmentId(), table.getSystemId(), table.getProjectId(),
                table.getLastUsage());
        testDataService.deleteTestData(table.getTableName());
        deletedTablesHistoryRepository.save(new DeletedTablesHistory(table.getTableName(), table.getProjectId(),
                table.getTableTitle(), table.getSystemId(), table.getEnvironmentId(), date));
    }
}

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

package org.qubership.atp.tdm.model.statistics;

import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.qubership.atp.tdm.utils.scheduler.ScheduleConfig;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;


@Schema(description = "Users-occupation-report schedule of one project: a periodic email of who occupied which "
        + "rows.")
@Data
@EqualsAndHashCode
@ToString
@Entity
public class TestDataTableUsersMonitoring implements ScheduleConfig {

    @Schema(description = "Project ID.")
    @Id
    @Column(name = "project_id")
    private UUID projectId;
    @Schema(description = "Sends the report on the schedule when set.")
    @Column(name = "enabled")
    private boolean enabled;
    @Schema(description = "Quartz cron expression, starting with the seconds field.")
    @Column(name = "cron_expression")
    private String cronExpression;
    @Schema(description = "Comma-separated email addresses to notify.")
    @Column(name = "recipients")
    private String recipients;
    @Schema(description = "Includes an HTML table of the occupations in the email body.")
    @Column(name = "html_report")
    private boolean htmlReport;
    @Schema(description = "Attaches a CSV file of the occupations to the email.")
    @Column(name = "csv_report")
    private boolean csvReport;
    @Schema(description = "Number of past days the report covers.")
    @Column(name = "days_count")
    private int daysCount;

    @Override
    public UUID getId() {
        return projectId;
    }

    @Override
    public String getSchedule() {
        return cronExpression;
    }

    @Override
    public boolean isScheduled() {
        return enabled && StringUtils.isNotEmpty(cronExpression);
    }
}

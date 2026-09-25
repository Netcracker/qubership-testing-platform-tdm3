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
import org.codehaus.jackson.annotate.JsonIgnore;
import org.qubership.atp.tdm.utils.scheduler.ScheduleConfig;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Transient;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Schema(description = "General-statistics monitoring schedule of one project: a periodic email when a table's "
        + "available-row count drops below a threshold.")
@Data
@EqualsAndHashCode
@ToString
@Entity
public class TestDataTableMonitoring implements ScheduleConfig {
    @Schema(description = "Project ID.")
    @Id
    @Column(name = "project_id")
    private UUID projectId;
    @Schema(description = "Runs the check on the schedule when set.")
    @Column(name = "enabled")
    private boolean enabled;
    @Schema(description = "Quartz cron expression, starting with the seconds field.")
    @Column(name = "cron_expression")
    private String cronExpression;
    @Schema(description = "Sends the email for a table when its available count drops below this threshold.")
    @Column(name = "threshold")
    private int threshold;
    @Schema(description = "Comma-separated email addresses to notify.")
    @Column(name = "recipients")
    private String recipients;
    @Transient
    @JsonIgnore
    private UUID id;
    @Transient
    @JsonIgnore
    private String schedule;
    @Transient
    @JsonIgnore
    private String scheduled;

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

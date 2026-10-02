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

package org.qubership.atp.tdm.model.cleanup;

import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.codehaus.jackson.annotate.JsonIgnore;
import org.qubership.atp.tdm.utils.scheduler.ScheduleConfig;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Transient;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "Which rows a cleanup removes from a table, and the cron schedule to run it on.")
@Data
@Entity
public class TestDataCleanupConfig implements ScheduleConfig {
    @Schema(description = "Cleanup configuration ID. Generated when the configuration is saved.")
    @Id
    @Column(name = "id")
    private UUID id;
    @Schema(description = "Runs the cleanup on the schedule when set; a saved but disabled configuration can "
            + "still be run with POST /api/tdm/cleanup/run.")
    @Column(name = "enabled")
    private boolean enabled;
    @Schema(description = "Quartz cron expression, starting with the seconds field.")
    @Column(name = "schedule")
    private String schedule;
    @Schema(description = "Which of searchSql, searchDate, or searchClass finds the rows to remove. searchClass is accepted but always rejected in this build: no cleaner class is registered.")
    @Enumerated(EnumType.STRING)
    @Column(name = "type")
    private CleanupType type;
    @Schema(description = "Timeout of searchSql, in seconds, from 1 to EXTERNAL_QUERY_MAX_TIMEOUT "
            + "(3600 by default).")
    @Column(name = "query_timeout")
    private Integer queryTimeout;
    @Schema(description = "SQL query that returns the rows to remove. Read when type is SQL.")
    @Column(name = "search_sql")
    private String searchSql;
    @Schema(description = "Name of the cleaner class to run. Read when type is CLASS; see the note on type.")
    @Column(name = "search_class")
    private String searchClass;
    @Schema(description = "Age of the rows to remove, as a number of weeks and days before now, such as \"3w4d\". Read when type is DATE; compared against the row's CREATED_WHEN.")
    @Column(name = "search_date")
    private String searchDate;
    @Schema(description = "Applies this configuration to the tables of every environment with the same title "
            + "and system, instead of to one table.")
    @Column(name = "shared")
    private boolean shared;

    @Transient
    @JsonIgnore
    private String scheduled;

    @Override
    public boolean isScheduled() {
        return enabled && StringUtils.isNotEmpty(schedule);
    }
}

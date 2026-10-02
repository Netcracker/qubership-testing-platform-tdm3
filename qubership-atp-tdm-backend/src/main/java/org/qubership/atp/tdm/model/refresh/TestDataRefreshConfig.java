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

package org.qubership.atp.tdm.model.refresh;

import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.qubership.atp.tdm.utils.scheduler.ScheduleConfig;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Transient;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "Refresh schedule of a table.")
@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
public class TestDataRefreshConfig implements ScheduleConfig {

    @Schema(description = "Refresh configuration ID. Generated when the configuration is saved.")
    @Id
    @Column(name = "id")
    private UUID id;
    @Schema(description = "Runs the refresh on the schedule when set; a saved but disabled configuration can "
            + "still be run with POST /api/tdm/data/refresh/run.")
    @Column(name = "enabled")
    private boolean enabled;
    @Schema(description = "Quartz cron expression, starting with the seconds field.")
    @Column(name = "schedule")
    private String schedule;
    @Schema(description = "Also refreshes the tables of the project with the same title and the same import "
            + "query.")
    @Column(name = "all_env")
    private boolean allEnv;

    @Schema(description = "Not read or returned by the REST API; PUT /api/tdm/data/refresh/config sets the query "
            + "timeout through its own queryTimeout parameter instead.")
    @Transient
    private Integer queryTimout;

    public boolean isScheduled() {
        return enabled && StringUtils.isNotEmpty(schedule);
    }
}

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

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.hibernate.proxy.HibernateProxy;
import org.qubership.atp.tdm.utils.scheduler.ScheduleConfig;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "Available-data monitoring schedule of one system's environment: a periodic email when a "
        + "chosen column's available-row count drops below a threshold.")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@IdClass(SystemEnvironmentModel.class)
public class TestAvailableDataMonitoring implements ScheduleConfig, Serializable {

    private static final long serialVersionUID = 3055608819732633976L;

    @Schema(description = "System ID.")
    @Id
    @Column(name = "system_id")
    private UUID systemId;
    @Schema(description = "Environment ID.")
    @Id
    @Column(name = "environment_id")
    private UUID environmentId;
    @Schema(description = "Runs the check on the schedule when set.")
    @Column(name = "scheduled")
    private boolean scheduled;
    @Schema(description = "Quartz cron expression, starting with the seconds field.")
    @Column(name = "schedule")
    private String schedule;
    @Schema(description = "Comma-separated email addresses to notify.")
    @Column(name = "recipients")
    private String recipients;
    @Schema(description = "Sends the email when the available count drops below this threshold.")
    @Column(name = "threshold")
    private int threshold;
    @Schema(description = "Name of the monitored column, or an empty string when none is chosen yet.")
    @Column(name = "description")
    private String description;
    @Schema(description = "Same value as description: name of the monitored column.")
    @Column(name = "active_column")
    private String activeColumn;

    @Transient
    @JsonIgnore
    private UUID id;

    public TestAvailableDataMonitoring(UUID systemId, UUID environmentId) {
        this.systemId = systemId;
        this.environmentId = environmentId;
    }

    @Override
    public UUID getId() {
        return systemId;
    }

    @Override
    public boolean isScheduled() {
        return scheduled && StringUtils.isNotEmpty(schedule);
    }

    @Override
    public final boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        Class<?> objectEffectiveClass = obj instanceof HibernateProxy
                ? ((HibernateProxy) obj).getHibernateLazyInitializer().getPersistentClass() : obj.getClass();
        Class<?> thisEffectiveClass = this instanceof HibernateProxy
                ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffectiveClass != objectEffectiveClass) {
            return false;
        }
        TestAvailableDataMonitoring that = (TestAvailableDataMonitoring) obj;

        return getSystemId() != null && Objects.equals(getSystemId(), that.getSystemId())
                && getEnvironmentId() != null && Objects.equals(getEnvironmentId(), that.getEnvironmentId());
    }

    @Override
    public final int hashCode() {
        return Objects.hash(systemId, environmentId);
    }
}

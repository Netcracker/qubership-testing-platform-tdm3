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

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.qubership.atp.tdm.model.TestDataOccupyStatistic;
import org.qubership.atp.tdm.model.TestDataTableCatalog;
import org.qubership.atp.tdm.model.statistics.AvailableDataStatisticsConfig;
import org.qubership.atp.tdm.model.statistics.ConsumedStatistics;
import org.qubership.atp.tdm.model.statistics.DateStatistics;
import org.qubership.atp.tdm.model.statistics.GeneralStatisticsItem;
import org.qubership.atp.tdm.model.statistics.OutdatedStatistics;
import org.qubership.atp.tdm.model.statistics.TestAvailableDataMonitoring;
import org.qubership.atp.tdm.model.statistics.TestDataTableMonitoring;
import org.qubership.atp.tdm.model.statistics.TestDataTableUsersMonitoring;
import org.qubership.atp.tdm.model.statistics.UsersOccupyStatisticRequest;
import org.qubership.atp.tdm.model.statistics.UsersOccupyStatisticResponse;
import org.qubership.atp.tdm.model.statistics.available.AvailableDataByColumnStats;
import org.qubership.atp.tdm.model.statistics.report.StatisticsReportObject;
import org.qubership.atp.tdm.model.statistics.report.UsersStatisticsReportObject;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * Statistics on test data tables, and the scheduled email reports and their configuration.
 */
public interface StatisticsService {

    /**
     * Returns the {@code test.data.initial.threshold} property: the default available-rows threshold to suggest
     * for a new monitoring schedule, not a saved schedule's own threshold.
     */
    int getThreshold();

    List<GeneralStatisticsItem> getTestDataAvailability(@Nonnull UUID projectId, @Nullable UUID systemId);

    ConsumedStatistics getTestDataConsumption(@Nonnull UUID projectId, @Nullable UUID systemId,
                                              @Nonnull LocalDate dateFrom, @Nonnull LocalDate dateTo);

    OutdatedStatistics getTestDataConsumptionWhitOutdated(@Nonnull UUID projectId, @Nullable UUID systemId,
                                                          @Nonnull LocalDate dateFrom, @Nonnull LocalDate dateTo,
                                                          int expirationDate);

    DateStatistics getTestDataCreatedWhen(@Nonnull UUID projectId, @Nullable UUID systemId,
                                          @Nonnull LocalDate dateFrom, @Nonnull LocalDate dateTo);

    TestDataTableMonitoring getMonitoringSchedule(@Nonnull UUID projectId);

    void saveMonitoringSchedule(@Nonnull TestDataTableMonitoring monitoringItem);

    void deleteMonitoringSchedule(@Nonnull TestDataTableMonitoring monitoringItem);

    TestDataTableUsersMonitoring getUsersMonitoringSchedule(@Nonnull UUID projectId);

    void saveUsersMonitoringSchedule(@Nonnull TestDataTableUsersMonitoring monitoringItem);

    void deleteUsersMonitoringSchedule(@Nonnull TestDataTableUsersMonitoring monitoringItem);

    String getNextScheduledRun(@Nonnull String cronExpression) throws ParseException;

    StatisticsReportObject getTestDataMonitoringStatistics(@Nonnull UUID projectId, int threshold);

    /**
     * Deletes every general-statistics monitoring schedule whose project has no table left in the catalog.
     */
    void removeUnused();

    /**
     * Adds the {@code OCCUPIED_DATE} column to every test data table that lacks it, and returns their names. A
     * migration for tables created before the column existed.
     */
    List<String> alterOccupiedDateColumn();

    void saveOccupyStatistic(@Nonnull TestDataOccupyStatistic testDataOccupyStatistic);

    void deleteAllOccupyStatisticByRowId(@Nonnull List<UUID> rows);

    /**
     * Records an occupation statistic for every row of {@code tableName}.
     */
    void fillCreatedWhenStatistics(@Nonnull String tableName, @Nonnull TestDataTableCatalog catalog);

    /**
     * Same as {@link #fillCreatedWhenStatistics(String, TestDataTableCatalog)}, for {@code rows} only.
     */
    void fillCreatedWhenStatistics(@Nonnull String tableName, @Nonnull TestDataTableCatalog catalog,
                                   @Nonnull List<UUID> rows);

    UsersStatisticsReportObject getUsersStatisticsReport(TestDataTableUsersMonitoring testDataTableUsersMonitoring);

    UsersOccupyStatisticResponse getOccupiedDataByUsers(@Nonnull UsersOccupyStatisticRequest request);

    /**
     * Builds a CSV of who occupied which rows over the last {@code days} days, one column per date. Covers only the
     * first 100 records {@link #getOccupiedDataByUsers} returns for that period, not every occupation.
     */
    File getCsvReportByUsers(UUID projectId, int days) throws IOException;

    /**
     * Returns every column name common to the system's tables as {@code columnKeys}, plus the system's saved
     * {@code activeColumnKey} and per-table value filter where a monitoring configuration is saved for it.
     */
    AvailableDataStatisticsConfig getAvailableStatsConfig(@Nonnull UUID systemId, @Nonnull UUID environmentId);

    void saveAvailableStatsConfig(@Nonnull AvailableDataStatisticsConfig config);

    /**
     * Counts, for every table of the system, the available rows for each value of the saved
     * {@link AvailableDataStatisticsConfig#getActiveColumnKey()}, restricted to the saved
     * {@link AvailableDataStatisticsConfig#getTablesColumns()} filter where one exists for a table.
     */
    AvailableDataByColumnStats getAvailableDataInColumn(@Nonnull UUID systemId, @Nonnull UUID environmentId);

    TestAvailableDataMonitoring getAvailableDataMonitoringConfig(@Nonnull UUID systemId, @Nonnull UUID environmentId);

    void saveAvailableDataMonitoringConfig(@Nonnull TestAvailableDataMonitoring monitoringConfig) throws Exception;

    void deleteAvailableDataMonitoringConfig(@Nonnull UUID systemId, @Nonnull UUID environmentId);

    void updateOccupiedStatisticsTableTitle(@Nonnull String tableName, @Nonnull String tableTitle);
}

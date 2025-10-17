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

package org.qubership.atp.tdm.repo;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.qubership.atp.tdm.model.TestDataOccupyStatistic;
import org.qubership.atp.tdm.model.TestDataTableCatalog;
import org.qubership.atp.tdm.model.statistics.ConsumedStatistics;
import org.qubership.atp.tdm.model.statistics.DateStatistics;
import org.qubership.atp.tdm.model.statistics.GeneralStatisticsItem;
import org.qubership.atp.tdm.model.statistics.OutdatedStatistics;
import org.qubership.atp.tdm.model.statistics.report.StatisticsReport;

import jakarta.annotation.Nonnull;

public interface StatisticsRepository {

    List<GeneralStatisticsItem> getTestDataAvailability(@Nonnull List<TestDataTableCatalog> catalogList,
                                                        @Nonnull UUID projectId);

    ConsumedStatistics getTestDataConsumption(@Nonnull List<TestDataOccupyStatistic> occupyStatisticList,
                                              @Nonnull UUID projectId, @Nonnull LocalDate dateFrom,
                                              @Nonnull LocalDate dateTo);

    OutdatedStatistics getTestDataOutdatedConsumption(@Nonnull List<TestDataTableCatalog> catalogList,
                                                      @Nonnull UUID projectId, @Nonnull LocalDate dateFrom,
                                                      @Nonnull LocalDate dateTo, int expirationDate);

    DateStatistics getTestDataCreatedWhen(@Nonnull List<TestDataOccupyStatistic> occupyStatisticList,
                                          @Nonnull UUID projectId, @Nonnull LocalDate dateFrom,
                                          @Nonnull LocalDate dateTo);

    List<StatisticsReport> getTestDataMonitoringStatistics(@Nonnull List<TestDataTableCatalog> catalogList,
                                                           @Nonnull UUID projectId);

    List<String> alterOccupiedDateColumn(List<String> tableNames);
}

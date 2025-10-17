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

package org.qubership.atp.tdm.benchmarks.facades;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import org.qubership.atp.tdm.model.statistics.ConsumedStatistics;
import org.qubership.atp.tdm.model.statistics.GeneralStatisticsItem;
import org.qubership.atp.tdm.repo.TestDataTableRepository;
import org.qubership.atp.tdm.service.StatisticsService;
import org.qubership.atp.tdm.service.TestDataService;

public class StatisticsFacade extends GeneralFacade {

    private final StatisticsService statisticsService;

    public StatisticsFacade(@Nonnull TestDataService testDataService,
                            @Nonnull TestDataTableRepository testDataTableRepository,
                            @Nonnull StatisticsService statisticsService) {
        super(testDataService, testDataTableRepository);
        this.statisticsService = statisticsService;
    }

    public List<GeneralStatisticsItem> getTestDataAvailability(@Nonnull UUID projectId, @Nullable UUID systemId) {
        return statisticsService.getTestDataAvailability(projectId, systemId);
    }

    public ConsumedStatistics getTestDataConsumption(@Nonnull UUID projectId, @Nullable UUID systemId) {
        return statisticsService.getTestDataConsumption(projectId, systemId,
                LocalDate.now(), LocalDate.now().plusDays(1));
    }
}

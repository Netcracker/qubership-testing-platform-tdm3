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

package org.qubership.atp.tdm.repo.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.qubership.atp.tdm.model.TestDataOccupyStatistic;
import org.qubership.atp.tdm.model.statistics.ConsumedStatistics;
import org.qubership.atp.tdm.repo.ProjectInformationRepository;
import org.qubership.atp.tdm.repo.impl.extractors.TestDataExtractorProvider;
import org.qubership.atp.tdm.utils.DataUtils;
import org.qubership.atp.tdm.utils.TestDataQueries;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

/**
 * Fails when a statistics request splits its values by the periods of another request.
 *
 * <p>The values of a request must be split by the periods of its own date range, one value per date label. The
 * period length used to be kept in a static field that every request overwrote, so a request that ran between
 * another request's labels and its values changed how those values were split.</p>
 */
class StatisticsRepositoryIntervalTest {

    private static final LocalDate DATE_FROM = LocalDate.of(2026, 9, 1);
    private static final LocalDate DATE_TO = LocalDate.of(2026, 9, 3);

    @Test
    void getTestDataConsumption_otherRequestInBetween_keepsOwnDailyPeriods() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        TestDataExtractorProvider extractorProvider = mock(TestDataExtractorProvider.class);
        List<Map<LocalDate, Long>> dbOutput =
                Collections.singletonList(Collections.singletonMap(LocalDate.of(2026, 9, 2), 5L));
        when(jdbcTemplate.query(eq(TestDataQueries.GET_TEST_DATA_CONSUMPTION_ITEM),
                ArgumentMatchers.<ResultSetExtractor<List<Map<LocalDate, Long>>>>any(),
                anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> {
                    // Another request, with a range split into years, runs while this one reads the database.
                    DataUtils.getStatisticsInterval(LocalDate.of(2020, 1, 1), LocalDate.of(2024, 1, 1));
                    return dbOutput;
                });
        StatisticsRepositoryImpl repository = new StatisticsRepositoryImpl(jdbcTemplate, extractorProvider,
                mock(ProjectInformationRepository.class));
        TestDataOccupyStatistic table = new TestDataOccupyStatistic();
        table.setTableName("tdm_table");
        table.setTableTitle("Table");

        ConsumedStatistics statistics = repository.getTestDataConsumption(Collections.singletonList(table),
                UUID.randomUUID(), DATE_FROM, DATE_TO);

        assertEquals(Arrays.asList(0L, 5L, 0L), statistics.getItems().get(0).getConsumed());
        assertEquals(statistics.getDates().size(), statistics.getItems().get(0).getConsumed().size());
    }
}

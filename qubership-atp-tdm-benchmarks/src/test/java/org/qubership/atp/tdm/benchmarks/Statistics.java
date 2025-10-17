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

package org.qubership.atp.tdm.benchmarks;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.qubership.atp.tdm.benchmarks.utils.Helper;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;

import org.qubership.atp.tdm.benchmarks.facades.StatisticsFacade;

import org.qubership.atp.tdm.env.configurator.model.Project;
import org.qubership.atp.tdm.env.configurator.service.EnvironmentsService;
import org.qubership.atp.tdm.model.statistics.ConsumedStatistics;
import org.qubership.atp.tdm.model.statistics.GeneralStatisticsItem;
import org.qubership.atp.tdm.repo.CatalogRepository;
import org.qubership.atp.tdm.repo.TestDataTableRepository;
import org.qubership.atp.tdm.service.StatisticsService;
import org.qubership.atp.tdm.service.TestDataService;
import org.qubership.atp.tdm.service.impl.StatisticsServiceTest;

@Sql({"/scripts.sql"})
@SpringBootTest
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class Statistics extends StatisticsServiceTest implements AbstractJmhTest {

    private static final String TEST_DATA_TABLE_STATISTICS_AVAILABILITY = "tdm_benchmark_statistics_availability";
    private static final String TEST_DATA_TABLE_STATISTICS_CONSUMPTION = "tdm_benchmark_statistics_consumption";

    private static final UUID projectId = UUID.randomUUID();
    private static final Project project = new Project() {{
        setName("Test Data Statistics Project");
        setId(projectId);
        setEnvironments(Collections.singletonList(environment));
    }};

    private static CatalogRepository benchmarkCatalogRepository;
    private static StatisticsService benchmarkStatisticsService;
    private static TestDataService benchmarkTestDataService;
    private static EnvironmentsService benchmarkEnvironmentsService;
    private static TestDataTableRepository benchmarkTestDataTableRepository;

    @Test
    public void runBenchmarksToGetStatistics() throws Exception {
        setFields();
        Options opts = prepareOptionBuilder("jmh-statistics-report.json");
        new Runner(opts).run();
    }

    private void setFields() {
        benchmarkCatalogRepository = catalogRepository;
        benchmarkStatisticsService = statisticsService;
        benchmarkTestDataService = testDataService;
        benchmarkEnvironmentsService = environmentsService;
        benchmarkTestDataTableRepository = testDataTableRepository;
    }

    @Benchmark
    @Warmup(iterations = 2, time = 200, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = 8, time = 200, timeUnit = TimeUnit.MILLISECONDS)
    public List<GeneralStatisticsItem> getStatisticsAvailability(DataAvailability data) {
        return data.testingTarget.getTestDataAvailability(projectId, systemId);
    }

    @Benchmark
    @Warmup(iterations = 2, time = 200, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = 8, time = 200, timeUnit = TimeUnit.MILLISECONDS)
    public ConsumedStatistics getTestDataConsumption(DataConsumption data) {
        return data.testingTarget.getTestDataConsumption(projectId, systemId);
    }

    @State(Scope.Benchmark)
    public static class DataAvailability {

        private StatisticsFacade testingTarget;

        @Setup
        public void setUp() {
            when(benchmarkEnvironmentsService.getFullProject(any())).thenReturn(project);
            Helper.createTestDataTableCatalog(environmentId, projectId, systemId,
                    "STATISTICS_AVAILABILITY",
                    TEST_DATA_TABLE_STATISTICS_AVAILABILITY, benchmarkCatalogRepository);
            testingTarget = new StatisticsFacade(benchmarkTestDataService,
                    benchmarkTestDataTableRepository, benchmarkStatisticsService);
            testingTarget.createTestDataTable(TEST_DATA_TABLE_STATISTICS_AVAILABILITY);
            testingTarget.occupyTestData(TEST_DATA_TABLE_STATISTICS_AVAILABILITY);
        }
    }

    @State(Scope.Benchmark)
    public static class DataConsumption {

        private StatisticsFacade testingTarget;

        @Setup
        public void setUp() {
            when(benchmarkEnvironmentsService.getFullProject(any())).thenReturn(project);
            Helper.createTestDataTableCatalog(environmentId, projectId, systemId,
                    "STATISTICS_CONSUMPTION",
                    TEST_DATA_TABLE_STATISTICS_CONSUMPTION, benchmarkCatalogRepository);
            testingTarget = new StatisticsFacade(benchmarkTestDataService,
                    benchmarkTestDataTableRepository, benchmarkStatisticsService);
            testingTarget.createTestDataTable(TEST_DATA_TABLE_STATISTICS_CONSUMPTION);
            testingTarget.occupyTestData(TEST_DATA_TABLE_STATISTICS_CONSUMPTION);
        }
    }
}

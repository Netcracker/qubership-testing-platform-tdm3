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
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.qubership.atp.tdm.benchmarks.utils.Helper;
import org.springframework.boot.test.context.SpringBootTest;

import org.qubership.atp.tdm.benchmarks.facades.CleanupDataFacade;

import org.qubership.atp.tdm.env.configurator.model.Project;
import org.qubership.atp.tdm.env.configurator.service.EnvironmentsService;
import org.qubership.atp.tdm.model.cleanup.CleanupResults;
import org.qubership.atp.tdm.repo.CatalogRepository;
import org.qubership.atp.tdm.repo.TestDataTableRepository;
import org.qubership.atp.tdm.service.CleanupService;
import org.qubership.atp.tdm.service.TestDataService;
import org.qubership.atp.tdm.service.impl.CleanupServiceTest;

@SpringBootTest
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class CleanupData extends CleanupServiceTest implements AbstractJmhTest {

    private static final String CLEANUP_TEST_DATA = "tdm_benchmark_cleanup_test_data";

    private static final UUID projectId = UUID.randomUUID();
    private static final Project project = new Project() {{
        setName("Test Data Cleanup Project");
        setId(projectId);
        setEnvironments(Collections.singletonList(environment));
    }};

    private static TestDataService benchmarkTestDataService;
    private static CleanupService benchmarkCleanupService;
    private static EnvironmentsService benchmarkEnvironmentsService;
    private static TestDataTableRepository benchmarkTestDataTableRepository;
    private static CatalogRepository benchmarkCatalogRepository;

    @Test
    public void runBenchmarksToReleaseData() throws Exception {
        setFields();
        Options opts = prepareOptionBuilder("jmh-cleanup-data-report.json");
        new Runner(opts).run();
    }

    private void setFields() {
        benchmarkTestDataService = testDataService;
        benchmarkCleanupService = cleanupService;
        benchmarkEnvironmentsService = environmentsService;
        benchmarkCatalogRepository = catalogRepository;
        benchmarkTestDataTableRepository = testDataTableRepository;
    }

    @Benchmark
    @Warmup(iterations = 2, time = 200, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = 8, time = 200, timeUnit = TimeUnit.MILLISECONDS)
    public List<CleanupResults> runCleanup(Data data) throws Exception {
        return data.testingTarget.runCleanup(CLEANUP_TEST_DATA);
    }

    @State(Scope.Benchmark)
    public static class Data {

        private CleanupDataFacade testingTarget;

        @Setup
        public void setUp() {
            when(benchmarkEnvironmentsService.getFullProject(any())).thenReturn(project);
            Helper.createTestDataTableCatalog(environmentId, projectId, systemId,
                    "CLEANUP_TEST_DATA", CLEANUP_TEST_DATA, benchmarkCatalogRepository);
            testingTarget = new CleanupDataFacade(benchmarkTestDataService,
                    benchmarkTestDataTableRepository, benchmarkCleanupService);
            testingTarget.createTestDataTable(CLEANUP_TEST_DATA);
        }

        @TearDown
        public void tearDown() {
            benchmarkCatalogRepository.deleteByTableName(CLEANUP_TEST_DATA);
        }
    }
}

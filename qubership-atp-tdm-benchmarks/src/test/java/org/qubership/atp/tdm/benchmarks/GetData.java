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
import org.springframework.boot.test.context.SpringBootTest;

import org.qubership.atp.tdm.AbstractTestDataTest;
import org.qubership.atp.tdm.benchmarks.facades.GetDataFacade;
import org.qubership.atp.tdm.model.table.TestDataTable;
import org.qubership.atp.tdm.repo.TestDataTableRepository;
import org.qubership.atp.tdm.service.TestDataService;

@SpringBootTest
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class GetData extends AbstractTestDataTest implements AbstractJmhTest {

    private static TestDataService benchmarkTestDataService;
    private static TestDataTableRepository benchmarkTestDataTableRepository;

    @Test
    public void runBenchmarksToGetData() throws Exception {
        setFields();
        Options opts = prepareOptionBuilder("jmh-get-data-report.json");
        new Runner(opts).run();
    }

    private void setFields() {
        benchmarkTestDataService = testDataService;
        benchmarkTestDataTableRepository = testDataTableRepository;
    }

    @Benchmark
    @Warmup(iterations = 2, time = 200, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = 8, time = 200, timeUnit = TimeUnit.MILLISECONDS)
    public TestDataTable getTestDataFilterEquals(GetDataFilterEquals data) {
        return data.testingTarget.getTestDataFilterEquals();
    }

    @Benchmark
    @Warmup(iterations = 2, time = 200, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = 8, time = 200, timeUnit = TimeUnit.MILLISECONDS)
    public TestDataTable getTestDataFilterContains(GetDataFilterContains data) {
        return data.testingTarget.getTestDataFilterContains();
    }

    @Benchmark
    @Warmup(iterations = 2, time = 200, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = 8, time = 200, timeUnit = TimeUnit.MILLISECONDS)
    public TestDataTable getTestDataDateFilter(GetDataFilterDates data) {
        return data.testingTarget.getTestDataFilterDates();
    }

    @Benchmark
    @Warmup(iterations = 2, time = 200, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = 8, time = 200, timeUnit = TimeUnit.MILLISECONDS)
    public TestDataTable getTestDataPagination(GetDataPagination data) {
        return data.testingTarget.getTestDataPagination();
    }

    @State(Scope.Benchmark)
    public static class GetDataFilterEquals {

        private GetDataFacade testingTarget;

        @Setup
        public void setUp() {
            testingTarget = new GetDataFacade(benchmarkTestDataService, benchmarkTestDataTableRepository);
            testingTarget.createTestDataTable(testingTarget.getTableName("FilterEquals"));
        }
    }

    @State(Scope.Benchmark)
    public static class GetDataFilterContains {

        private GetDataFacade testingTarget;

        @Setup
        public void setUp() {
            testingTarget = new GetDataFacade(benchmarkTestDataService, benchmarkTestDataTableRepository);
            testingTarget.createTestDataTable(testingTarget.getTableName("FilterContains"));
        }
    }

    @State(Scope.Benchmark)
    public static class GetDataFilterDates {

        private GetDataFacade testingTarget;

        @Setup
        public void setUp() {
            testingTarget = new GetDataFacade(benchmarkTestDataService, benchmarkTestDataTableRepository);
            testingTarget.createTestDataTable(testingTarget.getTableName("FilterDates"));
        }
    }

    @State(Scope.Benchmark)
    public static class GetDataPagination {

        private GetDataFacade testingTarget;

        @Setup
        public void setUp() {
            testingTarget = new GetDataFacade(benchmarkTestDataService, benchmarkTestDataTableRepository);
            testingTarget.createTestDataTable(testingTarget.getTableName("Pagination"));
        }
    }
}

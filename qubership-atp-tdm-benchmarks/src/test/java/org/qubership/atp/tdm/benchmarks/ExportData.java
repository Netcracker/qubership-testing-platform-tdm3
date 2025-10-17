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

import java.io.File;
import java.io.IOException;
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
import org.qubership.atp.tdm.benchmarks.facades.GeneralFacade;
import org.springframework.boot.test.context.SpringBootTest;

import org.qubership.atp.tdm.AbstractTestDataTest;
import org.qubership.atp.tdm.repo.TestDataTableRepository;
import org.qubership.atp.tdm.service.TestDataService;

@SpringBootTest
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class ExportData extends AbstractTestDataTest implements AbstractJmhTest {

    private static final String TEST_DATA_TABLE_EXPORT_AS_EXCEL = "tdm_benchmark_test_data_export_excel";
    private static final String TEST_DATA_TABLE_EXPORT_AS_CSV = "tdm_benchmark_test_data_export_csv";

    private static TestDataService benchmarkTestDataService;
    private static TestDataTableRepository benchmarkTestDataTableRepository;

    @Test
    public void runBenchmarksToExportData() throws Exception {
        setFields();
        Options opts = prepareOptionBuilder("jmh-export-data-report.json");
        new Runner(opts).run();
    }

    private void setFields() {
        benchmarkTestDataService = testDataService;
        benchmarkTestDataTableRepository = testDataTableRepository;
    }

    @Benchmark
    @Warmup(iterations = 2, time = 200, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = 8, time = 200, timeUnit = TimeUnit.MILLISECONDS)
    public File getTestDataTableAsExcelFile(GetDataAsExcel data) throws IOException {
        return data.testingTarget.getTestDataTableAsExcelFile(TEST_DATA_TABLE_EXPORT_AS_EXCEL);
    }

    @Benchmark
    @Warmup(iterations = 2, time = 200, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = 8, time = 200, timeUnit = TimeUnit.MILLISECONDS)
    public File getTestDataTableAsCsvFile(GetDataAsCsv data) throws IOException {
        return data.testingTarget.getTestDataTableAsCsvFile(TEST_DATA_TABLE_EXPORT_AS_CSV);
    }

    @State(Scope.Benchmark)
    public static class GetDataAsExcel {

        private GeneralFacade testingTarget;

        @Setup
        public void setUp() {
            testingTarget = new GeneralFacade(benchmarkTestDataService, benchmarkTestDataTableRepository);
            testingTarget.createTestDataTable(TEST_DATA_TABLE_EXPORT_AS_EXCEL);
        }
    }

    @State(Scope.Benchmark)
    public static class GetDataAsCsv {

        private GeneralFacade testingTarget;

        @Setup
        public void setUp() {
            testingTarget = new GeneralFacade(benchmarkTestDataService, benchmarkTestDataTableRepository);
            testingTarget.createTestDataTable(TEST_DATA_TABLE_EXPORT_AS_CSV);
        }
    }
}

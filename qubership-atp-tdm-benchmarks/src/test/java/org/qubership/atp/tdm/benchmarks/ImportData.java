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

import java.io.IOException;
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
import org.qubership.atp.tdm.benchmarks.facades.GeneralFacade;
import org.qubership.atp.tdm.benchmarks.utils.Helper;
import org.springframework.boot.test.context.SpringBootTest;

import org.qubership.atp.tdm.AbstractTestDataTest;
import org.qubership.atp.tdm.env.configurator.model.Project;
import org.qubership.atp.tdm.env.configurator.service.EnvironmentsService;
import org.qubership.atp.tdm.model.ImportTestDataStatistic;
import org.qubership.atp.tdm.model.table.TestDataTable;
import org.qubership.atp.tdm.repo.CatalogRepository;
import org.qubership.atp.tdm.repo.TestDataTableRepository;
import org.qubership.atp.tdm.service.TestDataService;

@SpringBootTest
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class ImportData extends AbstractTestDataTest implements AbstractJmhTest {

    private static final String TDM_BENCHMARK_SQL_IMPORT_SOURCE_TABLE = "tdm_benchmark_sql_import_source_table";
    private static final String BENCHMARKS_IMPORT_BIG_FILE_9MB = "Benchmarks_importBigFile_9MB.xlsx";
    private static final String BENCHMARKS_IMPORT_BIG_FILE_18MB = "Benchmarks_importBigFile_18MB.xlsx";

    private static final UUID projectId = UUID.randomUUID();
    private static final Project project = new Project() {{
        setName("Test Data Import Project");
        setId(projectId);
        setEnvironments(Collections.singletonList(environment));
    }};

    private static TestDataService benchmarkTestDataService;
    private static EnvironmentsService benchmarkEnvironmentsService;
    private static CatalogRepository benchmarkCatalogRepository;
    private static TestDataTableRepository benchmarkTestDataTableRepository;

    @Test
    public void runBenchmarksToImportData() throws Exception {
        setFields();
        Options opts = prepareOptionBuilder("jmh-import-data-report.json");
        new Runner(opts).run();
    }

    private void setFields() {
        benchmarkTestDataService = testDataService;
        benchmarkEnvironmentsService = environmentsService;
        benchmarkCatalogRepository = catalogRepository;
        benchmarkTestDataTableRepository = testDataTableRepository;
    }

    @Benchmark
    @Warmup(iterations = 2, time = 200, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = 4, time = 200, timeUnit = TimeUnit.MILLISECONDS)
    public TestDataTable importExcelTestDataBigFile9Mb(ImportExcelData data) throws IOException {
        return data.testingTarget.importExcelTestData(BENCHMARKS_IMPORT_BIG_FILE_9MB);
    }

    @Benchmark
    @Warmup(iterations = 2, time = 200, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = 4, time = 200, timeUnit = TimeUnit.MILLISECONDS)
    public TestDataTable importExcelTestDataBigFile18Mb(ImportExcelData data) throws IOException {
        return data.testingTarget.importExcelTestData(BENCHMARKS_IMPORT_BIG_FILE_18MB);
    }

    @Benchmark
    @Warmup(iterations = 2, time = 200, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = 4, time = 200, timeUnit = TimeUnit.MILLISECONDS)
    public List<ImportTestDataStatistic> importExcelTestDataBigFile9MbViaService(ImportExcelData data)
            throws IOException {
        return data.testingTarget.importExcelTestDataViaService(BENCHMARKS_IMPORT_BIG_FILE_9MB,
                "BENCHMARKS_IMPORT_BIG_FILE_9MB_VIA_SERVICE");
    }

    @Benchmark
    @Warmup(iterations = 2, time = 200, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = 4, time = 200, timeUnit = TimeUnit.MILLISECONDS)
    public List<ImportTestDataStatistic> importExcelTestDataBigFile18MbViaService(ImportExcelData data)
            throws IOException {
        return data.testingTarget.importExcelTestDataViaService(BENCHMARKS_IMPORT_BIG_FILE_18MB,
                "BENCHMARKS_IMPORT_BIG_FILE_18MB_VIA_SERVICE");
    }

    @Benchmark
    @Warmup(iterations = 2, time = 200, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = 4, time = 200, timeUnit = TimeUnit.MILLISECONDS)
    public List<ImportTestDataStatistic> importSqlTestDataViaService(ImportSqlData data) {
        return data.testingTarget.importSqlTestData(environmentId, "TDM_BENCHMARK_SQL_IMPORT_SOURCE_TABLE",
                TDM_BENCHMARK_SQL_IMPORT_SOURCE_TABLE);
    }

    @State(Scope.Benchmark)
    public static class ImportExcelData {

        private GeneralFacade testingTarget;

        @Setup
        public void setUp() {
            testingTarget = new GeneralFacade(benchmarkTestDataService, benchmarkTestDataTableRepository);
        }
    }

    @State(Scope.Benchmark)
    public static class ImportSqlData {

        private GeneralFacade testingTarget;

        @Setup
        public void setUp() {
            when(benchmarkEnvironmentsService.getFullProject(any())).thenReturn(project);
            Helper.createTestDataTableCatalog(environmentId, projectId, systemId,
                    "TDM_BENCHMARK_SQL_IMPORT_SOURCE_TABLE",
                    TDM_BENCHMARK_SQL_IMPORT_SOURCE_TABLE, benchmarkCatalogRepository);
            testingTarget = new GeneralFacade(benchmarkTestDataService,
                    benchmarkTestDataTableRepository);
            testingTarget.createTestDataTable(TDM_BENCHMARK_SQL_IMPORT_SOURCE_TABLE);
        }
    }
}

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

import org.qubership.atp.tdm.benchmarks.facades.AtpActionsFacade;

import org.qubership.atp.tdm.env.configurator.model.LazyEnvironment;
import org.qubership.atp.tdm.env.configurator.model.LazyProject;
import org.qubership.atp.tdm.env.configurator.service.EnvironmentsService;
import org.qubership.atp.tdm.model.rest.ApiDataFilter;
import org.qubership.atp.tdm.model.rest.ResponseMessage;
import org.qubership.atp.tdm.model.rest.requests.AddInfoToRowRequest;
import org.qubership.atp.tdm.model.rest.requests.GetRowRequest;
import org.qubership.atp.tdm.model.rest.requests.OccupyRowRequest;
import org.qubership.atp.tdm.model.rest.requests.UpdateRowRequest;
import org.qubership.atp.tdm.repo.CatalogRepository;
import org.qubership.atp.tdm.repo.TestDataTableRepository;
import org.qubership.atp.tdm.service.AtpActionService;
import org.qubership.atp.tdm.service.TestDataService;
import org.qubership.atp.tdm.service.impl.AtpActionServiceTest;

@SpringBootTest
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class AtpActions extends AtpActionServiceTest implements AbstractJmhTest {

    private static final String ATP_ACTIONS_OCCUPY = "tdm_benchmark_atp_actions_occupy";
    private static final String ATP_ACTIONS_UPDATE = "tdm_benchmark_atp_actions_update";
    private static final String ATP_ACTIONS_GET = "tdm_benchmark_atp_actions_get";
    private static final String ATP_ACTIONS_ADD_INFO = "tdm_benchmark_atp_actions_add_info";

    private static final UUID projectId = UUID.randomUUID();

    private static final LazyEnvironment lazyEnvironment = new LazyEnvironment() {{
        setName("Lazy Environment");
        setId(projectId);
    }};

    private static final LazyProject lazyProject = new LazyProject() {{
        setName("Lazy Project");
        setId(projectId);
    }};

    private static TestDataService benchmarkTestDataService;
    private static AtpActionService benchmarkAtpActionService;
    private static EnvironmentsService benchmarkEnvironmentsService;
    private static TestDataTableRepository benchmarkTestDataTableRepository;
    private static CatalogRepository benchmarkCatalogRepository;

    @Test
    public void runBenchmarksToReleaseData() throws Exception {
        setFields();
        Options opts = prepareOptionBuilder("jmh-atp-actions-report.json");
        new Runner(opts).run();
    }

    private void setFields() {
        benchmarkTestDataService = testDataService;
        benchmarkAtpActionService = atpActionService;
        benchmarkEnvironmentsService = environmentsService;
        benchmarkCatalogRepository = catalogRepository;
        benchmarkTestDataTableRepository = testDataTableRepository;
    }

    @Benchmark
    @Warmup(iterations = 2, time = 200, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = 8, time = 200, timeUnit = TimeUnit.MILLISECONDS)
    public ResponseMessage insertTestData(InsertData data) {
        List<Map<String, Object>> records = buildTestDataTable().getData();
        return data.testingTarget.insertTestData(lazyProject.getName(), lazyEnvironment.getName(),
                system.getName(), "ATP_ACTIONS_INSERT", records);
    }

    @Benchmark
    @Warmup(iterations = 2, time = 200, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = 8, time = 200, timeUnit = TimeUnit.MILLISECONDS)
    public List<ResponseMessage> occupyTestData(OccupyData data) {
        OccupyRowRequest occupyRowRequest = buildOccupyRowRequest("Assignment",
                "SIM", "Equals", GeneralFacade.TEST_DATA_SEARCH_VALUE);
        return data.testingTarget.occupyTestData(lazyProject.getName(), lazyEnvironment.getName(),
                system.getName(), "ATP_ACTIONS_OCCUPY", Collections.singletonList(occupyRowRequest));
    }

    @Benchmark
    @Warmup(iterations = 2, time = 200, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = 8, time = 200, timeUnit = TimeUnit.MILLISECONDS)
    public List<ResponseMessage> updateTestData(UpdateData data) {
        UpdateRowRequest updateRowRequest = new UpdateRowRequest();
        List<ApiDataFilter> filters = Collections.singletonList(new ApiDataFilter("SIM",
                "Equals", GeneralFacade.TEST_DATA_SEARCH_VALUE, false));
        updateRowRequest.setFilters(filters);
        Map<String, String> recordWithDataForUpdate = new HashMap<>();
        recordWithDataForUpdate.put("Environment", "ZLAB001");
        updateRowRequest.setRecordWithDataForUpdate(recordWithDataForUpdate);
        return data.testingTarget.updateTestData(lazyProject.getName(), lazyEnvironment.getName(),
                system.getName(), "ATP_ACTIONS_UPDATE", Collections.singletonList(updateRowRequest));
    }

    @Benchmark
    @Warmup(iterations = 2, time = 200, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = 8, time = 200, timeUnit = TimeUnit.MILLISECONDS)
    public List<ResponseMessage> getTestData(GetData data) {
        GetRowRequest getRowRequest = buildGetRowRequest("Assignment",
                "SIM", "Equals", GeneralFacade.TEST_DATA_SEARCH_VALUE);
        return data.testingTarget.getTestData(lazyProject.getName(), lazyEnvironment.getName(),
                system.getName(), "ATP_ACTIONS_GET", Collections.singletonList(getRowRequest));
    }

    @Benchmark
    @Warmup(iterations = 2, time = 200, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = 8, time = 200, timeUnit = TimeUnit.MILLISECONDS)
    public List<ResponseMessage> addInfoToRow(AddInfoData data) {
        AddInfoToRowRequest addInfoToRowRequest = new AddInfoToRowRequest();
        List<ApiDataFilter> filters = Collections.singletonList(new ApiDataFilter("SIM",
                "Equals", GeneralFacade.TEST_DATA_SEARCH_VALUE, false));
        addInfoToRowRequest.setFilters(filters);
        Map<String, String> recordWithDataForUpdate = new HashMap<>();
        recordWithDataForUpdate.put("Environment", "ZLAB001");
        addInfoToRowRequest.setRecordWithDataForUpdate(recordWithDataForUpdate);
        return data.testingTarget.addInfoToRow(lazyProject.getName(), lazyEnvironment.getName(),
                system.getName(), "ATP_ACTIONS_ADD_INFO", Collections.singletonList(addInfoToRowRequest));
    }

    @State(Scope.Benchmark)
    public static class InsertData {

        private AtpActionsFacade testingTarget;

        @Setup
        public void setUp() {
            when(benchmarkEnvironmentsService.getLazyProjectByName(any())).thenReturn(lazyProject);
            when(benchmarkEnvironmentsService.getLazyEnvironmentByName(any(), any())).thenReturn(lazyEnvironment);
            testingTarget = new AtpActionsFacade(benchmarkTestDataService,
                    benchmarkTestDataTableRepository, benchmarkAtpActionService);
        }
    }

    @State(Scope.Benchmark)
    public static class OccupyData {

        private AtpActionsFacade testingTarget;

        @Setup
        public void setUp() {
            when(benchmarkEnvironmentsService.getLazyProjectByName(any())).thenReturn(lazyProject);
            when(benchmarkEnvironmentsService.getLazyEnvironmentByName(any(), any())).thenReturn(lazyEnvironment);
            Helper.createTestDataTableCatalog(environmentId, projectId, systemId,
                    "ATP_ACTIONS_OCCUPY", ATP_ACTIONS_OCCUPY, benchmarkCatalogRepository);
            testingTarget = new AtpActionsFacade(benchmarkTestDataService,
                    benchmarkTestDataTableRepository, benchmarkAtpActionService);
            testingTarget.createTestDataTable(ATP_ACTIONS_OCCUPY);
        }
    }

    @State(Scope.Benchmark)
    public static class UpdateData {

        private AtpActionsFacade testingTarget;

        @Setup
        public void setUp() {
            when(benchmarkEnvironmentsService.getLazyProjectByName(any())).thenReturn(lazyProject);
            when(benchmarkEnvironmentsService.getLazyEnvironmentByName(any(), any())).thenReturn(lazyEnvironment);
            Helper.createTestDataTableCatalog(environmentId, projectId, systemId,
                    "ATP_ACTIONS_UPDATE", ATP_ACTIONS_UPDATE, benchmarkCatalogRepository);
            testingTarget = new AtpActionsFacade(benchmarkTestDataService,
                    benchmarkTestDataTableRepository, benchmarkAtpActionService);
            testingTarget.createTestDataTable(ATP_ACTIONS_UPDATE);
        }
    }

    @State(Scope.Benchmark)
    public static class GetData {

        private AtpActionsFacade testingTarget;

        @Setup
        public void setUp() {
            when(benchmarkEnvironmentsService.getLazyProjectByName(any())).thenReturn(lazyProject);
            when(benchmarkEnvironmentsService.getLazyEnvironmentByName(any(), any())).thenReturn(lazyEnvironment);
            Helper.createTestDataTableCatalog(environmentId, projectId, systemId,
                    "ATP_ACTIONS_GET", ATP_ACTIONS_GET, benchmarkCatalogRepository);
            testingTarget = new AtpActionsFacade(benchmarkTestDataService,
                    benchmarkTestDataTableRepository, benchmarkAtpActionService);
            testingTarget.createTestDataTable(ATP_ACTIONS_GET);
        }
    }

    @State(Scope.Benchmark)
    public static class AddInfoData {

        private AtpActionsFacade testingTarget;

        @Setup
        public void setUp() {
            when(benchmarkEnvironmentsService.getLazyProjectByName(any())).thenReturn(lazyProject);
            when(benchmarkEnvironmentsService.getLazyEnvironmentByName(any(), any())).thenReturn(lazyEnvironment);
            Helper.createTestDataTableCatalog(environmentId, projectId, systemId,
                    "ATP_ACTIONS_ADD_INFO", ATP_ACTIONS_ADD_INFO, benchmarkCatalogRepository);
            testingTarget = new AtpActionsFacade(benchmarkTestDataService,
                    benchmarkTestDataTableRepository, benchmarkAtpActionService);
            testingTarget.createTestDataTable(ATP_ACTIONS_ADD_INFO);
        }
    }
}

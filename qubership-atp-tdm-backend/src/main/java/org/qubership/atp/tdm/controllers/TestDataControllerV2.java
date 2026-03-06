/*
 * # Copyright 2024-2025 NetCracker Technology Corporation
 * #
 * # Licensed under the Apache License, Version 2.0 (the "License");
 * # you may not use this file except in compliance with the License.
 * # You may obtain a copy of the License at
 * #
 * #      http://www.apache.org/licenses/LICENSE-2.0
 * #
 * # Unless required by applicable law or agreed to in writing, software
 * # distributed under the License is distributed on an "AS IS" BASIS,
 * # WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * # See the License for the specific language governing permissions and
 * # limitations under the License.
 */

package org.qubership.atp.tdm.controllers;

import java.util.List;

import javax.annotation.Nonnull;

import org.qubership.atp.integration.configuration.configuration.AuditAction;
import org.qubership.atp.tdm.model.ImportSqlTestDataRequest;
import org.qubership.atp.tdm.model.ImportTestDataStatistic;
import org.qubership.atp.tdm.service.TestDataService;
import org.qubership.atp.tdm.service.impl.MetricService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

/**
 * Test Data Controller V2 - Enhanced endpoints with request body support.
 * Provides improved API design with cleaner parameter handling and better compatibility.
 */
@Slf4j
@RequestMapping("/api/tdm/v2")
@RestController
@Tag(name = "test-data-controller-v2", description = "Test Data Controller V2")
public class TestDataControllerV2 {

    private final TestDataService testDataService;
    private final MetricService metricService;

    @Autowired
    public TestDataControllerV2(@Nonnull TestDataService testDataService, @Nonnull MetricService metricService) {
        this.testDataService = testDataService;
        this.metricService = metricService;
    }

    /**
     * Import Sql TestData v2 - uses request body instead of request parameters.
     * This endpoint provides better compatibility and cleaner parameter handling.
     */
    @Operation(description = "Import sql to TDM v2 - with request body.")
    @PreAuthorize("@entityAccess.checkAccess("
            + "T(org.qubership.atp.tdm.utils.UsersManagementEntities).TEST_DATA.getName(),"
            + "#request.projectId, 'CREATE')")
    @AuditAction(auditAction = "Import sql to TDM v2. ProjectId {{#request.projectId}}, "
            + "TableTitle {{#request.tableTitle}}")
    @PostMapping(value = "/import/sql")
    public List<ImportTestDataStatistic> importSqlTestData(@RequestBody ImportSqlTestDataRequest request) {
        metricService.incrementInsertAction(request.getProjectId());
        return testDataService.importSqlTestData(request.getProjectId(), request.getEnvironmentsIds(),
                request.getSystemName(), request.getTableTitle(), request.getQuery(), request.getQueryTimeout());
    }
}
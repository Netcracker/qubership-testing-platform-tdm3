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

package org.qubership.atp.tdm.controllers;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.qubership.atp.integration.configuration.configuration.AuditAction;
import org.qubership.atp.integration.configuration.mdc.MdcUtils;
import org.qubership.atp.tdm.mdc.MdcField;
import org.qubership.atp.tdm.model.ChangeTitleRequest;
import org.qubership.atp.tdm.model.ColumnValues;
import org.qubership.atp.tdm.model.EnvsList;
import org.qubership.atp.tdm.model.ImportTestDataStatistic;
import org.qubership.atp.tdm.model.TestDataRequest;
import org.qubership.atp.tdm.model.TestDataTableCatalog;
import org.qubership.atp.tdm.model.TestDataTableUpdateByQuery;
import org.qubership.atp.tdm.model.ei.TdmDataToExport;
import org.qubership.atp.tdm.model.rest.ResponseMessage;
import org.qubership.atp.tdm.model.rest.ResponseType;
import org.qubership.atp.tdm.model.table.TableColumnValues;
import org.qubership.atp.tdm.model.table.TestDataFlagsTable;
import org.qubership.atp.tdm.model.table.TestDataTable;
import org.qubership.atp.tdm.service.TestDataService;
import org.qubership.atp.tdm.service.impl.MetricService;
import org.qubership.atp.tdm.utils.HttpUtils;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.google.gson.Gson;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequestMapping("/api/tdm")
@RestController()
public class TestDataController /* implements TestDataControllerApi */ {

    private final TestDataService testDataService;
    private final MetricService metricService;


    @Autowired
    public TestDataController(@Nonnull TestDataService testDataService, @Nonnull MetricService metricService) {
        this.testDataService = testDataService;
        this.metricService = metricService;
    }

    @Operation(description = "Get test data tables catalog.")
    @AuditAction(auditAction = "Get test data tables catalog. ProjectId {{#projectId}}")
    @GetMapping(value = "/tables/catalog")
    public List<TestDataTableCatalog> getTestDataTablesCatalog(@RequestParam UUID projectId,
                                                               @RequestParam(required = false) UUID systemId) {
        log.info("Fetching table {}", projectId.toString());
        return testDataService.getTestDataTablesCatalog(projectId, systemId);
    }

    @Operation(description = "Get tables under project.")
    @PreAuthorize("@entityAccess.checkAccess("
            + "T(org.qubership.atp.tdm.utils.UsersManagementEntities).TEST_DATA.getName(),"
            + "#projectId, 'READ')")
    @AuditAction(auditAction = "Get tables under projectId {{#projectId}}")
    @GetMapping(value = "/tables/list")
    public TdmDataToExport getTestDataTablesList(@RequestParam UUID projectId) {
        return testDataService.tablesToExport(projectId);
    }

    @Operation(description = "Get tables id and name under project and environment.")
    @PreAuthorize("@entityAccess.checkAccess("
            + "T(org.qubership.atp.tdm.utils.UsersManagementEntities).TEST_DATA.getName(),"
            + "#projectId, 'READ')")
    @AuditAction(auditAction = "Get tables id and name under project {{#projectId}} and environment {{#envId}}")
    @GetMapping(value = "/environment/tables/list")
    public Map<String, String> getTestDataTablesListByEnvironment(@RequestParam UUID projectId,
                                                                  @RequestParam UUID envId) {
        MdcUtils.put(MdcField.ENVIRONMENT_ID.toString(), envId);
        return testDataService.tablesToExportByEnvironment(projectId, envId);
    }

    /**
     * Import Excel TestData.
     */
    @Operation(description = "Import excel to TDM.")
    @PreAuthorize("@entityAccess.checkAccess("
            + "T(org.qubership.atp.tdm.utils.UsersManagementEntities).TEST_DATA.getName(),"
            + "#projectId, 'CREATE')")
    @AuditAction(auditAction = "Import excel to TDM. ProjectId {{#projectId}}, TableTitle {{#tableTitle}}")
    @PostMapping(value = "/import/excel")
    public List<ImportTestDataStatistic> importExcelTestData(@RequestParam UUID projectId,
                                                             @RequestParam(required = false) UUID environmentId,
                                                             @RequestParam(required = false) UUID systemId,
                                                             @RequestParam String tableTitle,
                                                             @RequestParam Boolean runSqlScript,
                                                             @RequestParam MultipartFile file) {
        metricService.incrementInsertAction(projectId);
        return testDataService.importExcelTestData(projectId, environmentId, systemId, tableTitle, runSqlScript, file);
    }

    /**
     * Import Sql TestData.
     */
    @Operation(description = "Import sql to TDM.")
    @PreAuthorize("@entityAccess.checkAccess("
            + "T(org.qubership.atp.tdm.utils.UsersManagementEntities).TEST_DATA.getName(),"
            + "#projectId, 'CREATE')")
    @AuditAction(auditAction = "Import sql to TDM. ProjectId {{#projectId}}, TableTitle {{#tableTitle}}")
    @PostMapping(value = "/import/sql")
    public List<ImportTestDataStatistic> importSqlTestData(@RequestParam UUID projectId,
                                                            @RequestParam List<UUID> environmentsIds,
                                                            @RequestParam String systemName,
                                                            @RequestParam String tableTitle,
                                                            @RequestParam String query,
                                                            @RequestParam Integer queryTimeout) {
        metricService.incrementInsertAction(projectId);
        return testDataService.importSqlTestData(projectId, environmentsIds, systemName,
                tableTitle, query, queryTimeout);
    }

    /**
     * Returns test data table.
     *
     * @param testDataRequest - test data request.
     * @return TestDataTable
     */
    @Operation(description = "Get test data table.")
    @PreAuthorize("@entityAccess.checkAccess("
            + "T(org.qubership.atp.tdm.utils.UsersManagementEntities).TEST_DATA.getName(),"
            + "@catalogRepository.findByTableName(#testDataRequest.tableName).getProjectId(), 'READ')")
    @AuditAction(auditAction = "Get test data table {{#testDataRequest.tableName}}")
    @PostMapping(value = "/table")
    public TestDataTable getTestData(@RequestBody TestDataRequest testDataRequest) {
        metricService.incrementGetAction(MDC.get(MdcField.PROJECT_ID.toString()));
        return testDataService.getTestData(testDataRequest.getTableName(), testDataRequest.getOffset(),
                testDataRequest.getLimit(), testDataRequest.getFilters(), testDataRequest.getDataTableOrder(),
                testDataRequest.isOccupied());
    }

    @Operation(description = "Occupy test data.")
    @PreAuthorize("@entityAccess.checkAccess("
            + "T(org.qubership.atp.tdm.utils.UsersManagementEntities).TEST_DATA.getName(),"
            + "@catalogRepository.findByTableName(#tableName).getProjectId(), 'UPDATE')")
    @AuditAction(auditAction = "Occupy test data. Table Name {{#tableName}}")
    @PutMapping(value = "/occupy")
    public void occupyTestData(@RequestParam String tableName, @RequestParam String occupiedBy,
                               @RequestBody List<UUID> rows) {
        metricService.incrementOccupyAction(MDC.get(MdcField.PROJECT_ID.toString()));
        testDataService.occupyTestData(tableName, occupiedBy, rows);
    }

    @Operation(description = "Release test data.")
    @PreAuthorize("@entityAccess.checkAccess("
            + "T(org.qubership.atp.tdm.utils.UsersManagementEntities).TEST_DATA.getName(),"
            + "@catalogRepository.findByTableName(#tableName).getProjectId(), 'UPDATE')")
    @AuditAction(auditAction = "Release test data. Table Name {{#tableName}}")
    @PutMapping(value = "/release")
    public void releaseTestData(@RequestParam String tableName, @RequestBody List<UUID> rows) {
        metricService.incrementReleaseAction(MDC.get(MdcField.PROJECT_ID.toString()));
        testDataService.releaseTestData(tableName, rows);
    }

    @Operation(description = "Delete selected rows from table.")
    @PreAuthorize("@entityAccess.checkAccess("
            + "T(org.qubership.atp.tdm.utils.UsersManagementEntities).TEST_DATA.getName(),"
            + "@catalogRepository.findByTableName(#tableName).getProjectId(), 'UPDATE')")
    @AuditAction(auditAction = "Delete selected rows from table {{#tableName}}")
    @PutMapping(value = "/delete/rows")
    public void deleteTestDataTableRows(@RequestParam String tableName, @RequestBody List<UUID> rows) {
        metricService.incrementDeleteAction(MDC.get(MdcField.PROJECT_ID.toString()));
        testDataService.deleteTestDataTableRows(tableName, rows);
    }

    @Operation(description = "Drop selected table.")
    @PreAuthorize("@entityAccess.checkAccess("
            + "T(org.qubership.atp.tdm.utils.UsersManagementEntities).TEST_DATA.getName(),"
            + "@catalogRepository.findByTableName(#tableName).getProjectId(), 'DELETE')")
    @AuditAction(auditAction = "Drop selected table {{#tableName}}")
    @DeleteMapping(value = "/table")
    public void deleteTestData(@RequestParam String tableName) {
        metricService.incrementDeleteAction(MDC.get(MdcField.PROJECT_ID.toString()));
        testDataService.deleteTestData(tableName);
    }

    /**
     * Remove all records from table .
     *
     * @param tableName table name
     * @param projectId project id
     * @param systemId  system id
     * @return table which has been truncated.
     */
    @Operation(description = "Truncate data in table.")
    @PreAuthorize("@entityAccess.checkAccess("
            + "T(org.qubership.atp.tdm.utils.UsersManagementEntities).TEST_DATA.getName(),"
            + "#projectId, 'DELETE')")
    @AuditAction(auditAction = "Truncate data in table {{#tableName}} in projectId {{#projectId}}")
    @DeleteMapping("/truncate/table")
    public ResponseMessage truncateDataInTable(@RequestParam String tableName,
                                               @RequestParam UUID projectId,
                                               @RequestParam(required = false) UUID systemId) {
        metricService.incrementDeleteAction(projectId.toString());
        testDataService.truncateDataInTable(tableName, projectId, systemId);
        return new ResponseMessage(ResponseType.SUCCESS, String.format("Data has been cleaned in table with tableName"
                + " = \"%s\".", tableName));
    }

    /**
     * Get TestDataTable As Excel File.
     */
    @Operation(description = "Download table as excel file.")
    @PreAuthorize("@entityAccess.checkAccess("
            + "T(org.qubership.atp.tdm.utils.UsersManagementEntities).TEST_DATA.getName(),"
            + "@catalogRepository.findByTableName(#tableName).getProjectId(), 'READ')")
    @AuditAction(auditAction = "Download table {{#tableName}} as excel file.")
    @GetMapping(path = "/download/excel")
    public ResponseEntity<InputStreamResource> getTestDataTableAsExcelFile(@RequestParam String tableName)
            throws IOException {
        metricService.incrementGetAction(MDC.get(MdcField.PROJECT_ID.toString()));
        File testDataTableAsExcelFile = testDataService.getTestDataTableAsExcelFile(tableName);
        return HttpUtils.buildFileResponseEntity(testDataTableAsExcelFile,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    }

    /**
     * Get TestDataTable As Csv File.
     */
    @Operation(description = "Download table as csv file.")
    @PreAuthorize("@entityAccess.checkAccess("
            + "T(org.qubership.atp.tdm.utils.UsersManagementEntities).TEST_DATA.getName(),"
            + "@catalogRepository.findByTableName(#tableName).getProjectId(), 'READ')")
    @AuditAction(auditAction = "Download table {{#tableName}} as csv file.")
    @GetMapping(path = "/download/csv")
    public ResponseEntity<InputStreamResource> getTestDataTableAsCsvFile(@RequestParam String tableName)
            throws IOException {
        metricService.incrementGetAction(MDC.get(MdcField.PROJECT_ID.toString()));
        File testDataTableAsCsvFile = testDataService.getTestDataTableAsCsvFile(tableName);
        return HttpUtils.buildFileResponseEntity(testDataTableAsCsvFile, "text/csv");
    }

    /**
     * Method fixes issue with occupation functional (ATPII-10699).
     * For all tables add new column "OCCUPIED_BY"
     */
    @Operation(description = "Old update.")
    @AuditAction(auditAction = "Old update.")
    @GetMapping(path = "/fix/occupied/by/column")
    public void alterOccupiedByColumn() {
        testDataService.alterOccupiedByColumn();
    }

    /**
     * Getting link preview.
     */
    @Operation(description = "Get preview for linker.")
    @PreAuthorize("@entityAccess.checkAccess("
            + "T(org.qubership.atp.tdm.utils.UsersManagementEntities).TEST_DATA.getName(),"
            + "#projectId, 'CREATE')")
    @AuditAction(auditAction = "Get preview for linker. ProjectId {{#projectId}}, TableName {{#tableName}}")
    @PostMapping(path = "/link/preview")
    public ResponseEntity<String> getPreviewLink(@RequestParam UUID projectId,
                                                 @RequestParam(required = false) UUID systemId,
                                                 @RequestParam String columnName,
                                                 @RequestParam(required = false) String tableName,
                                                 @RequestParam boolean pickUpFullLinkFromTableCell,
                                                 @RequestBody(required = false) String endpoint) {
        String link = testDataService.getPreviewLink(projectId, systemId, endpoint,
                columnName, tableName, pickUpFullLinkFromTableCell);
        return ResponseEntity.ok(new Gson().toJson(link));
    }

    @Operation(description = "Setup links.")
    @PreAuthorize("@entityAccess.checkAccess("
            + "T(org.qubership.atp.tdm.utils.UsersManagementEntities).TEST_DATA.getName(),"
            + "#projectId, 'CREATE')")
    @AuditAction(auditAction = "Setup links. ProjectId {{#projectId}}, TableName {{#tableName}}")
    @PostMapping(path = "/link/setup")
    public void setupColumnLinks(@RequestParam Boolean isAll,
                                 @RequestParam UUID projectId,
                                 @RequestParam(required = false) UUID systemId,
                                 @RequestParam(required = false) String tableName,
                                 @RequestParam String columnName,
                                 @RequestParam Boolean validateUnoccupiedResources,
                                 @RequestParam boolean pickUpFullLinkFromTableCell,
                                 @RequestBody(required = false) String endpoint) {
        testDataService.setupColumnLinks(isAll, projectId, systemId, tableName, columnName, endpoint,
                validateUnoccupiedResources, pickUpFullLinkFromTableCell);
    }

    @Operation(description = "Check flag \'is unoccupied validation\'.")
    @PreAuthorize("@entityAccess.checkAccess("
            + "T(org.qubership.atp.tdm.utils.UsersManagementEntities).TEST_DATA.getName(),"
            + "@catalogRepository.findByTableName(#tableName).getProjectId(), 'READ')")
    @AuditAction(auditAction = "Check flag: is unoccupied validation for table {{#tableName}}.")
    @GetMapping(value = "/validation/unoccupied")
    public TestDataFlagsTable isUnoccupiedValidation(@RequestParam("tableName") String tableName) {
        return testDataService.getUnoccupiedValidationFlagStatus(tableName);
    }

    @Operation(description = "Returns a list of environment IDs that have tables with the requested "
            + "table title for the specified project.")
    @PreAuthorize("@entityAccess.checkAccess("
            + "T(org.qubership.atp.tdm.utils.UsersManagementEntities).TEST_DATA.getName(),"
            + "#projectId, 'READ')")
    @AuditAction(auditAction = "Get Table Environments by projectId {{#projectId}} and tableTitle {{#tableTitle}}")
    @GetMapping(path = "/table/environments")
    public EnvsList getTableEnvironments(@RequestParam("projectId") UUID projectId,
                                         @RequestParam("tableTitle") String tableTitle) {
        return testDataService.getTableEnvironments(projectId, tableTitle);
    }

    /**
     * Replaces macroses (related to internal TDM table) with real values.
     *
     * @param tableName - table name
     * @param query     - source query
     */
    @Operation(description = "Evaluate query.")
    @PreAuthorize("@entityAccess.checkAccess("
            + "T(org.qubership.atp.tdm.utils.UsersManagementEntities).TEST_DATA.getName(),"
            + "@catalogRepository.findByTableName(#tableName).getProjectId(), 'UPDATE')")
    @AuditAction(auditAction = "Evaluate Query for table {{#tableName}}")
    @PutMapping(value = "/evaluate/query")
    public Map<String, String> evaluateQuery(@RequestParam("tableName") String tableName,
                                             @RequestBody String query) {
        metricService.incrementUpdateAction(MDC.get(MdcField.PROJECT_ID.toString()));
        Map<String, String> result = new HashMap<>();
        result.put("query", testDataService.evaluateQuery(tableName, query));
        return result;
    }

    @Operation(description = "Get column distinct values.")
    @PreAuthorize("@entityAccess.checkAccess("
            + "T(org.qubership.atp.tdm.utils.UsersManagementEntities).TEST_DATA.getName(),"
            + "@catalogRepository.findByTableName(#tableName).getProjectId(), 'READ')")
    @AuditAction(auditAction = "Get column distinct values. TableName {{#tableName}}")
    @GetMapping(path = "/table/column/distinct/values")
    public ColumnValues getColumnDistinctValues(@RequestParam("tableName") String tableName,
                                                @RequestParam("columnName") String columnName,
                                                @RequestParam("occupied") Boolean occupied) {
        return testDataService.getColumnDistinctValues(tableName, columnName, occupied);
    }

    @Operation(description = "Get row value.")
    @PreAuthorize("@entityAccess.checkAccess("
            + "T(org.qubership.atp.tdm.utils.UsersManagementEntities).TEST_DATA.getName(),"
            + "#projectId, 'READ')")
    @AuditAction(auditAction = "Get row value. projectId {{#projectId}}, tableTitle {{#tableTitle}}")
    @GetMapping(path = "/table/row")
    public Map<String, Object> getTableRow(@RequestParam UUID projectId,
                                           @RequestParam(required = false) UUID systemId,
                                           @RequestParam String tableTitle,
                                           @RequestParam String columnName,
                                           @RequestParam String searchValue,
                                           @RequestParam(required = false) boolean occupied) {
        return testDataService.getTableRow(projectId, systemId, tableTitle, columnName, searchValue, occupied);
    }

    @Operation(description = "Changes table title.")
    @PreAuthorize("@entityAccess.checkAccess("
            + "T(org.qubership.atp.tdm.utils.UsersManagementEntities).TEST_DATA.getName(),"
            + "@catalogRepository.findByTableName(#changeTitleRequest.tableName).getProjectId(), 'UPDATE')")
    @AuditAction(auditAction = "Changes table title. tableTitle{{#changeTitleRequest.tableTitle}} "
            + "tableName{{#changeTitleRequest.tableName}}")
    @PutMapping(value = "/change/title")
    public boolean changeTestDataTitle(@RequestBody ChangeTitleRequest changeTitleRequest) {
        return testDataService.changeTestDataTitle(changeTitleRequest.getTableName(),
                changeTitleRequest.getTableTitle());
    }

    /**
     * Update Table By Sql.
     */
    @Operation(description = "Update existing table by sql.")
    @PreAuthorize("@entityAccess.checkAccess("
            + "T(org.qubership.atp.tdm.utils.UsersManagementEntities).TEST_DATA.getName(),"
            + "#updateByQuery.projectId, 'UPDATE')")
    @AuditAction(auditAction = "Update existing table {{#tableName}}, projectId {{#projectId}} by sql.")
    @PostMapping(value = "/update/sql")
    public ImportTestDataStatistic updateTableBySql(@RequestBody TestDataTableUpdateByQuery updateByQuery) {
        metricService.incrementUpdateAction(updateByQuery.getProjectId().toString());
        return testDataService.updateTestDataBySql(updateByQuery.getProjectId(), updateByQuery.getEnvironmentId(),
                updateByQuery.getSystemId(), updateByQuery.getTableName(), updateByQuery.getQuery(),
                updateByQuery.getQueryTimeout());
    }

    /**
     * Method to support implementation (ATPII-12007).
     * For all tables add new column "CREATED_WHEN"
     */
    @Operation(description = "Old update.")
    @AuditAction(auditAction = "Old update.")
    @GetMapping(path = "/alter/created/when")
    public void alterCreatedWhenColumn() {
        testDataService.alterCreatedWhenColumn();
    }

    @Operation(description = "Old update.")
    @AuditAction(auditAction = "Old update.")
    @GetMapping(path = "/fill/envId")
    public void fillEnvIdColumn() {
        testDataService.fillEnvIdColumn();
    }

    /**
     * Alter Occupy Statistic.
     */
    @Operation(description = "Old update.")
    @AuditAction(auditAction = "Old update.")
    @GetMapping(path = "/alter/occupy/statistic")
    public void alterOccupyStatistic() {
        testDataService.alterOccupyStatistic();
    }

    @Operation(description = "Resolve discrepancy TestDataFlagsTable and TestDataTableCatalog.")
    @AuditAction(auditAction = "Resolve discrepancy TestDataFlagsTable and TestDataTableCatalog.")
    @GetMapping(path = "/resolve/discrepancy/testDataFlagsTableAndTestDataTableCatalog")
    public void resolveDiscrepancyTestDataFlagsTableAndTestDataTableCatalog() {
        testDataService.resolveDiscrepancyTestDataFlagsTableAndTestDataTableCatalog();
    }

    @Operation(description = "Get all distinct values by system_id and column_name")
    @GetMapping(value = "/data/available/recalculate")
    public List<TableColumnValues> getDistinctColumnValues(
            @RequestParam UUID systemId, @RequestParam String columnName, @RequestParam UUID environmentId) {
        return testDataService.getDistinctTablesColumnValues(systemId, environmentId, columnName);
    }
}

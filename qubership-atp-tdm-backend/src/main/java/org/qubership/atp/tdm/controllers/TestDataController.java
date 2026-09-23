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
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequestMapping("/api/tdm")
@RestController()
@Tag(name = "test-data-controller", description = "Test data tables: import, read, occupy and release rows, "
        + "export, and maintenance. A test data table has a title shown in the UI and a generated database table "
        + "name; the tableName parameters take the database table name.")
public class TestDataController /* implements TestDataControllerApi */ {

    private final TestDataService testDataService;
    private final MetricService metricService;


    @Autowired
    public TestDataController(@Nonnull TestDataService testDataService, @Nonnull MetricService metricService) {
        this.testDataService = testDataService;
        this.metricService = metricService;
    }

    @Operation(summary = "List the tables of a project",
            description = "Returns the catalog entries of the tables, with the import query and timeout of tables "
                    + "imported by SQL.")
    @AuditAction(auditAction = "Get test data tables catalog. ProjectId {{#projectId}}")
    @GetMapping(value = "/tables/catalog")
    public List<TestDataTableCatalog> getTestDataTablesCatalog(
            @Parameter(description = "Project ID.")
            @RequestParam UUID projectId,
            @Parameter(description = "System ID. When omitted, lists the tables of all systems.")
            @RequestParam(required = false) UUID systemId) {
        log.info("Fetching table {}", projectId.toString());
        return testDataService.getTestDataTablesCatalog(projectId, systemId);
    }

    @Operation(summary = "List the table names of a project",
            description = "Returns the project ID and the database table names of its tables.")
    @PreAuthorize("@entityAccess.checkAccess("
            + "T(org.qubership.atp.tdm.utils.UsersManagementEntities).TEST_DATA.getName(),"
            + "#projectId, 'READ')")
    @AuditAction(auditAction = "Get tables under projectId {{#projectId}}")
    @GetMapping(value = "/tables/list")
    public TdmDataToExport getTestDataTablesList(
            @Parameter(description = "Project ID.")
            @RequestParam UUID projectId) {
        return testDataService.tablesToExport(projectId);
    }

    @Operation(summary = "List the tables of an environment",
            description = "Returns a map of database table names to table titles.")
    @PreAuthorize("@entityAccess.checkAccess("
            + "T(org.qubership.atp.tdm.utils.UsersManagementEntities).TEST_DATA.getName(),"
            + "#projectId, 'READ')")
    @AuditAction(auditAction = "Get tables id and name under project {{#projectId}} and environment {{#envId}}")
    @GetMapping(value = "/environment/tables/list")
    public Map<String, String> getTestDataTablesListByEnvironment(
            @Parameter(description = "Project ID.")
            @RequestParam UUID projectId,
            @Parameter(description = "Environment ID.")
            @RequestParam UUID envId) {
        MdcUtils.put(MdcField.ENVIRONMENT_ID.toString(), envId);
        return testDataService.tablesToExportByEnvironment(projectId, envId);
    }

    /**
     * Import Excel TestData.
     */
    @Operation(summary = "Import rows from an Excel file",
            description = "Adds the rows of the file to the table with this title in the system, or creates the "
                    + "table. environmentId and systemId are required: without them the import fails.")
    @PreAuthorize("@entityAccess.checkAccess("
            + "T(org.qubership.atp.tdm.utils.UsersManagementEntities).TEST_DATA.getName(),"
            + "#projectId, 'CREATE')")
    @AuditAction(auditAction = "Import excel to TDM. ProjectId {{#projectId}}, TableTitle {{#tableTitle}}")
    @PostMapping(value = "/import/excel")
    public List<ImportTestDataStatistic> importExcelTestData(
            @Parameter(description = "Project ID.")
            @RequestParam UUID projectId,
            @Parameter(description = "Environment ID.")
            @RequestParam(required = false) UUID environmentId,
            @Parameter(description = "System ID.")
            @RequestParam(required = false) UUID systemId,
            @Parameter(description = "Title of the table.")
            @RequestParam String tableTitle,
            @Parameter(description = "After the import, updates the rows with the SQL query saved for the table.")
            @RequestParam Boolean runSqlScript,
            @Parameter(description = "Excel file with the rows.")
            @RequestParam MultipartFile file) {
        metricService.incrementInsertAction(projectId);
        return testDataService.importExcelTestData(projectId, environmentId, systemId, tableTitle, runSqlScript, file);
    }

    /**
     * Import Sql TestData.
     */
    @Operation(operationId = "importSqlTestData", summary = "Import rows with an SQL query",
            description = "For each environment, runs the query against the database of the system with this name, "
                    + "and adds the result to the table with this title, or creates the table. The query and timeout "
                    + "are saved for refresh. Returns one import result per environment; a missing environment or "
                    + "system is reported in the result.")
    @PreAuthorize("@entityAccess.checkAccess("
            + "T(org.qubership.atp.tdm.utils.UsersManagementEntities).TEST_DATA.getName(),"
            + "#projectId, 'CREATE')")
    @AuditAction(auditAction = "Import sql to TDM. ProjectId {{#projectId}}, TableTitle {{#tableTitle}}")
    @PostMapping(value = "/import/sql")
    public List<ImportTestDataStatistic> importSqlTestData(
            @Parameter(description = "Project ID.")
            @RequestParam UUID projectId,
            @Parameter(description = "IDs of the environments to import from.")
            @RequestParam List<UUID> environmentsIds,
            @Parameter(description = "Name of the system whose DB connection runs the query.")
            @RequestParam String systemName,
            @Parameter(description = "Title of the table.")
            @RequestParam String tableTitle,
            @Parameter(description = "SQL query that returns the rows.")
            @RequestParam String query,
            @Parameter(description = "Query timeout, in seconds.")
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
    @Operation(summary = "Read rows of a table",
            description = "Returns a page of rows that match the filters in the request body, sorted as requested, "
                    + "from the available rows or, with occupied set, from the occupied rows.")
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

    @Operation(summary = "Occupy rows by ID",
            description = "Marks the rows as occupied by the user in occupiedBy and records the occupation in the "
                    + "statistics.")
    @PreAuthorize("@entityAccess.checkAccess("
            + "T(org.qubership.atp.tdm.utils.UsersManagementEntities).TEST_DATA.getName(),"
            + "@catalogRepository.findByTableName(#tableName).getProjectId(), 'UPDATE')")
    @AuditAction(auditAction = "Occupy test data. Table Name {{#tableName}}")
    @PutMapping(value = "/occupy")
    public void occupyTestData(
            @Parameter(description = "Database table name of the test data table.")
            @RequestParam String tableName,
            @Parameter(description = "Name of the user who occupies the rows.")
            @RequestParam String occupiedBy,
            @Parameter(description = "ROW_ID values of the rows.")
            @RequestBody List<UUID> rows) {
        metricService.incrementOccupyAction(MDC.get(MdcField.PROJECT_ID.toString()));
        testDataService.occupyTestData(tableName, occupiedBy, rows);
    }

    @Operation(summary = "Release rows by ID",
            description = "Makes the rows available again and removes their occupation records from the statistics.")
    @PreAuthorize("@entityAccess.checkAccess("
            + "T(org.qubership.atp.tdm.utils.UsersManagementEntities).TEST_DATA.getName(),"
            + "@catalogRepository.findByTableName(#tableName).getProjectId(), 'UPDATE')")
    @AuditAction(auditAction = "Release test data. Table Name {{#tableName}}")
    @PutMapping(value = "/release")
    public void releaseTestData(
            @Parameter(description = "Database table name of the test data table.")
            @RequestParam String tableName,
            @Parameter(description = "ROW_ID values of the rows.")
            @RequestBody List<UUID> rows) {
        metricService.incrementReleaseAction(MDC.get(MdcField.PROJECT_ID.toString()));
        testDataService.releaseTestData(tableName, rows);
    }

    @Operation(summary = "Delete rows by ID")
    @PreAuthorize("@entityAccess.checkAccess("
            + "T(org.qubership.atp.tdm.utils.UsersManagementEntities).TEST_DATA.getName(),"
            + "@catalogRepository.findByTableName(#tableName).getProjectId(), 'UPDATE')")
    @AuditAction(auditAction = "Delete selected rows from table {{#tableName}}")
    @PutMapping(value = "/delete/rows")
    public void deleteTestDataTableRows(
            @Parameter(description = "Database table name of the test data table.")
            @RequestParam String tableName,
            @Parameter(description = "ROW_ID values of the rows.")
            @RequestBody List<UUID> rows) {
        metricService.incrementDeleteAction(MDC.get(MdcField.PROJECT_ID.toString()));
        testDataService.deleteTestDataTableRows(tableName, rows);
    }

    @Operation(summary = "Delete a table",
            description = "Drops the database table, removes it from the catalog, and stops its scheduled refresh.")
    @PreAuthorize("@entityAccess.checkAccess("
            + "T(org.qubership.atp.tdm.utils.UsersManagementEntities).TEST_DATA.getName(),"
            + "@catalogRepository.findByTableName(#tableName).getProjectId(), 'DELETE')")
    @AuditAction(auditAction = "Drop selected table {{#tableName}}")
    @DeleteMapping(value = "/table")
    public void deleteTestData(
            @Parameter(description = "Database table name of the test data table.")
            @RequestParam String tableName) {
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
    @Operation(summary = "Delete all rows of a table",
            description = "Returns HTTP 400 when the project, or the system when given, has no table with this name.")
    @PreAuthorize("@entityAccess.checkAccess("
            + "T(org.qubership.atp.tdm.utils.UsersManagementEntities).TEST_DATA.getName(),"
            + "#projectId, 'DELETE')")
    @AuditAction(auditAction = "Truncate data in table {{#tableName}} in projectId {{#projectId}}")
    @DeleteMapping("/truncate/table")
    public ResponseMessage truncateDataInTable(
            @Parameter(description = "Database table name of the test data table.")
            @RequestParam String tableName,
            @Parameter(description = "Project ID.")
            @RequestParam UUID projectId,
            @Parameter(description = "System ID.")
            @RequestParam(required = false) UUID systemId) {
        metricService.incrementDeleteAction(projectId.toString());
        testDataService.truncateDataInTable(tableName, projectId, systemId);
        return new ResponseMessage(ResponseType.SUCCESS, String.format("Data has been cleaned in table with tableName"
                + " = \"%s\".", tableName));
    }

    /**
     * Get TestDataTable As Excel File.
     */
    @Operation(summary = "Download a table as an Excel file")
    @PreAuthorize("@entityAccess.checkAccess("
            + "T(org.qubership.atp.tdm.utils.UsersManagementEntities).TEST_DATA.getName(),"
            + "@catalogRepository.findByTableName(#tableName).getProjectId(), 'READ')")
    @AuditAction(auditAction = "Download table {{#tableName}} as excel file.")
    @GetMapping(path = "/download/excel")
    public ResponseEntity<InputStreamResource> getTestDataTableAsExcelFile(
            @Parameter(description = "Database table name of the test data table.")
            @RequestParam String tableName)
            throws IOException {
        metricService.incrementGetAction(MDC.get(MdcField.PROJECT_ID.toString()));
        File testDataTableAsExcelFile = testDataService.getTestDataTableAsExcelFile(tableName);
        return HttpUtils.buildFileResponseEntity(testDataTableAsExcelFile,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    }

    /**
     * Get TestDataTable As Csv File.
     */
    @Operation(summary = "Download a table as a CSV file")
    @PreAuthorize("@entityAccess.checkAccess("
            + "T(org.qubership.atp.tdm.utils.UsersManagementEntities).TEST_DATA.getName(),"
            + "@catalogRepository.findByTableName(#tableName).getProjectId(), 'READ')")
    @AuditAction(auditAction = "Download table {{#tableName}} as csv file.")
    @GetMapping(path = "/download/csv")
    public ResponseEntity<InputStreamResource> getTestDataTableAsCsvFile(
            @Parameter(description = "Database table name of the test data table.")
            @RequestParam String tableName)
            throws IOException {
        metricService.incrementGetAction(MDC.get(MdcField.PROJECT_ID.toString()));
        File testDataTableAsCsvFile = testDataService.getTestDataTableAsCsvFile(tableName);
        return HttpUtils.buildFileResponseEntity(testDataTableAsCsvFile, "text/csv");
    }

    /**
     * Method fixes issue with occupation functional (ATPII-10699).
     * For all tables add new column "OCCUPIED_BY"
     */
    @Operation(summary = "Add the OCCUPIED_BY column to all tables",
            description = "Adds the OCCUPIED_BY column to every test data table that lacks it. A migration for "
                    + "tables created before the column existed.")
    @AuditAction(auditAction = "Old update.")
    @GetMapping(path = "/fix/occupied/by/column")
    public void alterOccupiedByColumn() {
        testDataService.alterOccupiedByColumn();
    }

    /**
     * Getting link preview.
     */
    @Operation(summary = "Preview the link of a column",
            description = "Returns, as a JSON string, the link that setup would build for the column: the value of "
                    + "the first row of the column when pickUpFullLinkFromTableCell is set, otherwise the url "
                    + "parameter of the system HTTP connection joined with the endpoint from the request body.")
    @PreAuthorize("@entityAccess.checkAccess("
            + "T(org.qubership.atp.tdm.utils.UsersManagementEntities).TEST_DATA.getName(),"
            + "#projectId, 'CREATE')")
    @AuditAction(auditAction = "Get preview for linker. ProjectId {{#projectId}}, TableName {{#tableName}}")
    @PostMapping(path = "/link/preview")
    public ResponseEntity<String> getPreviewLink(
            @Parameter(description = "Project ID.")
            @RequestParam UUID projectId,
            @Parameter(description = "System ID.")
            @RequestParam(required = false) UUID systemId,
            @Parameter(description = "Name of the column.")
            @RequestParam String columnName,
            @Parameter(description = "Database table name; used with pickUpFullLinkFromTableCell.")
            @RequestParam(required = false) String tableName,
            @Parameter(description = "Takes the whole link from the column value instead of building it.")
            @RequestParam boolean pickUpFullLinkFromTableCell,
            @Parameter(description = "Endpoint to join with the url parameter of the system HTTP connection.")
            @RequestBody(required = false) String endpoint) {
        String link = testDataService.getPreviewLink(projectId, systemId, endpoint,
                columnName, tableName, pickUpFullLinkFromTableCell);
        return ResponseEntity.ok(new Gson().toJson(link));
    }

    @Operation(summary = "Turn the values of a column into links",
            description = "Saves the link settings for the column of one table, or, with isAll, of the tables with "
                    + "the same title in all environments of the project.")
    @PreAuthorize("@entityAccess.checkAccess("
            + "T(org.qubership.atp.tdm.utils.UsersManagementEntities).TEST_DATA.getName(),"
            + "#projectId, 'CREATE')")
    @AuditAction(auditAction = "Setup links. ProjectId {{#projectId}}, TableName {{#tableName}}")
    @PostMapping(path = "/link/setup")
    public void setupColumnLinks(
            @Parameter(description = "Applies the settings to all tables with the same title.")
            @RequestParam Boolean isAll,
            @Parameter(description = "Project ID.")
            @RequestParam UUID projectId,
            @Parameter(description = "System ID.")
            @RequestParam(required = false) UUID systemId,
            @Parameter(description = "Database table name of the test data table.")
            @RequestParam(required = false) String tableName,
            @Parameter(description = "Name of the column.")
            @RequestParam String columnName,
            @Parameter(description = "Value of the validate-unoccupied-resources flag saved for the table.")
            @RequestParam Boolean validateUnoccupiedResources,
            @Parameter(description = "Takes the whole link from the column value instead of building it.")
            @RequestParam boolean pickUpFullLinkFromTableCell,
            @Parameter(description = "Endpoint to join with the url parameter of the system HTTP connection.")
            @RequestBody(required = false) String endpoint) {
        testDataService.setupColumnLinks(isAll, projectId, systemId, tableName, columnName, endpoint,
                validateUnoccupiedResources, pickUpFullLinkFromTableCell);
    }

    @Operation(summary = "Get the validation flags of a table")
    @PreAuthorize("@entityAccess.checkAccess("
            + "T(org.qubership.atp.tdm.utils.UsersManagementEntities).TEST_DATA.getName(),"
            + "@catalogRepository.findByTableName(#tableName).getProjectId(), 'READ')")
    @AuditAction(auditAction = "Check flag: is unoccupied validation for table {{#tableName}}.")
    @GetMapping(value = "/validation/unoccupied")
    public TestDataFlagsTable isUnoccupiedValidation(
            @Parameter(description = "Database table name of the test data table.")
            @RequestParam("tableName") String tableName) {
        return testDataService.getUnoccupiedValidationFlagStatus(tableName);
    }

    @Operation(summary = "List the environments of a table title",
            description = "Returns the IDs of the environments that have a table with this title in the project.")
    @PreAuthorize("@entityAccess.checkAccess("
            + "T(org.qubership.atp.tdm.utils.UsersManagementEntities).TEST_DATA.getName(),"
            + "#projectId, 'READ')")
    @AuditAction(auditAction = "Get Table Environments by projectId {{#projectId}} and tableTitle {{#tableTitle}}")
    @GetMapping(path = "/table/environments")
    public EnvsList getTableEnvironments(
            @Parameter(description = "Project ID.")
            @RequestParam("projectId") UUID projectId,
            @Parameter(description = "Title of the table.")
            @RequestParam("tableTitle") String tableTitle) {
        return testDataService.getTableEnvironments(projectId, tableTitle);
    }

    /**
     * Replaces macroses (related to internal TDM table) with real values.
     *
     * @param tableName - table name
     * @param query     - source query
     */
    @Operation(summary = "Substitute table values into a query",
            description = "Replaces the ${...} macros in the query from the request body with values from the table, "
                    + "and returns the result in the query field.")
    @PreAuthorize("@entityAccess.checkAccess("
            + "T(org.qubership.atp.tdm.utils.UsersManagementEntities).TEST_DATA.getName(),"
            + "@catalogRepository.findByTableName(#tableName).getProjectId(), 'UPDATE')")
    @AuditAction(auditAction = "Evaluate Query for table {{#tableName}}")
    @PutMapping(value = "/evaluate/query")
    public Map<String, String> evaluateQuery(
            @Parameter(description = "Database table name of the test data table.")
            @RequestParam("tableName") String tableName,
            @Parameter(description = "Query with macros.")
            @RequestBody String query) {
        metricService.incrementUpdateAction(MDC.get(MdcField.PROJECT_ID.toString()));
        Map<String, String> result = new HashMap<>();
        result.put("query", testDataService.evaluateQuery(tableName, query));
        return result;
    }

    @Operation(summary = "List the distinct values of a column",
            description = "Returns the distinct values of the column among the available rows, or among the occupied "
                    + "rows when occupied is true.")
    @PreAuthorize("@entityAccess.checkAccess("
            + "T(org.qubership.atp.tdm.utils.UsersManagementEntities).TEST_DATA.getName(),"
            + "@catalogRepository.findByTableName(#tableName).getProjectId(), 'READ')")
    @AuditAction(auditAction = "Get column distinct values. TableName {{#tableName}}")
    @GetMapping(path = "/table/column/distinct/values")
    public ColumnValues getColumnDistinctValues(
            @Parameter(description = "Database table name of the test data table.")
            @RequestParam("tableName") String tableName,
            @Parameter(description = "Name of the column.")
            @RequestParam("columnName") String columnName,
            @Parameter(description = "Reads the occupied rows instead of the available ones.")
            @RequestParam("occupied") Boolean occupied) {
        return testDataService.getColumnDistinctValues(tableName, columnName, occupied);
    }

    @Operation(summary = "Find a row by column value",
            description = "Returns the first row of the table with this title whose column equals searchValue, "
                    + "case-sensitively.")
    @PreAuthorize("@entityAccess.checkAccess("
            + "T(org.qubership.atp.tdm.utils.UsersManagementEntities).TEST_DATA.getName(),"
            + "#projectId, 'READ')")
    @AuditAction(auditAction = "Get row value. projectId {{#projectId}}, tableTitle {{#tableTitle}}")
    @GetMapping(path = "/table/row")
    public Map<String, Object> getTableRow(
            @Parameter(description = "Project ID.")
            @RequestParam UUID projectId,
            @Parameter(description = "System ID.")
            @RequestParam(required = false) UUID systemId,
            @Parameter(description = "Title of the table.")
            @RequestParam String tableTitle,
            @Parameter(description = "Name of the column to search in.")
            @RequestParam String columnName,
            @Parameter(description = "Value to search for.")
            @RequestParam String searchValue,
            @Parameter(description = "Searches the occupied rows instead of the available ones.")
            @RequestParam(required = false) boolean occupied) {
        return testDataService.getTableRow(projectId, systemId, tableTitle, columnName, searchValue, occupied);
    }

    @Operation(summary = "Rename a table",
            description = "Changes the title of the table and of its occupation statistics. Returns whether the title "
                    + "changed.")
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
    @Operation(summary = "Update rows with an SQL query",
            description = "Runs the query against the database of the system and updates the table rows from its "
                    + "result. The query and timeout are saved with the table.")
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
    @Operation(summary = "Add the CREATED_WHEN column to all tables",
            description = "Adds the CREATED_WHEN column to every test data table that lacks it. A migration for "
                    + "tables created before the column existed.")
    @AuditAction(auditAction = "Old update.")
    @GetMapping(path = "/alter/created/when")
    public void alterCreatedWhenColumn() {
        testDataService.alterCreatedWhenColumn();
    }

    @Operation(summary = "Fill in the environment of existing tables",
            description = "Sets the environment ID in the catalog entries of tables that have a system. Deletes the "
                    + "tables of a project whose loading fails with a \"not found\" error. A migration for tables "
                    + "created before the catalog stored the environment.")
    @AuditAction(auditAction = "Old update.")
    @GetMapping(path = "/fill/envId")
    public void fillEnvIdColumn() {
        testDataService.fillEnvIdColumn();
    }

    /**
     * Alter Occupy Statistic.
     */
    @Operation(summary = "Fill in the occupation statistics from occupied rows",
            description = "Adds an occupation record to the statistics for every occupied row of every table. A "
                    + "migration for rows occupied before the statistics existed.")
    @AuditAction(auditAction = "Old update.")
    @GetMapping(path = "/alter/occupy/statistic")
    public void alterOccupyStatistic() {
        testDataService.alterOccupyStatistic();
    }

    @Operation(summary = "Align the flags table with the catalog",
            description = "Adds default validation flags for catalog tables that have none, and deletes flags of "
                    + "tables that are not in the catalog.")
    @AuditAction(auditAction = "Resolve discrepancy TestDataFlagsTable and TestDataTableCatalog.")
    @GetMapping(path = "/resolve/discrepancy/testDataFlagsTableAndTestDataTableCatalog")
    public void resolveDiscrepancyTestDataFlagsTableAndTestDataTableCatalog() {
        testDataService.resolveDiscrepancyTestDataFlagsTableAndTestDataTableCatalog();
    }

    @Operation(summary = "List column values across the tables of a system",
            description = "Returns, for each table of the system in the environment that has the column, the distinct "
                    + "values of the column.")
    @GetMapping(value = "/data/available/recalculate")
    public List<TableColumnValues> getDistinctColumnValues(
            @Parameter(description = "System ID.")
            @RequestParam UUID systemId,
            @Parameter(description = "Name of the column.")
            @RequestParam String columnName,
            @Parameter(description = "Environment ID.")
            @RequestParam UUID environmentId) {
        return testDataService.getDistinctTablesColumnValues(systemId, environmentId, columnName);
    }
}

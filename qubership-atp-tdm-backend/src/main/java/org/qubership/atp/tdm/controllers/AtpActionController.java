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

import java.util.List;

import org.qubership.atp.integration.configuration.configuration.AuditAction;
import org.qubership.atp.tdm.model.rest.ResponseMessage;
import org.qubership.atp.tdm.model.rest.requests.RestApiRequest;
import org.qubership.atp.tdm.service.AtpActionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Nonnull;

@RequestMapping("/api/tdm/rest")
@RestController()
@Tag(name = "atp-action-controller", description = "Operations that ATP actions call on a test data table. "
        + "A request names the table by projectName, envName, systemName, and title-table; projectName is a project "
        + "name from PROJECTS_INFO. When envName or systemName is missing, the first table with that title in the "
        + "project is used. A trailing timestamp such as \" 2024-05-01T10:15:30\" in envName is ignored. Most "
        + "operations return one ResponseMessage per row request, with type SUCCESS or ERROR and HTTP status 200 in "
        + "both cases, so one failed row request does not stop the others.")
public class AtpActionController /* implements AtpActionControllerApi */ {

    private final AtpActionService service;

    @Autowired
    public AtpActionController(@Nonnull AtpActionService service) {
        this.service = service;
    }

    @Operation(summary = "Insert rows into a table",
            description = "Inserts the rows from insert-records. When no table with this title exists, creates "
                    + "the table first.")
    @AuditAction(auditAction = "ATP Action. Insert records to project {{#request.projectName}} "
            + "to table {{#request.titleTable}}")
    @PostMapping(value = "/insert-records")
    public ResponseMessage insertTestData(@RequestBody RestApiRequest request) {
        return service.insertTestData(request.getProjectName(), request.getEnvName(),
                request.getSystemName(), request.getTitleTable(), request.getRecords());
    }

    /**
     * Allow to occupy records under ATP_USER.
     *
     * @param request - RestApiRequest
     * @return List of ResponseMessages
     */
    @Operation(operationId = "atpOccupyTestData", summary = "Occupy rows and return one column",
            description = "For each request in occupy-row-requests, occupies the first available row that "
                    + "matches search-row-parameters-set and returns the value of name-column-response. "
                    + "The row is marked as occupied by ATP_User.")
    @AuditAction(auditAction = "ATP Action. Occupy test data to project {{#request.projectName}} "
            + "to table {{#request.titleTable}}")
    @PostMapping(value = "/occupy-records")
    public List<ResponseMessage> occupyTestData(@RequestBody RestApiRequest request) {
        return service.occupyTestData(request.getProjectName(), request.getEnvName(),
                request.getSystemName(), request.getTitleTable(), request.getOccupyRowRequests());
    }

    /**
     * Allow to occupy records under ATP_USER.
     *
     * @param request - RestApiRequest
     * @return List of ResponseMessages
     */
    @Operation(summary = "Occupy rows and return several columns",
            description = "For each request in occupy-full-row-requests, occupies the first available row that "
                    + "matches search-row-parameters-set and returns the values of response-column-names as a "
                    + "JSON object. The row stays available when one of the columns does not exist. "
                    + "The row is marked as occupied by ATP_User.")
    @AuditAction(auditAction = "ATP Action. Occupy test data to project {{#request.projectName}} "
            + "to table {{#request.titleTable}}")
    @PostMapping(value = "/occupy-records-full-row")
    public List<ResponseMessage> occupyTestDataFullRow(@RequestBody RestApiRequest request) {
        return service.occupyTestDataFullRow(request.getProjectName(), request.getEnvName(),
                request.getSystemName(), request.getTitleTable(), request.getOccupyFullRowRequests());
    }

    /**
     * Allow to release occupied records.
     *
     * @param request - RestApiRequest
     * @return List of ResponseMessages
     */
    @Operation(operationId = "atpReleaseTestData", summary = "Release occupied rows",
            description = "For each request in release-row-requests, releases the occupied row that matches "
                    + "search-row-parameters-set and returns the value of name-column-response. The request "
                    + "fails when more than one occupied row matches.")
    @AuditAction(auditAction = "ATP Action. Release test data to project {{#request.projectName}} "
            + "to table {{#request.titleTable}}")
    @PostMapping(value = "/release-records")
    public List<ResponseMessage> releaseTestData(@RequestBody RestApiRequest request) {
        return service.releaseTestData(request.getProjectName(), request.getEnvName(),
                request.getSystemName(), request.getTitleTable(), request.getReleaseRowRequests());
    }

    /**
     * Allow to release occupied records.
     *
     * @param request - RestApiRequest
     * @return List of ResponseMessages
     */
    @Operation(summary = "Release all occupied rows of a table")
    @AuditAction(auditAction = "ATP Action. Release test data to project {{#request.projectName}} "
            + "to table {{#request.titleTable}}")
    @PostMapping(value = "/release-records/bulk")
    public List<ResponseMessage> releaseFullTestData(@RequestBody RestApiRequest request) {
        return service.releaseFullTestData(request.getProjectName(), request.getEnvName(),
                request.getSystemName(), request.getTitleTable());
    }

    @Operation(summary = "Update rows",
            description = "For each request in update-row-requests, sets the columns in record-with-data-for-update "
                    + "to the given values in every row that matches search-row-parameters-set, and returns the "
                    + "number of updated rows.")
    @AuditAction(auditAction = "ATP Action. Update test data to project {{#request.projectName}} "
            + "to table {{#request.titleTable}}")
    @PostMapping(value = "/update-records")
    public List<ResponseMessage> updateTestData(@RequestBody RestApiRequest request) {
        return service.updateTestData(request.getProjectName(), request.getEnvName(),
                request.getSystemName(), request.getTitleTable(), request.getUpdateRowRequests());
    }

    @Operation(operationId = "atpGetTestData", summary = "Read one column of a row",
            description = "For each request in get-row-requests, returns the value of name-column-response from "
                    + "the first available row that matches search-row-parameters-set. The row is not occupied.")
    @AuditAction(auditAction = "ATP Action. Get test data from project {{#request.projectName}} "
            + "table {{#request.titleTable}}")
    @PostMapping(value = "/get-record")
    public List<ResponseMessage> getTestData(@RequestBody RestApiRequest request) {
        return service.getTestData(request.getProjectName(), request.getEnvName(),
                request.getSystemName(), request.getTitleTable(), request.getGetRowRequests());
    }

    /**
     * Allow to return values from multiple columns.
     *
     * @param request Request with looking data criteria.
     * @return Object with multiple columns value.
     */
    @Operation(summary = "Read several columns of a row",
            description = "For each request in get-row-requests, returns the values of response-column-names from "
                    + "the first available row that matches search-row-parameters-set, as a JSON object. The row "
                    + "is not occupied.")
    @AuditAction(auditAction = "ATP Action. Get multiple column test data from project {{#request.projectName}} "
            + "table {{#request.titleTable}}")
    @PostMapping(value = "/get-records")
    public List<ResponseMessage> getMultipleColumnTestData(@RequestBody RestApiRequest request) {
        return service.getMultipleColumnTestData(request.getProjectName(), request.getEnvName(),
                request.getSystemName(), request.getTitleTable(), request.getGetRowRequests());
    }

    @Operation(summary = "Add values to rows",
            description = "For each request in add-info-to-row-requests, appends each value in "
                    + "record-with-data-for-update on a new line to the current value of its column, in every row "
                    + "that matches search-row-parameters-set, and returns the number of updated rows.")
    @AuditAction(auditAction = "ATP Action. Add info to row in table {{#request.titleTable}} "
            + "in project {{#request.projectName}}")
    @PostMapping(value = "/add-info-to-row")
    public List<ResponseMessage> addInfoToRow(@RequestBody RestApiRequest request) {
        return service.addInfoToRow(request.getProjectName(), request.getEnvName(),
                request.getSystemName(), request.getTitleTable(), request.getAddInfoToRowRequests());
    }

    @Operation(summary = "Refresh tables by title",
            description = "Runs the refresh query of the table with this title in the system, or of every table "
                    + "with this title in the project when envName or systemName is missing.")
    @AuditAction(auditAction = "ATP Action. Refresh tables by name {{#request.titleTable}} "
            + "in project {{#request.projectName}}")
    @PostMapping(value = "/refresh-tables")
    public List<ResponseMessage> refreshTables(@RequestBody RestApiRequest request) {
        return service.refreshTables(request.getProjectName(), request.getEnvName(), request.getSystemName(),
                request.getTitleTable());
    }

    @Operation(summary = "Delete all rows of a table",
            description = "Unlike the other operations, projectName must hold the project ID.")
    @AuditAction(auditAction = "ATP Action. Truncate table {{#request.titleTable}} "
            + "in project {{#request.projectName}}")
    @PostMapping(value = "/truncate-table")
    public List<ResponseMessage> truncateTable(@RequestBody RestApiRequest request) {
        return service.truncateTable(request.getProjectName(), request.getEnvName(), request.getSystemName(),
                request.getTitleTable());
    }

    @Operation(summary = "Run the cleanup of a table",
            description = "Runs the cleanup configured for the table, and returns the number of removed rows. "
                    + "Unlike the other operations, projectName must hold the project ID.")
    @AuditAction(auditAction = "ATP Action. Run cleanup for table {{#request.titleTable}} "
            + "in project {{#request.projectName}}")
    @PostMapping(value = "/run-cleanup-table")
    public List<ResponseMessage> runCleanupForTable(@RequestBody RestApiRequest request) {
        return service.runCleanupForTable(request.getProjectName(), request.getEnvName(), request.getSystemName(),
                request.getTitleTable());
    }

    @Operation(summary = "Get the database table name of a table",
            description = "Returns the name of the database table that stores the table with this title. All four "
                    + "fields are required; a missing field or an unknown table returns HTTP 400.")
    @AuditAction(auditAction = "ATP Action. Returns Table name based on {{#request.titleTable}}.")
    @PostMapping(value = "/resolve-table")
    public ResponseMessage resolveTableName(@RequestBody RestApiRequest request) {
        return service.resolveTableName(request.getProjectName(), request.getEnvName(),
                request.getSystemName(), request.getTitleTable());
    }
}

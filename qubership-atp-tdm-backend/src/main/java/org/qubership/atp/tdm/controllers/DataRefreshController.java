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

import java.text.ParseException;
import java.util.List;
import java.util.UUID;

import org.qubership.atp.integration.configuration.configuration.AuditAction;
import org.qubership.atp.tdm.model.refresh.RefreshResults;
import org.qubership.atp.tdm.model.refresh.TestDataRefreshConfig;
import org.qubership.atp.tdm.service.DataRefreshService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.google.gson.Gson;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Nonnull;

@RequestMapping("/api/tdm/data/refresh")
@RestController()
@Tag(name = "data-refresh-controller", description = "Refresh settings of test data tables. A refresh deletes the "
        + "rows of a table and fills it again from the SQL query the table was imported with.")
public class DataRefreshController /* implements DataRefreshControllerApi */ {

    private final DataRefreshService dataRefreshService;

    @Autowired
    public DataRefreshController(@Nonnull DataRefreshService dataRefreshService) {
        this.dataRefreshService = dataRefreshService;
    }

    /**
     * Get refresh configuration for specified dataset / table ID.
     *
     * @param id - refresh config id
     * @return refresh configuration object.
     */
    @Operation(summary = "Get refresh settings")
    @PreAuthorize("@entityAccess.checkAccess("
            + "T(org.qubership.atp.tdm.utils.UsersManagementEntities).TEST_DATA.getName(),"
            + "@catalogRepository.findByRefreshConfigId(#id).getProjectId(), 'READ')")
    @AuditAction(auditAction = "Get refresh configuration by id {{#id}}")
    @GetMapping(path = {"/config/{id}"})
    public ResponseEntity<TestDataRefreshConfig> getRefreshConfig(
            @Parameter(description = "Refresh configuration ID.") @PathVariable("id") UUID id) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .body(dataRefreshService.getRefreshConfig(id));
    }

    /**
     * Save / update data refresh settings.
     *
     * @param tableName Name of table
     * @param queryTimeout Query timeout value in seconds
     * @param refreshConfig TestDataRefreshConfig object
     * @return TestDataRefreshConfig object after saving
     * @throws Exception in case errors while config saving.
     */
    @Operation(summary = "Save refresh settings",
            description = "Saves the refresh configuration and the query timeout for the table, and schedules "
                    + "the refresh. With allEnv set in the configuration, it also applies to the tables of the "
                    + "project with the same title and the same import query. A queryTimeout out of range, or a "
                    + "table whose environment cannot run SQL queries, returns HTTP 400.")
    @PreAuthorize("@entityAccess.checkAccess("
            + "T(org.qubership.atp.tdm.utils.UsersManagementEntities).TEST_DATA.getName(),"
            + "@catalogRepository.findByTableName(#tableName).getProjectId(), 'CREATE')")
    @AuditAction(auditAction = "Save / update data refresh settings. Table {{#tableName}}")
    @PostMapping(value = "/config")
    public TestDataRefreshConfig saveRefreshConfig(
            @Parameter(description = "Database table name of the test data table.")
            @RequestParam("tableName") String tableName,
            @Parameter(description = "Timeout of the import query, in seconds, from 1 to "
                    + "EXTERNAL_QUERY_MAX_TIMEOUT (3600 by default).")
            @RequestParam Integer queryTimeout,
            @RequestBody TestDataRefreshConfig refreshConfig) throws Exception {
        return dataRefreshService.saveRefreshConfig(tableName, queryTimeout, refreshConfig);
    }

    /**
     * Force run data refresh.
     *
     * @param tableName Name of table
     * @param queryTimeout Query timeout value in seconds
     * @param allEnv Flag if the action should be applied to all environments (true) or not
     * @return List of RefreshResults after refresh running
     * @throws Exception in case errors while refresh running.
     */
    @Operation(summary = "Run a refresh now",
            description = "Deletes all rows of the table, occupied rows included, and runs the import query "
                    + "again with the saved query timeout. Returns the number of loaded rows per table.")
    @PreAuthorize("@entityAccess.checkAccess("
            + "T(org.qubership.atp.tdm.utils.UsersManagementEntities).TEST_DATA.getName(),"
            + "@catalogRepository.findByTableName(#tableName).getProjectId(), 'CREATE')")
    @AuditAction(auditAction = "Force run data refresh. Table {{#tableName}}")
    @PostMapping(value = "/run")
    public List<RefreshResults> runDataRefresh(
            @Parameter(description = "Database table name of the test data table.")
            @RequestParam("tableName") String tableName,
            @Parameter(description = "Not used; the refresh runs with the query timeout saved for the table.")
            @RequestParam Integer queryTimeout,
            @Parameter(description = "Also refreshes the tables of the project with the same title and the same "
                    + "import query.")
            @RequestParam boolean allEnv) throws Exception {
        return dataRefreshService.runRefresh(tableName, queryTimeout, allEnv, false);
    }

    /**
     * Get next run's date / time details.
     *
     * @param cronExpression cron expression to calculate next run based on
     * @return ResponseMessage that contains the details
     * @throws ParseException Thrown in case if invalid cron expression was provided.
     */
    @Operation(operationId = "getNextRefreshRun", summary = "Get the next run time of a schedule",
            description = "Returns the next time the Quartz cron expression fires after now, as a JSON string "
                    + "in \"EEE MMM dd HH:mm:ss zzz yyyy\" format.")
    @AuditAction(auditAction = "Get next run's date. cron {{#cronExpression}}")
    @GetMapping(value = "/next/run")
    public ResponseEntity<String> getNextScheduledRun(
            @Parameter(description = "Quartz cron expression, starting with the seconds field.")
            @RequestParam("cronExpression") String cronExpression)
            throws ParseException {
        return ResponseEntity.ok(new Gson().toJson(dataRefreshService.getNextScheduledRun(cronExpression)));
    }
}

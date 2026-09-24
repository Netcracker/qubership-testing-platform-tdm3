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
import org.qubership.atp.tdm.model.cleanup.CleanupResults;
import org.qubership.atp.tdm.model.cleanup.CleanupSettings;
import org.qubership.atp.tdm.service.CleanupService;
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

@RequestMapping("/api/tdm/cleanup")
@RestController()
@Tag(name = "data-cleanup-controller", description = "Cleanup settings of test data tables: which rows to "
        + "delete, found by an SQL query, a date, or a cleaner class, and the cron schedule to do it on.")
public class DataCleanupController /* implements DataCleanupControllerApi */ {

    private final CleanupService cleanupService;

    @Autowired
    public DataCleanupController(@Nonnull CleanupService cleanupService) {
        this.cleanupService = cleanupService;
    }

    /**
     * Get cleanup configuration for specified dataset / table ID.
     *
     * @param id - cleanup config id
     * @return cleanup configuration object
     */
    @Operation(summary = "Get cleanup settings",
            description = "Returns the cleanup configuration and the environments of the tables that use it. "
                    + "tableName is not set in the response.")
    @PreAuthorize("@entityAccess.checkAccess("
            + "T(org.qubership.atp.tdm.utils.UsersManagementEntities).TEST_DATA.getName(),"
            + "@catalogRepository.findAllByCleanupConfigId(#id).get(0).getProjectId(), 'READ')")
    @AuditAction(auditAction = "Get cleanup configuration by id {{#id}}")
    @GetMapping(path = {"/config/{id}"})
    public ResponseEntity<CleanupSettings> getCleanupConfig(
            @Parameter(description = "Cleanup configuration ID.") @PathVariable("id") UUID id) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .body(cleanupService.getCleanupSettings(id));
    }

    /**
     * Save / update data cleanup settings.
     *
     * @param cleanupConfig CleanupSettings object to be saved
     * @return CleanupSettings object after saving
     * @throws Exception in case errors while settings saving.
     */
    @Operation(summary = "Save cleanup settings",
            description = "Saves the cleanup configuration for the table in tableName and for the tables of the "
                    + "same system in the environments listed in environmentsList, and schedules it. "
                    + "queryTimeout must be between 1 and EXTERNAL_QUERY_MAX_TIMEOUT seconds (3600 by default); a "
                    + "value out of range returns HTTP 400.")
    @PreAuthorize("@entityAccess.checkAccess("
            + "T(org.qubership.atp.tdm.utils.UsersManagementEntities).TEST_DATA.getName(),"
            + "@catalogRepository.findByTableName(#cleanupConfig.tableName).getProjectId(), 'CREATE')")
    @AuditAction(auditAction = "Save / update data cleanup settings. "
            + "Table {{#cleanupConfig.tableName}}")
    @PostMapping(value = "/config")
    public CleanupSettings saveCleanupConfig(@RequestBody CleanupSettings cleanupConfig) throws Exception {
        return cleanupService.saveCleanupConfig(cleanupConfig);
    }

    /**
     * Force run data cleanup.
     *
     * @param cleanupConfig CleanupSettings object to run cleanup
     * @return List of CleanupResults produced by cleanup run
     * @throws Exception in case errors while cleanup running.
     */
    @Operation(summary = "Run a cleanup now",
            description = "Runs the cleanup configuration from the request body, without saving it, on the table "
                    + "in tableName and on the tables of the same system in the environments listed in "
                    + "environmentsList. Returns the number of checked and removed rows per table.")
    @PreAuthorize("@entityAccess.checkAccess("
            + "T(org.qubership.atp.tdm.utils.UsersManagementEntities).TEST_DATA.getName(),"
            + "@catalogRepository.findByTableName(#cleanupConfig.tableName).getProjectId(), 'CREATE')")
    @AuditAction(auditAction = "Force run data cleanup. Table {{#cleanupConfig.tableName}}")
    @PostMapping(value = "/run")
    public List<CleanupResults> runDataCleanup(@RequestBody CleanupSettings cleanupConfig) throws Exception {
        return cleanupService.runCleanup(cleanupConfig);
    }

    /**
     * Get next run's date / time details.
     *
     * @param cronExpression cron expression to calculate next run based on
     * @return ResponseEntity of String message that contains details
     * @throws ParseException Thrown in case if invalid cron expression was provided.
     */
    @Operation(operationId = "getNextCleanupRun", summary = "Get the next run time of a schedule",
            description = "Returns the next time the Quartz cron expression fires after now, as a JSON string "
                    + "in \"EEE MMM dd HH:mm:ss zzz yyyy\" format.")
    @AuditAction(auditAction = "Get next run's date. cron {{#cronExpression}}")
    @GetMapping(value = "/next/run")
    public ResponseEntity<String> getNextScheduledRun(
            @Parameter(description = "Quartz cron expression, starting with the seconds field.")
            @RequestParam("cronExpression") String cronExpression)
            throws ParseException {
        return ResponseEntity.ok(new Gson().toJson(cleanupService.getNextScheduledRun(cronExpression)));
    }

    @Operation(summary = "Fill in the cleanup type of existing configurations",
            description = "Sets the type of every cleanup configuration from the field it uses: DATE when "
                    + "searchDate is set, otherwise SQL when searchSql is set, otherwise CLASS when searchClass is "
                    + "set. A migration for configurations saved before the type existed.")
    @AuditAction(auditAction = "Old update.")
    @GetMapping(path = "/fill/cleanup/type")
    public void fillCleanupTypeColumn() {
        cleanupService.fillCleanupTypeColumn();
    }
}

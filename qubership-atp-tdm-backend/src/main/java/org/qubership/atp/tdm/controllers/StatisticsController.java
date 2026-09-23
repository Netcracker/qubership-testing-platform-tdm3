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
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.qubership.atp.integration.configuration.configuration.AuditAction;
import org.qubership.atp.tdm.model.statistics.AvailableDataStatisticsConfig;
import org.qubership.atp.tdm.model.statistics.ConsumedStatistics;
import org.qubership.atp.tdm.model.statistics.DateStatistics;
import org.qubership.atp.tdm.model.statistics.GeneralStatisticsItem;
import org.qubership.atp.tdm.model.statistics.OutdatedStatistics;
import org.qubership.atp.tdm.model.statistics.TestAvailableDataMonitoring;
import org.qubership.atp.tdm.model.statistics.TestDataTableMonitoring;
import org.qubership.atp.tdm.model.statistics.TestDataTableUsersMonitoring;
import org.qubership.atp.tdm.model.statistics.UsersOccupyStatisticRequest;
import org.qubership.atp.tdm.model.statistics.UsersOccupyStatisticResponse;
import org.qubership.atp.tdm.model.statistics.available.AvailableDataByColumnStats;
import org.qubership.atp.tdm.service.StatisticsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Nonnull;

@RequestMapping("/api/tdm/statistics")
@RestController()
@Tag(name = "statistics-controller", description = "Statistics on test data tables and the email reports "
        + "sent on a schedule. Statistics without systemId group the tables of a project by table title, with the "
        + "per-system values in details.")
public class StatisticsController /* implements StatisticsControllerApi */ {

    private final StatisticsService statisticsService;

    @Autowired
    public StatisticsController(@Nonnull StatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    @Operation(summary = "Get the default report threshold",
            description = "Returns the default threshold of available rows for the statistics report: the value of "
                    + "the test.data.initial.threshold property, 10 by default.")
    @AuditAction(auditAction = "Get threshold for statistics")
    @GetMapping(value = "/threshold")
    public int getThreshold() {
        return statisticsService.getThreshold();
    }

    @Operation(summary = "Get available and occupied row counts",
            description = "Returns, for each table, the number of available rows, occupied rows, rows occupied today, "
                    + "and all rows.")
    @PreAuthorize("@entityAccess.checkAccess("
            + "T(org.qubership.atp.tdm.utils.UsersManagementEntities).STATISTICS.getName(),"
            + "#projectId, 'READ')")
    @AuditAction(auditAction = "Get test data availability. ProjectId {{#projectId}}")
    @GetMapping(value = "/data/available")
    public List<GeneralStatisticsItem> getTestDataAvailability(
            @Parameter(description = "Project ID.") @RequestParam UUID projectId,
            @Parameter(description = "System ID. When omitted, covers all systems of the project.")
            @RequestParam(required = false) UUID systemId) {
        return statisticsService.getTestDataAvailability(projectId, systemId);
    }

    @Operation(summary = "Get the number of occupied rows per period",
            description = "Returns, for each table, how many rows were occupied in each part of the period. The "
                    + "values are split into days, weeks, months, or years, depending on the length of the period.")
    @PreAuthorize("@entityAccess.checkAccess("
            + "T(org.qubership.atp.tdm.utils.UsersManagementEntities).STATISTICS.getName(),"
            + "#projectId, 'READ')")
    @AuditAction(auditAction = "Get test data consumption. ProjectId {{#projectId}}")
    @GetMapping(value = "/data/occupied")
    public ConsumedStatistics getTestDataConsumption(
            @Parameter(description = "Project ID.") @RequestParam UUID projectId,
            @Parameter(description = "System ID. When omitted, covers all systems of the project.")
            @RequestParam(required = false) UUID systemId,
            @Parameter(description = "First day of the period, as yyyy-MM-dd.") @RequestParam String dateFrom,
            @Parameter(description = "Last day of the period, as yyyy-MM-dd.") @RequestParam String dateTo) {
        return statisticsService.getTestDataConsumption(projectId, systemId,
                LocalDate.parse(dateFrom), LocalDate.parse(dateTo));
    }

    @Operation(summary = "Get created, consumed, and outdated row counts per period",
            description = "Returns, for each table, how many rows were created, consumed, and outdated in each part "
                    + "of the period. Rows occupied on or after dateFrom plus expirationDate days are counted as "
                    + "outdated. The values are split into days, weeks, months, or years, depending on the length of "
                    + "the period.")
    @PreAuthorize("@entityAccess.checkAccess("
            + "T(org.qubership.atp.tdm.utils.UsersManagementEntities).STATISTICS.getName(),"
            + "#projectId, 'READ')")
    @AuditAction(auditAction = "Get test data consumption with outdated. ProjectId {{#projectId}}")
    @GetMapping(value = "/data/outdated")
    public OutdatedStatistics getTestDataConsumptionWhitOutdated(
            @Parameter(description = "Project ID.") @RequestParam UUID projectId,
            @Parameter(description = "System ID. When omitted, covers all systems of the project.")
            @RequestParam(required = false) UUID systemId,
            @Parameter(description = "First day of the period, as yyyy-MM-dd.") @RequestParam String dateFrom,
            @Parameter(description = "Last day of the period, as yyyy-MM-dd.") @RequestParam String dateTo,
            @Parameter(description = "Number of days after dateFrom from which occupied rows count as outdated.")
            @RequestParam String expirationDate) {
        return statisticsService.getTestDataConsumptionWhitOutdated(projectId, systemId,
                LocalDate.parse(dateFrom), LocalDate.parse(dateTo), Integer.valueOf(expirationDate));
    }

    @Operation(summary = "Get the number of created rows per period",
            description = "Returns, for each table, how many rows were created in each part of the period. The values "
                    + "are split into days, weeks, months, or years, depending on the length of the period.")
    @PreAuthorize("@entityAccess.checkAccess("
            + "T(org.qubership.atp.tdm.utils.UsersManagementEntities).STATISTICS.getName(),"
            + "#projectId, 'READ')")
    @AuditAction(auditAction = "Get test data by created when date. ProjectId {{#projectId}}")
    @GetMapping(value = "/data/created/when")
    public DateStatistics getTestDataCreatedWhen(
            @Parameter(description = "Project ID.") @RequestParam UUID projectId,
            @Parameter(description = "System ID. When omitted, covers all systems of the project.")
            @RequestParam(required = false) UUID systemId,
            @Parameter(description = "First day of the period, as yyyy-MM-dd.") @RequestParam String dateFrom,
            @Parameter(description = "Last day of the period, as yyyy-MM-dd.") @RequestParam String dateTo) {
        return statisticsService.getTestDataCreatedWhen(projectId, systemId,
                LocalDate.parse(dateFrom), LocalDate.parse(dateTo));
    }

    @Operation(summary = "Get the statistics report schedule",
            description = "Returns the schedule, recipients, and threshold of the project statistics report. The "
                    + "report splits the tables into those with at least threshold available rows and those with "
                    + "fewer. Returns a disabled schedule with empty fields when the project has none.")
    @PreAuthorize("@entityAccess.checkAccess("
            + "T(org.qubership.atp.tdm.utils.UsersManagementEntities).STATISTICS.getName(),"
            + "#projectId, 'READ')")
    @AuditAction(auditAction = "Get statistics monitoring schedule. ProjectId {{#projectId}}")
    @GetMapping(value = "/schedule")
    public TestDataTableMonitoring getMonitoringSchedule(
            @Parameter(description = "Project ID.") @RequestParam UUID projectId) {
        return statisticsService.getMonitoringSchedule(projectId);
    }

    @Operation(summary = "Save the statistics report schedule",
            description = "Saves the schedule of the project statistics report and reschedules it. cronExpression is "
                    + "a Quartz cron expression.")
    @PreAuthorize("@entityAccess.checkAccess("
            + "T(org.qubership.atp.tdm.utils.UsersManagementEntities).STATISTICS.getName(),"
            + "#monitoringItem.getProjectId(), 'CREATE')")
    @AuditAction(auditAction = "Setup next run in schedule for statistics monitoring. "
            + "ProjectId {{#monitoringItem.projectId}}")
    @PostMapping(value = "/schedule")
    public void setupScheduledRun(@RequestBody TestDataTableMonitoring monitoringItem) {
        statisticsService.saveMonitoringSchedule(monitoringItem);
    }

    @Operation(summary = "Delete the statistics report schedule",
            description = "Deletes the schedule of the project in projectId and stops the report. Only projectId is "
                    + "read from the request body.")
    @PreAuthorize("@entityAccess.checkAccess("
            + "T(org.qubership.atp.tdm.utils.UsersManagementEntities).STATISTICS.getName(),"
            + "#monitoringItem.getProjectId(), 'UPDATE')")
    @AuditAction(auditAction = "Delete schedule for statistics monitoring."
            + "ProjectId {{#monitoringItem.projectId}}")
    @PutMapping(value = "/delete/schedule")
    public void deleteScheduledRun(@RequestBody TestDataTableMonitoring monitoringItem) {
        statisticsService.deleteMonitoringSchedule(monitoringItem);
    }

    /**
     * Get getting TestDataTableUsersMonitoring.
     *
     * @param projectId uuid project
     * @return TestDataTableUsersMonitoring that contains the details
     */

    @Operation(summary = "Get the users report schedule",
            description = "Returns the schedule and settings of the report on rows occupied by users. Returns a "
                    + "disabled schedule with empty fields when the project has none.")
    @PreAuthorize("@entityAccess.checkAccess("
            + "T(org.qubership.atp.tdm.utils.UsersManagementEntities).STATISTICS.getName(),"
            + "#projectId, 'READ')")
    @AuditAction(auditAction = "Get statistics users monitoring schedule. ProjectId {{#projectId}}")
    @GetMapping(value = "/schedule/users")
    public TestDataTableUsersMonitoring getUsersMonitoringSchedule(
            @Parameter(description = "Project ID.") @RequestParam UUID projectId) {
        return statisticsService.getUsersMonitoringSchedule(projectId);
    }

    /**
     * Post save TestDataTableUsersMonitoring.
     *
     * @param monitoringItem TestDataTableUsersMonitoring
     */

    @Operation(summary = "Save the users report schedule")
    @PreAuthorize("@entityAccess.checkAccess("
            + "T(org.qubership.atp.tdm.utils.UsersManagementEntities).STATISTICS.getName(),"
            + "#monitoringItem.getProjectId(), 'CREATE')")
    @AuditAction(auditAction = "Setup next run in schedule for statistics users monitoring."
            + "ProjectId {{#monitoringItem.projectId}}")
    @PostMapping(value = "/schedule/users")
    public void setupUsersScheduledRun(@RequestBody TestDataTableUsersMonitoring monitoringItem) {
        statisticsService.saveUsersMonitoringSchedule(monitoringItem);
    }

    /**
     * Put delete TestDataTableUsersMonitoring.
     *
     * @param monitoringItem TestDataTableUsersMonitoring
     */

    @Operation(summary = "Delete the users report schedule")
    @PreAuthorize("@entityAccess.checkAccess("
            + "T(org.qubership.atp.tdm.utils.UsersManagementEntities).STATISTICS.getName(),"
            + "#monitoringItem.getProjectId(), 'UPDATE')")
    @AuditAction(auditAction = "Delete schedule for statistics users monitoring."
            + "ProjectId {{#monitoringItem.projectId}}")
    @PutMapping(value = "/delete/schedule/users")
    public void deleteUsersScheduledRun(@RequestBody TestDataTableUsersMonitoring monitoringItem) {
        statisticsService.deleteUsersMonitoringSchedule(monitoringItem);
    }

    /**
     * Get next run's date / time details.
     *
     * @param cronExpression cron expression to calculate next run based on
     * @return nextRunHashMap that contains the details
     * @throws ParseException Thrown in case if invalid cron expression was provided
     */

    @Operation(operationId = "getNextStatisticsRun", summary = "Get the next run time of a schedule",
            description = "Returns the next time the Quartz cron expression fires after now, in the nextRun field, in "
                    + "\"EEE MMM dd HH:mm:ss zzz yyyy\" format.")
    @AuditAction(auditAction = "Get next run's date. cron {{#cronExpression}}")
    @GetMapping(value = "/next/run")
    public Map<String, String> getNextScheduledRun(
            @Parameter(description = "Quartz cron expression, starting with the seconds field.")
            @RequestParam String cronExpression) throws ParseException {
        Map<String, String> nextRunHashMap = new HashMap<>();
        nextRunHashMap.put("nextRun", statisticsService.getNextScheduledRun(cronExpression));
        return nextRunHashMap;
    }

    /**
     * Method fixes issue with statistics functional (ATPII-10354).
     *
     * @return list of tables to which a new column was added
     */

    @Operation(summary = "Add the OCCUPIED_DATE column to all tables",
            description = "Adds the OCCUPIED_DATE column to every test data table that lacks it, and returns the "
                    + "table names. A migration for tables created before the column existed.")
    @AuditAction(auditAction = "Old update.")
    @GetMapping(value = "/fix/occupied/date/column")
    public List<String> alterOccupiedDateColumn() {
        return statisticsService.alterOccupiedDateColumn();
    }

    /**
     * Endpoint return list of users with occupied data.
     *
     * @param request Request data for users statistics.
     * @return List of users with data about occupation.
     */
    @Operation(summary = "Get rows occupied by users",
            description = "Returns a page of occupation records by user for the project and the period in the request "
                    + "body. dateTo must be later than dateFrom; otherwise the request returns HTTP 400.")
    @PreAuthorize("@entityAccess.checkAccess("
            + "T(org.qubership.atp.tdm.utils.UsersManagementEntities).STATISTICS.getName(),"
            + "#request.getProjectId(), 'CREATE')")
    @AuditAction(auditAction = "Get statistics by users. ProjectId {{#request.projectId}}")
    @PostMapping(value = "/data/occupied/users")
    public UsersOccupyStatisticResponse getStatisticsByUsers(
            @RequestBody UsersOccupyStatisticRequest request) {
        return statisticsService.getOccupiedDataByUsers(request);
    }

    @Operation(summary = "Get the available-data-by-column settings",
            description = "Returns the settings of the available-data-by-column statistics for a system in an "
                    + "environment, with the column names of the system tables to choose from.")
    @GetMapping(value = "/available/column/configuration")
    @PreAuthorize("@entityAccess.checkAccess("
            + "T(org.qubership.atp.tdm.utils.UsersManagementEntities).STATISTICS.getName(),"
            + "@environmentsServiceImpl.getLazyEnvironment(#environmentId).getProjectId(), 'READ')")
    public AvailableDataStatisticsConfig getAvailableDataStatsConfig(
            @Parameter(description = "System ID.") @RequestParam UUID systemId,
            @Parameter(description = "Environment ID.") @RequestParam UUID environmentId) {
        return statisticsService.getAvailableStatsConfig(systemId, environmentId);
    }

    @Operation(summary = "Save the available-data-by-column settings")
    @PostMapping(value = "/available/column/configuration")
    @PreAuthorize("@entityAccess.checkAccess("
            + "T(org.qubership.atp.tdm.utils.UsersManagementEntities).STATISTICS.getName(),"
            + "@environmentsServiceImpl.getLazyEnvironment(#statsConfig.getEnvironmentId()).getProjectId(), "
            + "'CREATE')")
    public void saveAvailableDataStatsConfig(
            @RequestBody AvailableDataStatisticsConfig statsConfig) {
        statisticsService.saveAvailableStatsConfig(statsConfig);
    }

    @Operation(summary = "Get available rows by column value",
            description = "Returns, for each table of the system, the number of available rows per value of the "
                    + "configured columns. Fails when the settings of this statistics are not saved for the system "
                    + "and environment.")
    @GetMapping(value = "/data/occupied/available")
    @PreAuthorize("@entityAccess.checkAccess("
            + "T(org.qubership.atp.tdm.utils.UsersManagementEntities).STATISTICS.getName(),"
            + "@environmentsServiceImpl.getLazyEnvironment(#environmentId).getProjectId(), 'READ')")
    public AvailableDataByColumnStats getAvailableData(
            @Parameter(description = "System ID.") @RequestParam UUID systemId,
            @Parameter(description = "Environment ID.") @RequestParam UUID environmentId) {
        return statisticsService.getAvailableDataInColumn(systemId, environmentId);
    }

    @Operation(summary = "Get the available-data report schedule")
    @GetMapping(value = "/schedule/available")
    @PreAuthorize("@entityAccess.checkAccess("
            + "T(org.qubership.atp.tdm.utils.UsersManagementEntities).STATISTICS.getName(),"
            + "@environmentsServiceImpl.getLazyEnvironment(#environmentId).getProjectId(), 'READ')")
    public TestAvailableDataMonitoring getAvailableDataMonitoringConfig(
            @Parameter(description = "System ID.") @RequestParam UUID systemId,
            @Parameter(description = "Environment ID.") @RequestParam UUID environmentId) {
        return statisticsService.getAvailableDataMonitoringConfig(systemId, environmentId);
    }

    @Operation(summary = "Save the available-data report schedule")
    @PostMapping(value = "/schedule/available")
    @PreAuthorize("@entityAccess.checkAccess("
            + "T(org.qubership.atp.tdm.utils.UsersManagementEntities).STATISTICS.getName(),"
            + "@environmentsServiceImpl.getLazyEnvironment(#monitoringConfig.getEnvironmentId()).getProjectId(),"
            + " 'UPDATE')")
    public void saveAvailableDataMonitoringConfig(
            @RequestBody TestAvailableDataMonitoring monitoringConfig) throws Exception {
        statisticsService.saveAvailableDataMonitoringConfig(monitoringConfig);
    }

    @Operation(summary = "Delete the available-data report schedule")
    @DeleteMapping(value = "/schedule/available")
    @PreAuthorize("@entityAccess.checkAccess("
            + "T(org.qubership.atp.tdm.utils.UsersManagementEntities).STATISTICS.getName(),"
            + "@environmentsServiceImpl.getLazyEnvironment(#environmentId).getProjectId(), 'UPDATE')")
    public void deleteAvailableDataMonitoringConfig(
            @Parameter(description = "System ID.") @RequestParam UUID systemId,
            @Parameter(description = "Environment ID.") @RequestParam UUID environmentId) {
        statisticsService.deleteAvailableDataMonitoringConfig(systemId, environmentId);
    }
}

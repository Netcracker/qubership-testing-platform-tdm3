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
import jakarta.annotation.Nonnull;

@RequestMapping("/api/tdm/statistics")
@RestController()
public class StatisticsController /* implements StatisticsControllerApi */ {

    private final StatisticsService statisticsService;

    @Autowired
    public StatisticsController(@Nonnull StatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    @Operation(description = "Get threshold for statistics.")
    @AuditAction(auditAction = "Get threshold for statistics")
    @GetMapping(value = "/threshold")
    public int getThreshold() {
        return statisticsService.getThreshold();
    }

    @Operation(description = "Get test data availability.")
    @PreAuthorize("@entityAccess.checkAccess("
            + "T(org.qubership.atp.tdm.utils.UsersManagementEntities).STATISTICS.getName(),"
            + "#projectId, 'READ')")
    @AuditAction(auditAction = "Get test data availability. ProjectId {{#projectId}}")
    @GetMapping(value = "/data/available")
    public List<GeneralStatisticsItem> getTestDataAvailability(@RequestParam UUID projectId,
                                                               @RequestParam(required = false) UUID systemId) {
        return statisticsService.getTestDataAvailability(projectId, systemId);
    }

    @Operation(description = "Get test data consumption.")
    @PreAuthorize("@entityAccess.checkAccess("
            + "T(org.qubership.atp.tdm.utils.UsersManagementEntities).STATISTICS.getName(),"
            + "#projectId, 'READ')")
    @AuditAction(auditAction = "Get test data consumption. ProjectId {{#projectId}}")
    @GetMapping(value = "/data/occupied")
    public ConsumedStatistics getTestDataConsumption(@RequestParam UUID projectId,
                                                     @RequestParam(required = false) UUID systemId,
                                                     @RequestParam String dateFrom,
                                                     @RequestParam String dateTo) {
        return statisticsService.getTestDataConsumption(projectId, systemId,
                LocalDate.parse(dateFrom), LocalDate.parse(dateTo));
    }

    @Operation(description = "Get test data consumption with outdated.")
    @PreAuthorize("@entityAccess.checkAccess("
            + "T(org.qubership.atp.tdm.utils.UsersManagementEntities).STATISTICS.getName(),"
            + "#projectId, 'READ')")
    @AuditAction(auditAction = "Get test data consumption with outdated. ProjectId {{#projectId}}")
    @GetMapping(value = "/data/outdated")
    public OutdatedStatistics getTestDataConsumptionWhitOutdated(@RequestParam UUID projectId,
                                                                 @RequestParam(required = false) UUID systemId,
                                                                 @RequestParam String dateFrom,
                                                                 @RequestParam String dateTo,
                                                                 @RequestParam String expirationDate) {
        return statisticsService.getTestDataConsumptionWhitOutdated(projectId, systemId,
                LocalDate.parse(dateFrom), LocalDate.parse(dateTo), Integer.valueOf(expirationDate));
    }

    @Operation(description = "Get test data by created when date.")
    @PreAuthorize("@entityAccess.checkAccess("
            + "T(org.qubership.atp.tdm.utils.UsersManagementEntities).STATISTICS.getName(),"
            + "#projectId, 'READ')")
    @AuditAction(auditAction = "Get test data by created when date. ProjectId {{#projectId}}")
    @GetMapping(value = "/data/created/when")
    public DateStatistics getTestDataCreatedWhen(@RequestParam UUID projectId,
                                                 @RequestParam(required = false) UUID systemId,
                                                 @RequestParam String dateFrom,
                                                 @RequestParam String dateTo) {
        return statisticsService.getTestDataCreatedWhen(projectId, systemId,
                LocalDate.parse(dateFrom), LocalDate.parse(dateTo));
    }

    @Operation(description = "Get statistics monitoring schedule.")
    @PreAuthorize("@entityAccess.checkAccess("
            + "T(org.qubership.atp.tdm.utils.UsersManagementEntities).STATISTICS.getName(),"
            + "#projectId, 'READ')")
    @AuditAction(auditAction = "Get statistics monitoring schedule. ProjectId {{#projectId}}")
    @GetMapping(value = "/schedule")
    public TestDataTableMonitoring getMonitoringSchedule(@RequestParam UUID projectId) {
        return statisticsService.getMonitoringSchedule(projectId);
    }

    @Operation(description = "Setup next run in schedule for statistics monitoring.")
    @PreAuthorize("@entityAccess.checkAccess("
            + "T(org.qubership.atp.tdm.utils.UsersManagementEntities).STATISTICS.getName(),"
            + "#monitoringItem.getProjectId(), 'CREATE')")
    @AuditAction(auditAction = "Setup next run in schedule for statistics monitoring. "
            + "ProjectId {{#monitoringItem.projectId}}")
    @PostMapping(value = "/schedule")
    public void setupScheduledRun(@RequestBody TestDataTableMonitoring monitoringItem) {
        statisticsService.saveMonitoringSchedule(monitoringItem);
    }

    @Operation(description = "Delete schedule for statistics monitoring.")
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

    @Operation(description = "Get statistics users monitoring schedule.")
    @PreAuthorize("@entityAccess.checkAccess("
            + "T(org.qubership.atp.tdm.utils.UsersManagementEntities).STATISTICS.getName(),"
            + "#projectId, 'READ')")
    @AuditAction(auditAction = "Get statistics users monitoring schedule. ProjectId {{#projectId}}")
    @GetMapping(value = "/schedule/users")
    public TestDataTableUsersMonitoring getUsersMonitoringSchedule(@RequestParam UUID projectId) {
        return statisticsService.getUsersMonitoringSchedule(projectId);
    }

    /**
     * Post save TestDataTableUsersMonitoring.
     *
     * @param monitoringItem TestDataTableUsersMonitoring
     */

    @Operation(description = "Setup next run in schedule for statistics users monitoring.")
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

    @Operation(description = "Delete schedule for statistics users monitoring.")
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

    @Operation(description = "Get next run's date / time details.")
    @AuditAction(auditAction = "Get next run's date. cron {{#cronExpression}}")
    @GetMapping(value = "/next/run")
    public Map<String, String> getNextScheduledRun(@RequestParam String cronExpression) throws ParseException {
        Map<String, String> nextRunHashMap = new HashMap<>();
        nextRunHashMap.put("nextRun", statisticsService.getNextScheduledRun(cronExpression));
        return nextRunHashMap;
    }

    /**
     * Method fixes issue with statistics functional (ATPII-10354).
     *
     * @return list of tables to which a new column was added
     */

    @Operation(description = "Old update.")
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
    @Operation(description = "Get statistics by users.")
    @PreAuthorize("@entityAccess.checkAccess("
            + "T(org.qubership.atp.tdm.utils.UsersManagementEntities).STATISTICS.getName(),"
            + "#request.getProjectId(), 'CREATE')")
    @AuditAction(auditAction = "Get statistics by users. ProjectId {{#request.projectId}}")
    @PostMapping(value = "/data/occupied/users")
    public UsersOccupyStatisticResponse getStatisticsByUsers(
            @RequestBody UsersOccupyStatisticRequest request) {
        return statisticsService.getOccupiedDataByUsers(request);
    }

    @Operation(description = "Get available data by column configuration.")
    @GetMapping(value = "/available/column/configuration")
    @PreAuthorize("@entityAccess.checkAccess("
            + "T(org.qubership.atp.tdm.utils.UsersManagementEntities).STATISTICS.getName(),"
            + "@environmentsServiceImpl.getLazyEnvironment(#environmentId).getProjectId(), 'READ')")
    public AvailableDataStatisticsConfig getAvailableDataStatsConfig(
            @RequestParam UUID systemId, @RequestParam UUID environmentId) {
        return statisticsService.getAvailableStatsConfig(systemId, environmentId);
    }

    @Operation(description = "Set available data by column configuration.")
    @PostMapping(value = "/available/column/configuration")
    @PreAuthorize("@entityAccess.checkAccess("
            + "T(org.qubership.atp.tdm.utils.UsersManagementEntities).STATISTICS.getName(),"
            + "@environmentsServiceImpl.getLazyEnvironment(#statsConfig.getEnvironmentId()).getProjectId(), "
            + "'CREATE')")
    public void saveAvailableDataStatsConfig(
            @RequestBody AvailableDataStatisticsConfig statsConfig) {
        statisticsService.saveAvailableStatsConfig(statsConfig);
    }

    @Operation(description = "Get available data by column")
    @GetMapping(value = "/data/occupied/available")
    @PreAuthorize("@entityAccess.checkAccess("
            + "T(org.qubership.atp.tdm.utils.UsersManagementEntities).STATISTICS.getName(),"
            + "@environmentsServiceImpl.getLazyEnvironment(#environmentId).getProjectId(), 'READ')")
    public AvailableDataByColumnStats getAvailableData(@RequestParam UUID systemId, @RequestParam UUID environmentId) {
        return statisticsService.getAvailableDataInColumn(systemId, environmentId);
    }

    @Operation(description = "Get available data monitoring configuration.")
    @GetMapping(value = "/schedule/available")
    @PreAuthorize("@entityAccess.checkAccess("
            + "T(org.qubership.atp.tdm.utils.UsersManagementEntities).STATISTICS.getName(),"
            + "@environmentsServiceImpl.getLazyEnvironment(#environmentId).getProjectId(), 'READ')")
    public TestAvailableDataMonitoring getAvailableDataMonitoringConfig(
            @RequestParam UUID systemId, @RequestParam UUID environmentId) {
        return statisticsService.getAvailableDataMonitoringConfig(systemId, environmentId);
    }

    @Operation(description = "Save available data monitoring configuration.")
    @PostMapping(value = "/schedule/available")
    @PreAuthorize("@entityAccess.checkAccess("
            + "T(org.qubership.atp.tdm.utils.UsersManagementEntities).STATISTICS.getName(),"
            + "@environmentsServiceImpl.getLazyEnvironment(#monitoringConfig.getEnvironmentId()).getProjectId(),"
            + " 'UPDATE')")
    public void saveAvailableDataMonitoringConfig(
            @RequestBody TestAvailableDataMonitoring monitoringConfig) throws Exception {
        statisticsService.saveAvailableDataMonitoringConfig(monitoringConfig);
    }

    @Operation(description = "Delete available data monitoring configuration.")
    @DeleteMapping(value = "/schedule/available")
    @PreAuthorize("@entityAccess.checkAccess("
            + "T(org.qubership.atp.tdm.utils.UsersManagementEntities).STATISTICS.getName(),"
            + "@environmentsServiceImpl.getLazyEnvironment(#environmentId).getProjectId(), 'UPDATE')")
    public void deleteAvailableDataMonitoringConfig(@RequestParam UUID systemId, @RequestParam UUID environmentId) {
        statisticsService.deleteAvailableDataMonitoringConfig(systemId, environmentId);
    }
}

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

package org.qubership.atp.tdm.websocket.bulkaction.cleanup;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import org.qubership.atp.common.lock.LockManager;
import org.qubership.atp.integration.configuration.mdc.MdcUtils;
import org.qubership.atp.tdm.env.configurator.model.LazyEnvironment;
import org.qubership.atp.tdm.env.configurator.service.EnvironmentsService;
import org.qubership.atp.tdm.exceptions.internal.TdmSearchCleanupConfigException;
import org.qubership.atp.tdm.mdc.TdmMdcHelper;
import org.qubership.atp.tdm.model.TestDataTableCatalog;
import org.qubership.atp.tdm.model.bulkaction.BulkActionConfig;
import org.qubership.atp.tdm.model.bulkaction.BulkActionResult;
import org.qubership.atp.tdm.model.cleanup.CleanupResults;
import org.qubership.atp.tdm.model.cleanup.TestDataCleanupConfig;
import org.qubership.atp.tdm.model.mail.bulkaction.BulkCleanupMailSender;
import org.qubership.atp.tdm.repo.CatalogRepository;
import org.qubership.atp.tdm.repo.CleanupConfigRepository;
import org.qubership.atp.tdm.service.CleanupService;
import org.qubership.atp.tdm.utils.CurrentTime;
import org.qubership.atp.tdm.websocket.bulkaction.BulkActionsHandler;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.socket.WebSocketSession;

import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BulkDataCleanupHandler extends BulkActionsHandler {

    private final CleanupService cleanupService;
    private final CleanupConfigRepository cleanupConfigRepository;

    /**
     * Constructor with parameters.
     */
    public BulkDataCleanupHandler(@Qualifier("websocket") ExecutorService executorService,
                                  @Nonnull CatalogRepository catalogRepository,
                                  @Nonnull EnvironmentsService environmentsService,
                                  @Nonnull CleanupService cleanupService,
                                  @Nonnull CleanupConfigRepository cleanupConfigRepository,
                                  @Nonnull BulkCleanupMailSender mailSender,
                                  @Nonnull CurrentTime currentTime,
                                  @Nonnull LockManager lockManager,
                                  TdmMdcHelper helper) {
        super(executorService, catalogRepository, environmentsService, mailSender, currentTime, lockManager, helper);
        this.cleanupConfigRepository = cleanupConfigRepository;
        this.cleanupService = cleanupService;
    }

    @Override
    public List<Future<BulkActionResult>> runBulkAction(@Nonnull WebSocketSession session,
                                                        @Nonnull ExecutorService executor,
                                                        @Nonnull List<LazyEnvironment> lazyEnvironments,
                                                        @Nonnull BulkActionConfig config, long processId) {
        log.info("Bulk cleanup has been initiated, id: {}, config: {}", processId, config);
        List<TestDataTableCatalog> catalogList = catalogRepository
                .findAllByProjectIdAndSystemIdAndCleanupConfigIdIsNotNull(config.getProjectId(), config.getSystemId())
                .stream()
                .filter(c -> cleanupConfigRepository.findById(c.getCleanupConfigId())
                        .orElseThrow(() ->
                                new TdmSearchCleanupConfigException(c.getCleanupConfigId().toString()))
                        .isEnabled()).collect(Collectors.toList());
        Map<UUID, Optional<TestDataCleanupConfig>> testDataCleanUpConfigs = catalogList.stream()
                .collect(Collectors.toMap(TestDataTableCatalog::getCleanupConfigId,
                        c -> cleanupConfigRepository.findById(c.getCleanupConfigId()), (a, b) -> b));
        log.trace("Found: {} tables with cleanup config.", catalogList.size());

        List<Future<BulkActionResult>> futures = new ArrayList<>();
        Map<String, String> mdcMap = MDC.getCopyOfContextMap();
        catalogList.forEach(tableCatalog -> {
            Future<BulkActionResult> future = executor
                    .submit(() -> {
                        MdcUtils.setContextMap(mdcMap);
                        String envName = getEnvName(lazyEnvironments, tableCatalog.getEnvironmentId());
                        try {
                            mdcHelper.putConfigFields(tableCatalog);
                            CleanupResults cleanupResults = cleanupService
                                    .runCleanup(tableCatalog.getTableName(),
                                            testDataCleanUpConfigs.get(tableCatalog.getCleanupConfigId())
                                                    .orElseThrow(() -> new TdmSearchCleanupConfigException()));
                            return new BulkActionResult(tableCatalog.getTableTitle(), tableCatalog.getTableName(),
                                    envName, cleanupResults);
                        } catch (Exception e) {
                            return new BulkActionResult(tableCatalog.getTableTitle(), tableCatalog.getTableName(),
                                    envName, e);
                        } finally {
                            mdcHelper.removeConfigFields();
                        }
                    });
            futures.add(future);
        });
        return futures;
    }
}

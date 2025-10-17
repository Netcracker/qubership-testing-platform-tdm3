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

package org.qubership.atp.tdm.websocket.bulkaction.dataload;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import org.qubership.atp.common.lock.LockManager;
import org.qubership.atp.tdm.env.configurator.model.LazyEnvironment;
import org.qubership.atp.tdm.env.configurator.service.EnvironmentsService;
import org.qubership.atp.tdm.mdc.TdmMdcHelper;
import org.qubership.atp.tdm.model.TestDataTableCatalog;
import org.qubership.atp.tdm.model.bulkaction.BulkActionConfig;
import org.qubership.atp.tdm.model.bulkaction.BulkActionResult;
import org.qubership.atp.tdm.model.mail.bulkaction.BulkCleanupMailSender;
import org.qubership.atp.tdm.repo.CatalogRepository;
import org.qubership.atp.tdm.repo.ImportInfoRepository;
import org.qubership.atp.tdm.service.DataRefreshService;
import org.qubership.atp.tdm.utils.CurrentTime;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.socket.WebSocketSession;

import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BulkDataImportHandler extends AbstractBulkDataLoadHandler {

    /**
     * Constructor with parameters.
     */
    public BulkDataImportHandler(@Qualifier("websocket") ExecutorService executorService,
                                 @Nonnull CatalogRepository catalogRepository,
                                 @Nonnull EnvironmentsService environmentsService,
                                 @Nonnull BulkCleanupMailSender mailSender,
                                 @Nonnull DataRefreshService dataRefreshService,
                                 @Nonnull ImportInfoRepository importInfoRepository,
                                 @Nonnull CurrentTime currentTime,
                                 @Nonnull LockManager lockManager,
                                 @Nonnull TdmMdcHelper mdcHelper) {
        super(executorService, catalogRepository, importInfoRepository, environmentsService, mailSender,
                dataRefreshService, currentTime, lockManager, mdcHelper);
    }

    @Override
    public List<Future<BulkActionResult>> runBulkAction(@Nonnull WebSocketSession session,
                                                        @Nonnull ExecutorService executor,
                                                        @Nonnull List<LazyEnvironment> lazyEnvironments,
                                                        @Nonnull BulkActionConfig config, long processId) {
        log.info("Bulk data import has been initiated, id: {}, config: {}", processId, config);
        List<TestDataTableCatalog> catalogList =
                catalogRepository.findAllByProjectIdAndTableTitle(config.getProjectId(), config.getTableTitle());
        List<TestDataTableCatalog> catalogListWithImportInfo = catalogList
                .stream()
                .filter(item -> Objects.nonNull(importInfoRepository.findByTableName(item.getTableName())))
                .filter(item -> lazyEnvironments.stream().anyMatch(e -> item.getEnvironmentId().equals(e.getId())))
                .collect(Collectors.toList());
        log.trace("Found: {} tables with title [{}].", catalogListWithImportInfo.size(), config.getTableTitle());

        return runBulkAction(executor, lazyEnvironments, catalogListWithImportInfo, config.isSaveOccupiedData());
    }
}

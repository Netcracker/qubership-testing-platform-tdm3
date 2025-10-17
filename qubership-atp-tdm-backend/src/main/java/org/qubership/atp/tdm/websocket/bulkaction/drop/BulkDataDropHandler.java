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

package org.qubership.atp.tdm.websocket.bulkaction.drop;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import org.qubership.atp.common.lock.LockManager;
import org.qubership.atp.integration.configuration.mdc.MdcUtils;
import org.qubership.atp.tdm.env.configurator.model.LazyEnvironment;
import org.qubership.atp.tdm.env.configurator.service.EnvironmentsService;
import org.qubership.atp.tdm.mdc.TdmMdcHelper;
import org.qubership.atp.tdm.model.DropResults;
import org.qubership.atp.tdm.model.TestDataTableCatalog;
import org.qubership.atp.tdm.model.bulkaction.BulkActionConfig;
import org.qubership.atp.tdm.model.bulkaction.BulkActionResult;
import org.qubership.atp.tdm.model.mail.bulkaction.BulkDropMailSender;
import org.qubership.atp.tdm.repo.CatalogRepository;
import org.qubership.atp.tdm.service.TestDataService;
import org.qubership.atp.tdm.utils.CurrentTime;
import org.qubership.atp.tdm.websocket.bulkaction.BulkActionsHandler;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.socket.WebSocketSession;

import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BulkDataDropHandler extends BulkActionsHandler {

    private final TestDataService testDataService;

    public BulkDataDropHandler(@Qualifier("websocket") ExecutorService executorService,
                               @Nonnull CatalogRepository catalogRepository,
                               @Nonnull EnvironmentsService environmentsService,
                               @Nonnull TestDataService testDataService,
                               @Nonnull BulkDropMailSender mailSender,
                               @Nonnull CurrentTime currentTime,
                               @Nonnull LockManager lockManager,
                               @Nonnull TdmMdcHelper mdcHelper) {
        super(executorService, catalogRepository, environmentsService, mailSender, currentTime, lockManager, mdcHelper);
        this.testDataService = testDataService;
    }

    @Override
    public List<Future<BulkActionResult>> runBulkAction(@Nonnull WebSocketSession session,
                                                        @Nonnull ExecutorService executor,
                                                        @Nonnull List<LazyEnvironment> lazyEnvironments,
                                                        @Nonnull BulkActionConfig config, long processId) {
        log.info("Bulk drop has been initiated, id: {}, config: {}", processId, config);
        List<TestDataTableCatalog> catalogList =
                catalogRepository.findAllByProjectIdAndTableTitle(config.getProjectId(),
                        config.getTableTitle());
        log.trace("Found: {} tables.", catalogList.size());
        List<Future<BulkActionResult>> futures = new ArrayList<>();
        Map<String, String> mdcMap = MDC.getCopyOfContextMap();
        catalogList.forEach(tableCatalog -> {
            Future<BulkActionResult> future = executor
                    .submit(() -> {
                        MdcUtils.setContextMap(mdcMap);
                        String envName = getEnvName(lazyEnvironments, tableCatalog.getEnvironmentId());
                        try {
                            mdcHelper.putConfigFields(tableCatalog);
                            DropResults dropResults = testDataService.deleteTestData(tableCatalog.getTableName());
                            return new BulkActionResult(tableCatalog.getTableTitle(),
                                    tableCatalog.getTableName(), envName, dropResults);
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


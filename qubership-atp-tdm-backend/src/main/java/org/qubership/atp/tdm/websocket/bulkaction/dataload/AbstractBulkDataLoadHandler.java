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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import org.qubership.atp.common.lock.LockManager;
import org.qubership.atp.integration.configuration.mdc.MdcUtils;
import org.qubership.atp.tdm.env.configurator.model.LazyEnvironment;
import org.qubership.atp.tdm.env.configurator.service.EnvironmentsService;
import org.qubership.atp.tdm.exceptions.websocket.TdmGetEnvironmentNameException;
import org.qubership.atp.tdm.mdc.TdmMdcHelper;
import org.qubership.atp.tdm.model.TestDataTableCatalog;
import org.qubership.atp.tdm.model.bulkaction.BulkActionResult;
import org.qubership.atp.tdm.model.mail.bulkaction.AbstractBulkActionMailSender;
import org.qubership.atp.tdm.model.refresh.RefreshResults;
import org.qubership.atp.tdm.repo.CatalogRepository;
import org.qubership.atp.tdm.repo.ImportInfoRepository;
import org.qubership.atp.tdm.service.DataRefreshService;
import org.qubership.atp.tdm.utils.CurrentTime;
import org.qubership.atp.tdm.websocket.bulkaction.BulkActionsHandler;
import org.slf4j.MDC;

import jakarta.annotation.Nonnull;

public abstract class AbstractBulkDataLoadHandler extends BulkActionsHandler {

    protected final ImportInfoRepository importInfoRepository;
    private final DataRefreshService dataRefreshService;

    /**
     * Constructor with parameters.
     */
    AbstractBulkDataLoadHandler(ExecutorService executorService,
                                @Nonnull CatalogRepository catalogRepository,
                                @Nonnull ImportInfoRepository importInfoRepository,
                                @Nonnull EnvironmentsService environmentsService,
                                @Nonnull AbstractBulkActionMailSender mailSender,
                                @Nonnull DataRefreshService dataRefreshService,
                                @Nonnull CurrentTime currentTime,
                                @Nonnull LockManager lockManager,
                                TdmMdcHelper helper) {
        super(executorService, catalogRepository, environmentsService, mailSender, currentTime, lockManager, helper);
        this.dataRefreshService = dataRefreshService;
        this.importInfoRepository = importInfoRepository;
    }

    /**
     * Run bulk action.
     *
     * @param executor - executor service.
     * @param lazyEnvironments - lazy environment list.
     * @param refreshCatalogs  - catalog for refresh.
     * @return BulkActionResult.
     */
    public List<Future<BulkActionResult>> runBulkAction(@Nonnull ExecutorService executor,
                                                        @Nonnull List<LazyEnvironment> lazyEnvironments,
                                                        @Nonnull List<TestDataTableCatalog> refreshCatalogs,
                                                        boolean saveOccupiedData) {
        List<Future<BulkActionResult>> futures = new ArrayList<>();
        Map<String, String> mdcMap = MDC.getCopyOfContextMap();
        refreshCatalogs.forEach(tableCatalog -> {
            Future<BulkActionResult> future = executor
                    .submit(() -> {
                        MdcUtils.setContextMap(mdcMap);
                        String envName = lazyEnvironments.stream()
                                .filter(env -> tableCatalog.getEnvironmentId().equals(env.getId()))
                                .findFirst()
                                .orElseThrow(() -> new
                                        TdmGetEnvironmentNameException(tableCatalog.getEnvironmentId().toString()))
                                .getName();
                        try {
                            mdcHelper.putConfigFields(tableCatalog);
                            RefreshResults refreshResults = dataRefreshService.runRefresh(tableCatalog.getTableName(),
                                    saveOccupiedData);
                            return new BulkActionResult(tableCatalog.getTableTitle(), tableCatalog.getTableName(),
                                    envName, refreshResults);
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

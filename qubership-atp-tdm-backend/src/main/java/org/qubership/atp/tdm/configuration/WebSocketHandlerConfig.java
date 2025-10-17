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

package org.qubership.atp.tdm.configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.qubership.atp.common.lock.LockManager;
import org.qubership.atp.tdm.env.configurator.service.EnvironmentsService;
import org.qubership.atp.tdm.mdc.TdmMdcHelper;
import org.qubership.atp.tdm.model.mail.bulkaction.BulkCleanupMailSender;
import org.qubership.atp.tdm.model.mail.bulkaction.BulkDropMailSender;
import org.qubership.atp.tdm.model.mail.bulkaction.BulkLinksRefreshMailSender;
import org.qubership.atp.tdm.model.mail.bulkaction.BulkRefreshMailSender;
import org.qubership.atp.tdm.repo.CatalogRepository;
import org.qubership.atp.tdm.repo.CleanupConfigRepository;
import org.qubership.atp.tdm.repo.ImportInfoRepository;
import org.qubership.atp.tdm.service.CleanupService;
import org.qubership.atp.tdm.service.ColumnService;
import org.qubership.atp.tdm.service.DataRefreshService;
import org.qubership.atp.tdm.service.TestDataService;
import org.qubership.atp.tdm.utils.CurrentTime;
import org.qubership.atp.tdm.websocket.bulkaction.cleanup.BulkDataCleanupHandler;
import org.qubership.atp.tdm.websocket.bulkaction.dataload.BulkDataImportHandler;
import org.qubership.atp.tdm.websocket.bulkaction.dataload.BulkDataRefreshHandler;
import org.qubership.atp.tdm.websocket.bulkaction.drop.BulkDataDropHandler;
import org.qubership.atp.tdm.websocket.bulkaction.links.BulkDataLinksRefreshHandler;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketHandlerConfig implements WebSocketConfigurer {

    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private final CatalogRepository catalogRepository;
    private final ImportInfoRepository importInfoRepository;
    private final DataRefreshService dataRefreshService;
    private final CleanupService cleanupService;
    private final EnvironmentsService environmentsService;
    private final TestDataService testDataService;
    private final ColumnService columnService;
    private final CleanupConfigRepository cleanupConfigRepository;
    private final BulkRefreshMailSender bulkRefreshMailSender;
    private final BulkCleanupMailSender bulkCleanupMailSender;
    private final BulkDropMailSender bulkDropMailSender;
    private final BulkLinksRefreshMailSender bulkLinksRefreshMailSender;
    private final CurrentTime currentTime;
    private final LockManager lockManager;
    private final TdmMdcHelper mdcHelper;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(new BulkDataRefreshHandler(executorService, catalogRepository, importInfoRepository,
                dataRefreshService, environmentsService, bulkRefreshMailSender, currentTime,
                lockManager, mdcHelper), "websocket/bulk/refresh").setAllowedOrigins("*");
        registry.addHandler(new BulkDataCleanupHandler(executorService, catalogRepository, environmentsService,
                cleanupService, cleanupConfigRepository, bulkCleanupMailSender, currentTime,
                lockManager, mdcHelper), "websocket/bulk/cleanup").setAllowedOrigins("*");
        registry.addHandler(new BulkDataImportHandler(executorService, catalogRepository, environmentsService,
                bulkCleanupMailSender, dataRefreshService, importInfoRepository, currentTime,
                lockManager, mdcHelper), "websocket/bulk/import").setAllowedOrigins("*");
        registry.addHandler(new BulkDataDropHandler(executorService, catalogRepository, environmentsService,
                        testDataService, bulkDropMailSender, currentTime, lockManager, mdcHelper),
                "websocket/bulk/drop").setAllowedOrigins("*");
        registry.addHandler(new BulkDataLinksRefreshHandler(executorService, catalogRepository, environmentsService,
                        columnService, bulkLinksRefreshMailSender, currentTime, lockManager, mdcHelper),
                "websocket/bulk/links").setAllowedOrigins("*");
    }

    @Bean(destroyMethod = "shutdown")
    @Qualifier("websocket")
    public ExecutorService executor() {
        return executorService;
    }
}

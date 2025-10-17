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

package org.qubership.atp.tdm.benchmarks.facades;

import java.util.UUID;

import jakarta.annotation.Nonnull;

import org.qubership.atp.tdm.repo.TestDataTableRepository;
import org.qubership.atp.tdm.service.ColumnService;
import org.qubership.atp.tdm.service.TestDataService;

public class SetupLinksFacade extends GeneralFacade {

    private final ColumnService columnService;

    public SetupLinksFacade(@Nonnull TestDataService testDataService,
                            @Nonnull TestDataTableRepository testDataTableRepository,
                            @Nonnull ColumnService columnService) {
        super(testDataService, testDataTableRepository);
        this.columnService = columnService;
    }

    public String setupColumnLinks(@Nonnull UUID projectId, @Nonnull UUID systemId,
                                   @Nonnull String tableName) {
        columnService.setupColumnLinks(true, projectId, systemId, tableName,
                "SIM", "?objectId=", false);
        return "Finished";
    }
}

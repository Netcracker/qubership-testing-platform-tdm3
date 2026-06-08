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

import java.util.List;
import java.util.Map;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import org.qubership.atp.tdm.model.rest.ResponseMessage;
import org.qubership.atp.tdm.model.rest.requests.AddInfoToRowRequest;
import org.qubership.atp.tdm.model.rest.requests.GetRowRequest;
import org.qubership.atp.tdm.model.rest.requests.OccupyRowRequest;
import org.qubership.atp.tdm.model.rest.requests.UpdateRowRequest;
import org.qubership.atp.tdm.repo.TestDataTableRepository;
import org.qubership.atp.tdm.service.AtpActionService;
import org.qubership.atp.tdm.service.TestDataService;

public class AtpActionsFacade extends GeneralFacade {

    private final AtpActionService atpActionService;

    public AtpActionsFacade(@Nonnull TestDataService testDataService,
                            @Nonnull TestDataTableRepository testDataTableRepository,
                            @Nonnull AtpActionService atpActionService) {
        super(testDataService, testDataTableRepository);
        this.atpActionService = atpActionService;
    }

    public ResponseMessage insertTestData(@Nonnull String projectName, @Nullable String envName,
                                          @Nullable String systemName, @Nonnull String tableTitle,
                                          List<Map<String, Object>> records) {
        return atpActionService.insertTestData(projectName, envName, systemName, tableTitle, records);
    }

    public List<ResponseMessage> occupyTestData(@Nonnull String projectName, @Nullable String envName,
                                                @Nullable String systemName, @Nonnull String tableTitle,
                                                List<OccupyRowRequest> occupyRowRequests) {
        return atpActionService.occupyTestData(projectName, envName, systemName, tableTitle, occupyRowRequests);
    }

    public List<ResponseMessage> updateTestData(@Nonnull String projectName, @Nullable String envName,
                                                @Nullable String systemName, @Nonnull String tableTitle,
                                                List<UpdateRowRequest> updateRowRequests) {
        return atpActionService.updateTestData(projectName, envName, systemName, tableTitle, updateRowRequests);
    }

    public List<ResponseMessage> getTestData(@Nonnull String projectName, @Nullable String envName,
                                             @Nullable String systemName, @Nonnull String tableTitle,
                                             List<GetRowRequest> getRowRequests) {
        return atpActionService.getTestData(projectName, envName, systemName, tableTitle, getRowRequests);
    }

    public List<ResponseMessage> addInfoToRow(@Nonnull String projectName, @Nullable String envName,
                                              @Nullable String systemName, @Nonnull String tableTitle,
                                              List<AddInfoToRowRequest> addInfoToRowRequests) {
        return atpActionService.addInfoToRow(projectName, envName, systemName, tableTitle, addInfoToRowRequests);
    }
}

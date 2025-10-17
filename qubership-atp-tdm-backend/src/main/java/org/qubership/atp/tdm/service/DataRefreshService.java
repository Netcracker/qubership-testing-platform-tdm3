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

package org.qubership.atp.tdm.service;

import java.text.ParseException;
import java.util.List;
import java.util.UUID;

import org.qubership.atp.tdm.model.refresh.RefreshResults;
import org.qubership.atp.tdm.model.refresh.TestDataRefreshConfig;

import jakarta.annotation.Nonnull;

public interface DataRefreshService {

    TestDataRefreshConfig getRefreshConfig(@Nonnull UUID id);

    TestDataRefreshConfig saveRefreshConfig(@Nonnull String tableName, @Nonnull Integer queryTimeout,
                                            @Nonnull TestDataRefreshConfig config) throws Exception;

    RefreshResults runRefresh(@Nonnull UUID configId);

    RefreshResults runRefresh(@Nonnull String tableName,
                              boolean saveOccupiedData) throws Exception;

    List<RefreshResults> runRefresh(@Nonnull String tableName, @Nonnull Integer queryTimeout, @Nonnull boolean allEnv,
                                    boolean saveOccupiedData) throws Exception;

    String getNextScheduledRun(String cronExpression) throws ParseException;

    void removeJob(@Nonnull UUID configId);
}

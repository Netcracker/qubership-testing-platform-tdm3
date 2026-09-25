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

/**
 * Refresh configuration and refresh runs of test data tables.
 */
public interface DataRefreshService {

    TestDataRefreshConfig getRefreshConfig(@Nonnull UUID id);

    TestDataRefreshConfig saveRefreshConfig(@Nonnull String tableName, @Nonnull Integer queryTimeout,
                                            @Nonnull TestDataRefreshConfig config) throws Exception;

    /**
     * Runs the saved configuration {@code configId} against its own table, replacing every row, unless
     * {@code configId}'s own {@code enabled} flag is unset.
     */
    RefreshResults runRefresh(@Nonnull UUID configId);

    /**
     * Runs {@code tableName}'s own stored import query against its system, keeping its occupied rows when
     * {@code saveOccupiedData} is set and replacing every row otherwise.
     */
    RefreshResults runRefresh(@Nonnull String tableName,
                              boolean saveOccupiedData) throws Exception;

    /**
     * Runs {@link #runRefresh(String, boolean)} for {@code tableName} and, when {@code allEnv} is set, for every
     * other table with the same title and the same import query, regardless of environment.
     */
    List<RefreshResults> runRefresh(@Nonnull String tableName, @Nonnull Integer queryTimeout, @Nonnull boolean allEnv,
                                    boolean saveOccupiedData) throws Exception;

    String getNextScheduledRun(String cronExpression) throws ParseException;

    /**
     * Removes {@code configId}'s Quartz job, without deleting the saved configuration.
     */
    void removeJob(@Nonnull UUID configId);
}

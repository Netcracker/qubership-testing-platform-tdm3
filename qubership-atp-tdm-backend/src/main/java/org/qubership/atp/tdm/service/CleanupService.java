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

import org.qubership.atp.tdm.model.cleanup.CleanupResults;
import org.qubership.atp.tdm.model.cleanup.CleanupSettings;
import org.qubership.atp.tdm.model.cleanup.TestDataCleanupConfig;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

public interface CleanupService {

    TestDataCleanupConfig getCleanupConfig(@Nonnull UUID id);

    CleanupSettings saveCleanupConfig(@Nonnull CleanupSettings cleanupSettings) throws Exception;

    List<CleanupResults> runCleanup(@Nonnull UUID configId) throws Exception;

    CleanupResults runCleanup(@Nonnull String tableName, @Nonnull TestDataCleanupConfig config) throws Exception;

    List<CleanupResults> runCleanup(@Nonnull CleanupSettings cleanupSettings) throws Exception;

    String getNextScheduledRun(@Nullable String cronExpression) throws ParseException;

    void removeUnused();

    void fillCleanupTypeColumn();

    CleanupSettings getCleanupSettings(@Nonnull UUID id);

    List<String> getTablesByTableNameAndEnvironmentsListWithSameSystemName(
            @Nonnull List<UUID> environmentsList,
            @Nonnull String tableName);
}

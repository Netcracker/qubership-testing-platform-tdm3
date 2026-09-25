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

/**
 * Cleanup configuration and cleanup runs of test data tables.
 */
public interface CleanupService {

    TestDataCleanupConfig getCleanupConfig(@Nonnull UUID id);

    /**
     * Saves the configuration for every table {@link #getTablesByTableNameAndEnvironmentsListWithSameSystemName}
     * finds for {@link CleanupSettings#getTableName()} and {@link CleanupSettings#getEnvironmentsList()}. When
     * {@link TestDataCleanupConfig#isShared()} is set, every other table of the project with the same title is set
     * to share it too, regardless of environment.
     */
    CleanupSettings saveCleanupConfig(@Nonnull CleanupSettings cleanupSettings) throws Exception;

    /**
     * Runs the saved configuration {@code configId} against every table that shares it.
     */
    List<CleanupResults> runCleanup(@Nonnull UUID configId) throws Exception;

    /**
     * Runs {@code config} against {@code tableName} directly, without saving the configuration or looking one up.
     *
     * @throws SecurityException if {@code config}'s type is {@code CLASS}; no cleaner class is registered
     */
    CleanupResults runCleanup(@Nonnull String tableName, @Nonnull TestDataCleanupConfig config) throws Exception;

    /**
     * Runs the unsaved configuration in {@code cleanupSettings} against every table
     * {@link #getTablesByTableNameAndEnvironmentsListWithSameSystemName} finds for
     * {@link CleanupSettings#getTableName()} and {@link CleanupSettings#getEnvironmentsList()}.
     */
    List<CleanupResults> runCleanup(@Nonnull CleanupSettings cleanupSettings) throws Exception;

    String getNextScheduledRun(@Nullable String cronExpression) throws ParseException;

    /**
     * Deletes every saved configuration that no table's catalog entry references any more, and its Quartz job.
     */
    void removeUnused();

    /**
     * Sets {@code type} on every saved configuration from the field it uses: {@code DATE} when {@code searchDate}
     * is set, otherwise {@code SQL} when {@code searchSql} is set, otherwise {@code CLASS} when {@code searchClass}
     * is set. A migration for configurations saved before {@code type} existed.
     */
    void fillCleanupTypeColumn();

    CleanupSettings getCleanupSettings(@Nonnull UUID id);

    /**
     * Finds every table with {@code tableName}'s own title, in one of {@code environmentsList}, whose system has
     * the same name as {@code tableName}'s own system. {@code tableName} itself is included only if its own
     * environment is in {@code environmentsList}.
     */
    List<String> getTablesByTableNameAndEnvironmentsListWithSameSystemName(
            @Nonnull List<UUID> environmentsList,
            @Nonnull String tableName);
}

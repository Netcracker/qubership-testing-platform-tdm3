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

package org.qubership.atp.tdm.repo;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.qubership.atp.tdm.model.TestDataTableCatalog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

@Repository
public interface CatalogRepository extends JpaRepository<TestDataTableCatalog, String> {

    List<TestDataTableCatalog> findAllByProjectIdAndSystemId(@Nonnull UUID projectId, @Nullable UUID systemId);

    List<TestDataTableCatalog> findAllByProjectIdAndEnvironmentId(@Nonnull UUID projectId,
                                                                  @Nullable UUID environmentId);

    List<TestDataTableCatalog> findByProjectIdAndLastUsageBefore(UUID projectId, Date before);

    List<TestDataTableCatalog> findByProjectIdNotInAndLastUsageBefore(Collection<UUID> projectIds, Date lastUsage);

    List<TestDataTableCatalog> findByLastUsageBefore(Date lastUsage);

    @Transactional
    void deleteByTableName(@Nonnull String tableName);

    TestDataTableCatalog findByRefreshConfigId(@Nonnull UUID id);

    TestDataTableCatalog findByProjectIdAndSystemIdAndTableTitle(@Nonnull UUID projectId, @Nonnull UUID systemId,
                                                                 @Nonnull String tableTitle);

    TestDataTableCatalog findByTableName(@Nonnull String tableName);

    TestDataTableCatalog findTableByProjectIdAndTableName(@Nonnull UUID projectId, @Nonnull String tableName);

    Optional<TestDataTableCatalog> findFirstByProjectIdAndTableTitle(@Nonnull UUID projectId,
                                                                    @Nonnull String tableTitle);

    List<TestDataTableCatalog> findAllByProjectIdAndTableTitle(@Nonnull UUID projectId, @Nonnull String tableTitle);

    List<TestDataTableCatalog> findAllByCleanupConfigId(@Nonnull UUID id);

    List<TestDataTableCatalog> findAllByProjectIdAndTableTitleAndCleanupConfigIdIsNotNull(@Nonnull UUID projectId,
                                                                                          @Nonnull String tableTitle);

    List<TestDataTableCatalog> findAllByProjectIdAndSystemIdAndCleanupConfigIdIsNotNull(@Nonnull UUID projectId,
                                                                                        @Nonnull UUID systemId);

    List<TestDataTableCatalog> findAllByProjectId(@Nonnull UUID projectId);

    List<TestDataTableCatalog> findByEnvironmentId(@Nonnull UUID environmentId);

    List<TestDataTableCatalog> findBySystemId(@Nonnull UUID systemId);

    List<TestDataTableCatalog> findAllByProjectIdAndTableTitleAndSystemIdIn(@Nonnull UUID projectId,
                                                                            @Nonnull String tableTitle,
                                                                            @Nonnull List<UUID> systemIds);

    List<TestDataTableCatalog> findAllByEnvironmentIdAndSystemId(@Nonnull UUID systemId, @Nonnull UUID environmentId);

    @Transactional
    @Modifying(flushAutomatically = true)
    @Query(value = "update TestDataTableCatalog c set c.lastUsage = :date where c.tableName = :tableName")
    void updateLastUsageByTableName(@Param("date") Date date, @Param("tableName") String tableName);
}

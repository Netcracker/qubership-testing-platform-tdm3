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

package org.qubership.atp.tdm.env.configurator.service;

import java.util.List;
import java.util.UUID;

import org.qubership.atp.tdm.env.configurator.model.Connection;
import org.qubership.atp.tdm.env.configurator.model.LazyEnvironment;
import org.qubership.atp.tdm.env.configurator.model.LazyProject;
import org.qubership.atp.tdm.env.configurator.model.LazySystem;
import org.qubership.atp.tdm.env.configurator.model.Project;
import org.qubership.atp.tdm.env.configurator.model.System;

import jakarta.annotation.Nonnull;

/**
 * Projects, dynamic environments, and systems. Projects come from the {@code PROJECTS_INFO} configuration; the
 * environments and systems are read directly from the database on every call, and only that database is dynamic:
 * despite their names, {@link #getLazyEnvironments}, {@link #getLazyEnvironmentsFromCache}, and
 * {@link #getLazyEnvironmentsRefresh} return the same result, and {@link #resetCaches} does nothing.
 */
public interface EnvironmentsService {

    /**
     * Returns {@code projectId} with every environment it has. Each environment carries one {@code System} entry
     * whose ID is its first system's, but whose connections are pooled from every system of that environment.
     */
    Project getFullProject(@Nonnull UUID projectId);

    LazyProject getLazyProjectById(@Nonnull UUID projectId);

    /**
     * Returns the project named {@code projectName}.
     *
     * @throws IllegalArgumentException if no project in {@code PROJECTS_INFO} has that name
     */
    LazyProject getLazyProjectByName(@Nonnull String projectName);

    List<LazyProject> getLazyProjects();

    List<LazyEnvironment> getLazyEnvironments(@Nonnull UUID projectId);

    /**
     * Same as {@link #getLazyEnvironments}. There is no cache to read from.
     */
    List<LazyEnvironment> getLazyEnvironmentsFromCache(@Nonnull UUID projectId);

    /**
     * Same as {@link #getLazyEnvironments}. There is no cache to refresh from.
     */
    List<LazyEnvironment> getLazyEnvironmentsRefresh(@Nonnull UUID projectId);

    LazyEnvironment getLazyEnvironment(@Nonnull UUID environmentId);

    /**
     * Returns {@code environmentId}'s name, or {@code null} if no environment has that ID.
     */
    String getEnvNameById(@Nonnull UUID environmentId);

    LazyEnvironment getLazyEnvironmentByName(@Nonnull UUID projectId, @Nonnull String environmentName);

    /**
     * Returns {@code systemId}'s connections.
     */
    List<Connection> getConnectionsSystemById(UUID environmentId, UUID systemId);

    LazySystem getLazySystemByName(@Nonnull UUID projectId, @Nonnull UUID environmentId, @Nonnull String systemName);

    LazySystem getLazySystemById(@Nonnull UUID environmentId, @Nonnull UUID systemId);

    List<LazySystem> getLazySystemsByProjectWithEnvIds(@Nonnull UUID projectId);

    List<LazySystem> getLazySystems(@Nonnull UUID environmentId);

    /**
     * Same as {@link #getLazySystemsByProjectWithEnvIds}, with each system's connections included.
     */
    List<LazySystem> getLazySystemsByProjectIdWithConnections(UUID projectId);

    /**
     * Returns the system named {@code systemName} in {@code environmentId}, with its connections.
     *
     * @throws org.qubership.atp.tdm.env.configurator.exceptions.internal.TdmEnvConvertFullSystemByNameException
     *      if no system has that name in that environment
     * @throws org.qubership.atp.tdm.env.configurator.exceptions.internal.TdmEnvDbConnectionException if the
     *      system has no connection named {@code DB}
     */
    System getFullSystemByName(@Nonnull UUID environmentId, @Nonnull String systemName);

    /**
     * Does nothing and returns {@code true}. There is no cache to reset.
     */
    boolean resetCaches();
}

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

import java.util.List;

import org.qubership.atp.tdm.model.rest.EnvironmentConnectionsResponse;
import org.qubership.atp.tdm.model.rest.ResponseMessage;
import org.qubership.atp.tdm.model.rest.requests.EnvironmentConnectionRequest;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * Create, update, and delete the dynamic environments and systems this service stores in its own database, backing
 * the Dynamic Environment API. {@code projectName} must already be one of the {@code PROJECTS_INFO} projects.
 */
public interface DynamicEnvironmentService {

    /**
     * Creates {@code envName} with {@code systemName}, or adds {@code systemName} to it if {@code envName} already
     * exists.
     *
     * @throws IllegalArgumentException if {@code systemName} already exists in {@code envName}
     */
    ResponseMessage createEnvironment(@Nonnull String projectName, @Nonnull String envName,
                                      @Nonnull String systemName,
                                      @Nonnull EnvironmentConnectionRequest connection);

    /**
     * Creates {@code envName} with {@code systemName} and every connection in {@code connections}, or adds the
     * system when the environment already exists.
     *
     * @throws IllegalArgumentException if {@code systemName} already exists in {@code envName}, or a connection
     *      is invalid
     */
    ResponseMessage createEnvironment(@Nonnull String projectName, @Nonnull String envName,
                                      @Nonnull String systemName,
                                      @Nonnull List<EnvironmentConnectionRequest> connections);

    /**
     * Upserts {@code connection} by name on {@code systemName}, and renames the environment or the system when
     * {@code newEnvName} or {@code newSystemName} is given.
     *
     * @throws org.qubership.atp.tdm.exceptions.internal.EnvironmentNotFoundException if {@code envName} or
     *      {@code systemName} does not exist
     */
    ResponseMessage updateEnvironment(@Nonnull String projectName, @Nonnull String envName,
                                      @Nonnull String systemName,
                                      @Nonnull EnvironmentConnectionRequest connection,
                                      @Nullable String newEnvName, @Nullable String newSystemName);

    /**
     * Upserts each connection in {@code connections} by name on {@code systemName}, and renames the environment
     * or the system when {@code newEnvName} or {@code newSystemName} is given. Connections omitted from the list
     * are left unchanged.
     *
     * @throws org.qubership.atp.tdm.exceptions.internal.EnvironmentNotFoundException if {@code envName} or
     *      {@code systemName} does not exist
     */
    ResponseMessage updateEnvironment(@Nonnull String projectName, @Nonnull String envName,
                                      @Nonnull String systemName,
                                      @Nonnull List<EnvironmentConnectionRequest> connections,
                                      @Nullable String newEnvName, @Nullable String newSystemName);

    /**
     * Returns connections for {@code envName}. When {@code systemName} is set, the result contains that system
     * only. Secret parameter values are masked.
     *
     * @throws org.qubership.atp.tdm.exceptions.internal.EnvironmentNotFoundException if {@code envName} does not
     *      exist
     * @throws org.qubership.atp.tdm.exceptions.internal.SystemNotFoundException if {@code systemName} is set and
     *      that system does not exist
     */
    EnvironmentConnectionsResponse getConnections(@Nonnull String projectName, @Nonnull String envName,
                                                  @Nullable String systemName);

    /**
     * Deletes {@code systemName} from {@code envName}, or the whole environment when {@code systemName} is not
     * given.
     */
    ResponseMessage deleteEnvironment(@Nonnull String projectName, @Nonnull String envName,
                                      @Nullable String systemName);
}

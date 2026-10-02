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

package org.qubership.atp.tdm.env.configurator.model;

import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A dynamic environment's own fields, with the names of its systems rather than their full data; see
 * {@link Environment} for an environment with its systems loaded.
 */
@Schema(description = "A dynamic environment.")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LazyEnvironment {

    @Schema(description = "Environment ID.")
    private UUID id;
    @Schema(description = "Project ID.")
    private UUID projectId;
    @Schema(description = "Environment name.")
    private String name;
    @Schema(description = "Cluster name.")
    private String clusterName;
    @Schema(description = "Environment description.")
    private String description;
    @Schema(description = "When the environment was created.")
    private String created;
    @Schema(description = "Who created the environment.")
    private String createdBy;
    @Schema(description = "When the environment was last modified.")
    private String modified;
    @Schema(description = "Who last modified the environment.")
    private String modifiedBy;
    @Schema(description = "Names of the environment's systems.")
    private List<String> systems;
}

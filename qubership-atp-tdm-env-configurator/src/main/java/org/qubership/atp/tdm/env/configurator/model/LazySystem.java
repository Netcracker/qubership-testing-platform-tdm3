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

@Schema(description = "A system of a dynamic environment.")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LazySystem {

    @Schema(description = "System ID.")
    private UUID id;
    @Schema(description = "System name.")
    private String name;
    @Schema(description = "System description.")
    private String description;
    @Schema(description = "When the system was created.")
    private String created;
    @Schema(description = "Who created the system.")
    private String createdBy;
    @Schema(description = "When the system was last modified.")
    private String modified;
    @Schema(description = "Who last modified the system.")
    private String modifiedBy;
    @Schema(description = "IDs of the environments the system belongs to.")
    private List<UUID> environmentIds;
    @Schema(description = "Names of the system's connections.")
    private List<String> connections;
}

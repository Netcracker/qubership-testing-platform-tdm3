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
import java.util.NoSuchElementException;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@EqualsAndHashCode(callSuper = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Environment extends AbstractConfiguratorModel {

    private UUID projectId;
    private List<System> systems;

    /**
     * Find system by ID.
     */
    public System getSystemByName(String name) throws NoSuchElementException {
        return getByName(systems, name);
    }

    /**
     * Find system by ID.
     */
    public System getSystemById(UUID id) throws NoSuchElementException {
        return getById(systems, id);
    }

    /**
     * Create Environment from LazyEnvironment.
     */
    public static Environment of(LazyEnvironment lazyEnvironment, List<System> systems) {
        Environment environment = new Environment();
        environment.setId(lazyEnvironment.getId());
        environment.setName(lazyEnvironment.getName());
        environment.setDescription(lazyEnvironment.getDescription());
        environment.setCreated(lazyEnvironment.getCreated());
        environment.setCreatedBy(lazyEnvironment.getCreatedBy());
        environment.setModified(lazyEnvironment.getModified());
        environment.setModifiedBy(lazyEnvironment.getModifiedBy());
        environment.setSystems(systems);
        return environment;
    }
}

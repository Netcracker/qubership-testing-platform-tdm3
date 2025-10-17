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
import java.util.stream.Collectors;

import org.qubership.atp.tdm.env.configurator.exceptions.internal.TdmEnvSearchSystemByIdException;

import jakarta.annotation.Nonnull;
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
public class Project extends AbstractConfiguratorModel {

    private String shortName;
    private List<Environment> environments;

    /**
     * Create Project from LazyProject.
     */
    public static Project of(LazyProject lazyProject, List<Environment> environments) {
        Project project = new Project();
        project.setId(lazyProject.getId());
        project.setName(lazyProject.getName());
        project.setEnvironments(environments);
        return project;
    }

    /**
     * Find environment by Name.
     */
    public Environment getEnvironmentByName(String id) throws NoSuchElementException {
        return getByName(environments, id);
    }

    /**
     * Find environment by ID.
     */
    public Environment getEnvironmentById(UUID id) throws NoSuchElementException {
        return getById(environments, id);
    }

    /**
     * Get full system by id and project.
     *
     * @param systemId - system id.
     * @return system.
     */
    public System getSystemById(@Nonnull UUID systemId) {
        return getEnvironments().stream()
                .flatMap(environment -> environment.getSystems().stream())
                .filter(system -> system.getId().equals(systemId))
                .findFirst()
                .orElseThrow(() -> new TdmEnvSearchSystemByIdException(systemId.toString()));
    }

    public List<System> getSystems() {
        return getEnvironments().stream()
                .flatMap(environment -> environment.getSystems().stream()).collect(Collectors.toList());
    }
}

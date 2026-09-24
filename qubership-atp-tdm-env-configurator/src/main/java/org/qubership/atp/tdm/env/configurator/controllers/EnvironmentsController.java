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

package org.qubership.atp.tdm.env.configurator.controllers;

import java.util.List;
import java.util.UUID;

import org.qubership.atp.integration.configuration.configuration.AuditAction;
import org.qubership.atp.tdm.env.configurator.model.LazyEnvironment;
import org.qubership.atp.tdm.env.configurator.model.LazyProject;
import org.qubership.atp.tdm.env.configurator.model.LazySystem;
import org.qubership.atp.tdm.env.configurator.service.EnvironmentsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@RequestMapping(value = "/api/tdm/")
@RestController()
@Tag(name = "environments-controller", description = "Projects, environments, and systems that test data tables "
        + "belong to. Projects come from PROJECTS_INFO; environments and systems are the dynamic environments "
        + "stored in the database.")
public class EnvironmentsController /* implements EnvironmentsControllerApi */ {

    private EnvironmentsService service;

    @Autowired
    public EnvironmentsController(EnvironmentsService service) {
        this.service = service;
    }

    @Operation(summary = "List projects", description = "Returns the ID and name of each project in PROJECTS_INFO.")
    @AuditAction(auditAction = "Get lazy projects")
    @GetMapping("/projects/lazy")
    public List<LazyProject> getProjects() {
        return service.getLazyProjects();
    }

    @Operation(summary = "List the environments of a project",
            description = "Returns each environment of the project with the IDs of its systems.")
    @AuditAction(auditAction = "Get lazy environment by projectId {{#projectId}}")
    @GetMapping("/projects/{projectId}/environments/lazy")
    @PreAuthorize("@entityAccess.checkAccess(#projectId, 'READ')")
    public List<LazyEnvironment> getLazyEnvironments(
            @Parameter(description = "Project ID.") @PathVariable("projectId") UUID projectId) {
        return service.getLazyEnvironments(projectId);
    }

    @Operation(summary = "Reload the environments of a project",
            description = "Returns the same list as GET /api/tdm/projects/{projectId}/environments/lazy. Environments "
                    + "are read from the database on each request.")
    @AuditAction(auditAction = "Refresh lazy environments by projectId {{#projectId}}")
    @GetMapping("/projects/{projectId}/environments/lazy/refresh")
    @PreAuthorize("@entityAccess.checkAccess(#projectId, 'READ')")
    public List<LazyEnvironment> getLazyEnvironmentsRefresh(
            @Parameter(description = "Project ID.") @PathVariable("projectId") UUID projectId) {
        return service.getLazyEnvironmentsRefresh(projectId);
    }

    @Operation(summary = "List the systems of an environment")
    @AuditAction(auditAction = "Get lazy system by environmentId {{#environmentId}}")
    @GetMapping("/environments/{environmentId}/systems/lazy")
    public List<LazySystem> getLazySystems(
            @Parameter(description = "Environment ID.") @PathVariable("environmentId") UUID environmentId) {
        return service.getLazySystems(environmentId);
    }

    @Operation(summary = "Reset the environment caches",
            description = "Does nothing and returns true.")
    @AuditAction(auditAction = "Reset caches")
    @GetMapping("/envs/reset/caches")
    public boolean resetCaches() {
        return service.resetCaches();
    }
}

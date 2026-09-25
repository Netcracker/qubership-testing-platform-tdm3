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

package org.qubership.atp.tdm.controllers;

import org.qubership.atp.integration.configuration.configuration.AuditAction;
import org.qubership.atp.tdm.exceptions.internal.EnvironmentNotFoundException;
import org.qubership.atp.tdm.model.rest.ResponseMessage;
import org.qubership.atp.tdm.model.rest.ResponseType;
import org.qubership.atp.tdm.model.rest.requests.EnvironmentManagementRequest;
import org.qubership.atp.tdm.service.DynamicEnvironmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Nonnull;

@RequestMapping("/api/tdm/rest/create-env")
@RestController
@Tag(name = "atp-env-controller", description = "Dynamic environments: environments, systems, and connections "
        + "created at run time and stored in the database. The project must be listed in PROJECTS_INFO. The "
        + "request body can hold the fields at the top level or inside an environment object.")
public class AtpEnvController {

    private final DynamicEnvironmentService service;

    @Autowired
    public AtpEnvController(@Nonnull DynamicEnvironmentService service) {
        this.service = service;
    }

    /**
     * Creates {@code request.envName} with {@code request.systemName} and its connection, or adds the system to
     * an existing environment. Returns HTTP 400 when the system already exists.
     */
    @Operation(summary = "Create an environment or add a system",
            description = "Creates the environment with the system and its connection, or adds the system to "
                    + "an existing environment. Returns HTTP 400 when the system already exists.")
    @AuditAction(auditAction = "ATP Action. Create environment {{#request.envName}} "
            + "in project {{#request.projectName}}")
    @PostMapping
    public ResponseMessage createEnvironment(@RequestBody EnvironmentManagementRequest request) {
        return service.createEnvironment(request.getProjectName(), request.getEnvName(),
                request.getSystemName(), request.getConnection());
    }

    /**
     * Replaces {@code request.systemName}'s connection, and renames the environment or the system when
     * {@code request.newEnvName} or {@code request.newSystemName} is set.
     */
    @Operation(summary = "Update a connection, or rename an environment or system",
            description = "Replaces the connection of the system, and renames the environment to newEnvName "
                    + "or the system to newSystemName when they are set.")
    @AuditAction(auditAction = "ATP Action. Update environment {{#request.envName}} "
            + "in project {{#request.projectName}}")
    @PutMapping
    public ResponseMessage updateEnvironment(@RequestBody EnvironmentManagementRequest request) {
        return service.updateEnvironment(request.getProjectName(), request.getEnvName(),
                request.getSystemName(), request.getConnection(),
                request.getNewEnvName(), request.getNewSystemName());
    }

    /**
     * Deletes {@code request.systemDeleteName} from {@code request.envName}, or the whole environment when
     * {@code systemDeleteName} is not set. Returns HTTP 404 when the environment does not exist.
     */
    @Operation(summary = "Delete an environment or a system",
            description = "Deletes the system in systemDeleteName, or the whole environment when "
                    + "systemDeleteName is not set. Returns HTTP 404 when the environment does not exist.")
    @AuditAction(auditAction = "ATP Action. Delete environment {{#request.envName}} "
            + "in project {{#request.projectName}}")
    @DeleteMapping
    public ResponseMessage deleteEnvironment(@RequestBody EnvironmentManagementRequest request) {
        return service.deleteEnvironment(request.getProjectName(), request.getEnvName(),
                request.getSystemDeleteName());
    }

    /**
     * The only failure of this controller with its own {@link ResponseMessage} body; every other one, including
     * {@code SystemNotFoundException} on {@code PUT}, falls through to Spring Boot's default error page.
     */
    @ExceptionHandler(EnvironmentNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ResponseMessage handleEnvironmentNotFound(EnvironmentNotFoundException ex) {
        return new ResponseMessage(ResponseType.ERROR, ex.getMessage());
    }
}

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
import jakarta.annotation.Nonnull;

@RequestMapping("/api/tdm/rest/create-env")
@RestController
public class AtpEnvController {

    private final DynamicEnvironmentService service;

    @Autowired
    public AtpEnvController(@Nonnull DynamicEnvironmentService service) {
        this.service = service;
    }

    @Operation(description = "ATP Action. Create dynamic environment with system and connection.")
    @AuditAction(auditAction = "ATP Action. Create environment {{#request.envName}} "
            + "in project {{#request.projectName}}")
    @PostMapping
    public ResponseMessage createEnvironment(@RequestBody EnvironmentManagementRequest request) {
        return service.createEnvironment(request.getProjectName(), request.getEnvName(),
                request.getSystemName(), request.getConnection());
    }

    @Operation(description = "ATP Action. Update connection parameters for an existing dynamic environment.")
    @AuditAction(auditAction = "ATP Action. Update environment {{#request.envName}} "
            + "in project {{#request.projectName}}")
    @PutMapping
    public ResponseMessage updateEnvironment(@RequestBody EnvironmentManagementRequest request) {
        return service.updateEnvironment(request.getProjectName(), request.getEnvName(),
                request.getSystemName(), request.getConnection(),
                request.getNewEnvName(), request.getNewSystemName());
    }

    @Operation(description = "ATP Action. Delete a dynamic environment or a single system within it.")
    @AuditAction(auditAction = "ATP Action. Delete environment {{#request.envName}} "
            + "in project {{#request.projectName}}")
    @DeleteMapping
    public ResponseMessage deleteEnvironment(@RequestBody EnvironmentManagementRequest request) {
        return service.deleteEnvironment(request.getProjectName(), request.getEnvName(),
                request.getSystemDeleteName());
    }

    @ExceptionHandler(EnvironmentNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ResponseMessage handleEnvironmentNotFound(EnvironmentNotFoundException ex) {
        return new ResponseMessage(ResponseType.ERROR, ex.getMessage());
    }
}

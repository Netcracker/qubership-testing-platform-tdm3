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

import java.io.IOException;

import org.qubership.atp.integration.configuration.configuration.AuditAction;
import org.qubership.atp.tdm.model.transfer.TransferStatus;
import org.qubership.atp.tdm.service.DatabaseExport;
import org.qubership.atp.tdm.service.DatabaseTransferService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * Moves the whole database of the service through the REST API. Every operation needs the transfer key, which
 * {@link org.qubership.atp.tdm.configuration.TransferKeyFilter} checks.
 */
@RestController
@RequestMapping("/api/tdm/database")
@Tag(name = "database-transfer-controller", description = "Exports and imports the whole database.")
@RequiredArgsConstructor
public class DatabaseTransferController {

    private final DatabaseTransferService service;

    /**
     * Streams a gzip-compressed SQL dump of the whole database.
     *
     * <p>The method writes to the servlet response on the request thread. A streaming response body would run under
     * the asynchronous request timeout of the servlet container, which cuts a long export.</p>
     */
    @Operation(summary = "Export the whole database",
            description = "Returns the whole database as a gzip-compressed SQL dump. Needs the X-TDM-Transfer-Key "
                    + "header. Answers 404 while the transfer is off, 403 for a wrong key, and 409 when the "
                    + "database is not a file database or another export is running.")
    @AuditAction(auditAction = "Export the whole database")
    @GetMapping("/export")
    public void export(HttpServletResponse response) throws IOException {
        try (DatabaseExport export = service.beginExport()) {
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType("application/gzip");
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                    ContentDisposition.attachment().filename(export.fileName()).build().toString());
            export.writeTo(response.getOutputStream());
        }
    }

    /**
     * Validates the uploaded dump and stores it for a later approval. The request body is the raw gzip file, not a
     * multipart form, so the multipart limits of the service do not apply to it.
     */
    @Operation(summary = "Stage a database dump",
            description = "Validates the request body, a gzip-compressed dump from the export operation, and stores "
                    + "it. Nothing changes in the database until the dump is approved. Send the file with "
                    + "Content-Type application/gzip. Answers 400 for an invalid dump with the reason, 409 when the "
                    + "database is not a file database, 413 over the size limit, and 507 when the volume lacks "
                    + "space.")
    @AuditAction(auditAction = "Stage a database dump")
    @PostMapping(value = "/import", consumes = {"application/gzip", "application/x-gzip",
            "application/octet-stream"})
    @ResponseStatus(HttpStatus.ACCEPTED)
    public TransferStatus stageImport(HttpServletRequest request) throws IOException {
        return service.stageImport(request.getInputStream(), request.getContentLengthLong());
    }

    /**
     * Reads the state of the import.
     */
    @Operation(summary = "Read the state of the database import",
            description = "Returns the state of the staged dump, or the result of the last restore.")
    @AuditAction(auditAction = "Read the state of the database import")
    @GetMapping("/import")
    public TransferStatus importStatus() {
        return service.importStatus();
    }

    /**
     * Deletes a staged or approved dump.
     */
    @Operation(summary = "Cancel the database import",
            description = "Deletes a staged or approved dump. The result of the last restore stays.")
    @AuditAction(auditAction = "Cancel the database import")
    @DeleteMapping("/import")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelImport() {
        service.cancelImport();
    }

    /**
     * Approves the staged dump.
     */
    @Operation(summary = "Approve the database import",
            description = "Approves the staged dump. The next start of the service replaces all data with it. "
                    + "With restart=true the service exits after the response, and the platform starts it again. "
                    + "Answers 400 unless confirm is REPLACE_ALL_DATA, and 409 when no dump is staged.")
    @AuditAction(auditAction = "Approve the database import")
    @PostMapping("/import/apply")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public TransferStatus applyImport(
            @Parameter(description = "Must be REPLACE_ALL_DATA.") @RequestParam(required = false) String confirm,
            @Parameter(description = "Exit after the response so that the platform restarts the service.")
            @RequestParam(defaultValue = "true") boolean restart) {
        return service.approveImport(confirm, restart);
    }
}

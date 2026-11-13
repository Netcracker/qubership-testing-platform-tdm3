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
import org.qubership.atp.tdm.service.DatabaseExport;
import org.qubership.atp.tdm.service.DatabaseTransferService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * Moves the whole database of the service through the REST API. Every operation needs the transfer key, which
 * {@link org.qubership.atp.tdm.configuration.TransferKeyInterceptor} checks.
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
}

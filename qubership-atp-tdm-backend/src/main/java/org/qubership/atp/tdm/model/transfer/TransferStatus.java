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

package org.qubership.atp.tdm.model.transfer;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * The state of a database import. The service answers with it and keeps it in {@code status.json} in the staging
 * directory.
 */
@Data
@Schema(description = "The state of a database import.")
public class TransferStatus {

    @Schema(description = "Where the import stands.")
    private TransferState state = TransferState.NONE;

    @Schema(description = "When the dump was uploaded.")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant uploadedAt;

    @Schema(description = "Size of the stored dump in bytes.")
    private Long sizeBytes;

    @Schema(description = "SHA-256 of the stored dump, in hexadecimal.")
    private String sha256;

    @Schema(description = "Version of the service that wrote the dump.")
    private String dumpServiceVersion;

    @Schema(description = "Version of H2 that wrote the dump.")
    private String dumpH2Version;

    @Schema(description = "Problems that do not stop the import.")
    private List<String> warnings = new ArrayList<>();

    @Schema(description = "When the import was approved.")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant approvedAt;

    @Schema(description = "When the last restore ended.")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant finishedAt;

    @Schema(description = "Why the last restore failed.")
    private String error;

    @Schema(description = "Name of the directory under backup/ that holds the database files the restore replaced.")
    private String backup;
}

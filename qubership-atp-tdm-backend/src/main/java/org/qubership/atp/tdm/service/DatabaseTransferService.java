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

package org.qubership.atp.tdm.service;

import java.io.InputStream;

import org.qubership.atp.tdm.exceptions.transfer.DatabaseTransferException;
import org.qubership.atp.tdm.model.transfer.TransferStatus;

/**
 * Moves the whole database of the service to another TDM3 service.
 */
public interface DatabaseTransferService {

    /**
     * Accepts an export of the whole database.
     *
     * @return the accepted export, which holds the only export slot until it is closed
     * @throws DatabaseTransferException with status {@code 409} when the database is not a file database or
     *                                   another export is running
     */
    DatabaseExport beginExport();

    /**
     * Validates an uploaded dump and stores it in the staging directory. A staged or approved dump that exists is
     * replaced. Nothing changes in the database.
     *
     * @param dump          the gzip-compressed dump, read to its end and not closed
     * @param contentLength the declared length of the dump in bytes, or a negative value when it is unknown
     * @return the status after the upload, in the state {@code STAGED}
     * @throws DatabaseTransferException with status {@code 400} when the dump breaks a rule, {@code 409} when the
     *                                   database is not a file database or another import operation is running,
     *                                   {@code 413} when the dump is over the size limit, and {@code 507} when the
     *                                   volume lacks the space for the restore
     * @throws java.io.UncheckedIOException when the staging directory cannot be written
     */
    TransferStatus stageImport(InputStream dump, long contentLength);

    /**
     * Reads the status of the import.
     *
     * @return the recorded status, in the state {@code NONE} when nothing was ever staged
     */
    TransferStatus importStatus();

    /**
     * Deletes a staged or approved dump. In every other state nothing changes, so the result of the last restore
     * stays readable.
     *
     * @throws DatabaseTransferException with status {@code 409} when an upload is running
     */
    void cancelImport();

    /**
     * Approves the staged dump, so that the next start of the service replaces the database with it.
     *
     * @param confirm the confirmation text, which must be {@code REPLACE_ALL_DATA}
     * @param restart {@code true} to have the service exit after the response, so that the platform starts it again
     * @return the status after the approval, in the state {@code APPROVED}
     * @throws DatabaseTransferException with status {@code 400} when the confirmation text is wrong, and {@code 409}
     *                                   when no dump is staged or an upload is running
     */
    TransferStatus approveImport(String confirm, boolean restart);
}

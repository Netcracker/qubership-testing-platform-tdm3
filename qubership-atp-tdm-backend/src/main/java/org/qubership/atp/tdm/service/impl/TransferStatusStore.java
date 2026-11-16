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

package org.qubership.atp.tdm.service.impl;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.qubership.atp.tdm.model.transfer.TransferStatus;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * Reads and writes {@code status.json} in the staging directory.
 *
 * <p>The restore step uses this class before the Spring context exists, so it owns its {@link ObjectMapper}.</p>
 */
public final class TransferStatusStore {

    private final ObjectMapper mapper = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(SerializationFeature.INDENT_OUTPUT);
    private final Path file;

    /**
     * Creates a store for one status file.
     *
     * @param file the path of {@code status.json}, which need not exist yet
     */
    public TransferStatusStore(Path file) {
        this.file = file;
    }

    /**
     * Reads the status.
     *
     * @return the recorded status, or a status in the state {@code NONE} while the file does not exist
     * @throws UncheckedIOException when the file exists and cannot be read or parsed
     */
    public TransferStatus read() {
        if (!Files.exists(file)) {
            return new TransferStatus();
        }
        try {
            return mapper.readValue(file.toFile(), TransferStatus.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read the transfer status file " + file, e);
        }
    }

    /**
     * Replaces the status file. A reader sees the old content or the new content, never a part of it.
     *
     * @param status the status to record
     * @throws UncheckedIOException when the file cannot be written
     */
    public void write(TransferStatus status) {
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.createDirectories(file.getParent());
            mapper.writeValue(temporary.toFile(), status);
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write the transfer status file " + file, e);
        }
    }
}

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

import java.io.IOException;
import java.io.OutputStream;

/**
 * A database export that the service has accepted and not yet written.
 *
 * <p>The caller must close the export, in a try-with-resources statement, to let the next export start.</p>
 */
public interface DatabaseExport extends AutoCloseable {

    /**
     * Returns the name for the downloaded file, {@code tdm-<database>-<timestamp>.sql.gz}.
     */
    String fileName();

    /**
     * Writes the gzip-compressed dump to the stream and finishes the compressed stream without closing the
     * underlying one.
     *
     * @param target receives the dump
     * @throws IOException when the target cannot be written
     */
    void writeTo(OutputStream target) throws IOException;

    @Override
    void close();
}

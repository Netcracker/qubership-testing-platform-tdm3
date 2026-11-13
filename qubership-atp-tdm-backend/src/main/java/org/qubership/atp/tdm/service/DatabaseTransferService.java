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

import org.qubership.atp.tdm.exceptions.transfer.DatabaseTransferException;

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
}

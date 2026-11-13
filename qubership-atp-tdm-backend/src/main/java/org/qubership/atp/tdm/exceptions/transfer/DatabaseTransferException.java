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

package org.qubership.atp.tdm.exceptions.transfer;

import org.springframework.http.HttpStatus;

import lombok.Getter;

/**
 * Reports a database transfer request that the service refuses, together with the HTTP status of the answer.
 */
@Getter
public class DatabaseTransferException extends RuntimeException {

    private final HttpStatus status;

    public DatabaseTransferException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }
}

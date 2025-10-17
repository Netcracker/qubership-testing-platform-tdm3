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

package org.qubership.atp.tdm.model.cleanup;

import org.qubership.atp.tdm.model.CommonResults;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@AllArgsConstructor
@NoArgsConstructor
public class CleanupResults extends CommonResults {

    private String tableName;
    private String error;
    private int recordsTotal;
    private int recordsRemoved;

    /**
     * Create cleanup result without error message.
     */
    public CleanupResults(String tableName, int recordsTotal, int recordsRemoved) {
        this.tableName = tableName;
        this.recordsTotal = recordsTotal;
        this.recordsRemoved = recordsRemoved;
    }
}

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

package org.qubership.atp.tdm.model.bulkaction;

import org.qubership.atp.tdm.model.CommonResults;

import jakarta.annotation.Nonnull;
import lombok.Data;

@Data
public class BulkActionResult {

    private String tableTitle;
    private String tableName;
    private String environmentName;
    private CommonResults results;
    private Exception exception;

    /**
     * Constructor for BulkActionResult.
     *
     * @param tableTitle - tableTitle.
     * @param tableName  - tableName.
     * @param results    - results.
     */
    public BulkActionResult(@Nonnull String tableTitle, @Nonnull String tableName, @Nonnull String environmentName,
                            @Nonnull CommonResults results) {
        this.tableTitle = tableTitle;
        this.tableName = tableName;
        this.environmentName = environmentName;
        this.results = results;
    }

    /**
     * Constructor for BulkActionResult.
     *
     * @param tableTitle - tableTitle.
     * @param tableName  - tableName.
     * @param exception  - exception info.
     */
    public BulkActionResult(@Nonnull String tableTitle, @Nonnull String tableName, @Nonnull String environmentName,
                            @Nonnull Exception exception) {
        this.tableTitle = tableTitle;
        this.tableName = tableName;
        this.environmentName = environmentName;
        this.exception = exception;
    }
}

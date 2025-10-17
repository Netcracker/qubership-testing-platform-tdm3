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

package org.qubership.atp.tdm.model.statistics;

import java.time.LocalDate;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode()
public class OutdatedStatisticsInner {

    private LocalDate date;
    private Long created;
    private Long consumed;
    private Long outdated;

    /**
     * Constructor.
     *
     * @param date - date.
     * @param created - created.
     * @param consumed - consumed.
     * @param outdated - outdated
     */
    public OutdatedStatisticsInner(LocalDate date, Long created, Long consumed, Long outdated) {
        this.date = date;
        this.created = created;
        this.consumed = consumed;
        this.outdated = outdated;
    }
}

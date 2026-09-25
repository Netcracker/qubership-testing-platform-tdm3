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

import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "Number of rows of one table occupied in each part of the requested period.")
@Data
@EqualsAndHashCode(callSuper = true)
public class ConsumedStatisticsItem extends StatisticsItem {

    @Schema(description = "Number of rows occupied in each part of the period, one value per label of "
            + "ConsumedStatistics.dates, in the same order.")
    private List<Long> consumed;
    @Schema(description = "Set instead of consumed on a row grouped across systems: one entry per system, with "
            + "consumed set.")
    private List<ConsumedStatisticsItem> details;

    /**
     * Class constructor.
     * @param context - data table name
     */
    public ConsumedStatisticsItem(String context) {
        super(StringUtils.EMPTY, StringUtils.EMPTY, context);
        this.details = Collections.emptyList();
    }

    /**
     * Class constructor.
     * @param context - data table name
     * @param consumed - number of consumed data
     */
    public ConsumedStatisticsItem(String context, List<Long> consumed) {
        super(StringUtils.EMPTY, StringUtils.EMPTY, context);
        this.consumed = consumed;
        this.details = Collections.emptyList();
    }
}

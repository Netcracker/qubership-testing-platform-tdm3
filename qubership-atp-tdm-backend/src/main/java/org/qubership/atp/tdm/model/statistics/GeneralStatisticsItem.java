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
import jakarta.annotation.Nonnull;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "Available and occupied row counts of one table. A row grouped across systems "
        + "carries the per-system rows in details instead of available, occupied, occupiedToday, and "
        + "total.")
@Data
@EqualsAndHashCode(callSuper = true)
public class GeneralStatisticsItem extends StatisticsItem {

    @Schema(description = "Number of available rows.")
    private Long available;
    @Schema(description = "Number of occupied rows.")
    private Long occupied;
    @Schema(description = "Number of rows occupied today.")
    private Long occupiedToday;
    @Schema(description = "Number of rows, available and occupied.")
    private Long total;
    @Schema(description = "Set instead of available, occupied, occupiedToday, and total on a row grouped across "
            + "systems: one entry per system, with those four fields set.")
    private List<GeneralStatisticsItem> details;

    /**
     * Class  constructor.
     *
     * @param context - data table name
     */
    public GeneralStatisticsItem(@Nonnull String context) {
        super(StringUtils.EMPTY, StringUtils.EMPTY, context);
        this.details = Collections.emptyList();
    }

    /**
     * Class  constructor.
     *
     * @param context   - data table name
     * @param available - number of available data
     * @param occupied  - number of consumed data
     */
    public GeneralStatisticsItem(@Nonnull String context, @Nonnull Long available, @Nonnull Long occupied,
                                 @Nonnull Long occupiedToday, @Nonnull Long total) {
        super(StringUtils.EMPTY, StringUtils.EMPTY, context);
        this.available = available;
        this.occupied = occupied;
        this.occupiedToday = occupiedToday;
        this.total = total;
        this.details = Collections.emptyList();
    }
}

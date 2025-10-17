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


import java.util.List;

import org.apache.commons.lang3.StringUtils;

import jakarta.annotation.Nonnull;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class UserGeneralStatisticsItem extends StatisticsItem {

    private List<Long> counts;

    /**
     * Class  constructor.
     *
     * @param context - data table name
     */
    public UserGeneralStatisticsItem(@Nonnull String context) {
        super(StringUtils.EMPTY, StringUtils.EMPTY, context);
    }

    /**
     * Class  constructor.
     *
     * @param envName    - environment name
     * @param systemName - system name
     * @param context    - data table name
     * @param counts     - list occupied data
     */
    public UserGeneralStatisticsItem(@Nonnull String envName, @Nonnull String systemName, @Nonnull String context,
                                     @Nonnull List<Long> counts) {
        super(envName, systemName, context);
        this.counts = counts;
    }
}

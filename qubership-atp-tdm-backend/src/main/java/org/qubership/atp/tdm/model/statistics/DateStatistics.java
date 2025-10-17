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

import java.util.ArrayList;
import java.util.List;

import jakarta.annotation.Nonnull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DateStatistics {

    private List<String> dates;
    private List<DateStatisticsItem> items;

    /**
     * Uniting statistics for deleted and existing tables.
     *
     * @param firstDateStatistics  - first source.
     * @param secondDateStatistics - second source.
     * @return united statistics list.
     */
    public static DateStatistics concatDateStatistics(@Nonnull DateStatistics firstDateStatistics,
                                                      @Nonnull DateStatistics secondDateStatistics) {
        DateStatistics statistics;
        if (firstDateStatistics.items.size() > secondDateStatistics.items.size()) {
            statistics = updateDateStatistics(firstDateStatistics, secondDateStatistics);
        } else {
            statistics = updateDateStatistics(secondDateStatistics, firstDateStatistics);
        }
        return statistics;
    }

    private static DateStatistics updateDateStatistics(@Nonnull DateStatistics firstDateStatistics,
                                                       @Nonnull DateStatistics secondDateStatistics) {
        firstDateStatistics.items.forEach(itemFirst -> {
            secondDateStatistics.items.forEach(itemSecond -> {
                if (itemSecond.getContext().equals(itemFirst.getContext())
                        && itemSecond.getEnvironment().equals(itemFirst.getEnvironment())
                        && itemSecond.getSystem().equals(itemFirst.getSystem())) {
                    List<Long> createdList = new ArrayList<>();
                    for (int i = 0; i < itemFirst.getCreated().size(); ++i) {
                        createdList.add(i, itemFirst.getCreated().get(i) + itemSecond.getCreated().get(i));
                    }
                    itemFirst.setCreated(createdList);
                }
            });
        });
        return firstDateStatistics;
    }
}



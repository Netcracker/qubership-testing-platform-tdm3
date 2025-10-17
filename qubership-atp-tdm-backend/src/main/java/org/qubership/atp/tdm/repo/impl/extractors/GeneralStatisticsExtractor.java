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

package org.qubership.atp.tdm.repo.impl.extractors;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.qubership.atp.tdm.model.statistics.GeneralStatisticsItem;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ResultSetExtractor;

import jakarta.annotation.Nonnull;

public class GeneralStatisticsExtractor implements ResultSetExtractor<GeneralStatisticsItem> {

    private final String tableTitle;

    GeneralStatisticsExtractor(@Nonnull String tableTitle) {
        this.tableTitle = tableTitle;
    }

    @Override
    public GeneralStatisticsItem extractData(ResultSet resultSet) throws SQLException, DataAccessException {
        GeneralStatisticsItem  generalStatisticsItem = new GeneralStatisticsItem(tableTitle);
        if (resultSet.next()) {
            generalStatisticsItem.setAvailable(resultSet.getLong(1));
            generalStatisticsItem.setOccupied(resultSet.getLong(2));
            generalStatisticsItem.setOccupiedToday(resultSet.getLong(3));
            generalStatisticsItem.setTotal(resultSet.getLong(4));
        }
        return generalStatisticsItem;
    }
}

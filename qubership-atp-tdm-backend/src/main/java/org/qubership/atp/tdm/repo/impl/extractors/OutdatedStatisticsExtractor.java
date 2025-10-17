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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.qubership.atp.tdm.model.statistics.OutdatedStatisticsInner;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ResultSetExtractor;

public class OutdatedStatisticsExtractor implements ResultSetExtractor<List<OutdatedStatisticsInner>> {

    OutdatedStatisticsExtractor() {
    }

    @Override
    public List<OutdatedStatisticsInner> extractData(ResultSet resultSet) throws SQLException, DataAccessException {
        List<OutdatedStatisticsInner> outdatedStatisticsItem = new ArrayList<>();
        while (resultSet.next()) {
            outdatedStatisticsItem.add(
                    new OutdatedStatisticsInner(LocalDate.parse(resultSet.getString(1)),
                            resultSet.getLong(2), resultSet.getLong(3),
                            resultSet.getLong(4)) {
                    });
        }
        return outdatedStatisticsItem;
    }
}

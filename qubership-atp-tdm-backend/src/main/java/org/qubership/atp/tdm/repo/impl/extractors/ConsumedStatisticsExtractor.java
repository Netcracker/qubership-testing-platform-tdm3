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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ResultSetExtractor;

public class ConsumedStatisticsExtractor implements ResultSetExtractor<List<Map<LocalDate, Long>>> {

    ConsumedStatisticsExtractor() {
    }

    @Override
    public List<Map<LocalDate, Long>> extractData(ResultSet resultSet) throws SQLException, DataAccessException {
        List<Map<LocalDate, Long>> consumedStatisticsItem = new ArrayList<>();
        while (resultSet.next()) {
            consumedStatisticsItem.add(new HashMap<LocalDate, Long>() {
                {
                    put(LocalDate.parse(resultSet.getString(1)), resultSet.getLong(2));
                }
            });
        }
        return consumedStatisticsItem;
    }
}

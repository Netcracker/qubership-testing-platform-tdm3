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

package org.qubership.atp.tdm.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.qubership.atp.tdm.model.table.OrderType;
import org.qubership.atp.tdm.model.table.TestDataTableFilter;
import org.qubership.atp.tdm.model.table.TestDataTableOrder;

/**
 * Fails when a filter value or a sort column of the users statistics request can change the SQL query.
 *
 * <p>A filter value must stay inside its string literal, and a sort column must be a single name. The value and
 * the column used to be copied into the query as they are.</p>
 */
class UsersOccupyStatisticUtilsTest {

    @Test
    void databaseFiltering_containsValueWithSingleQuote_staysInsideTheLiteral() {
        TestDataTableFilter filter = new TestDataTableFilter("occupied_by", "contains",
                Collections.singletonList("x"), true);

        String condition = UsersOccupyStatisticUtils.databaseFiltering(filter, "stats.occupied_by",
                "x' OR '1'='1");

        assertEquals("stats.occupied_by LIKE '%x'' OR ''1''=''1%'", condition);
    }

    @Test
    void databaseFiltering_startWithValueWithSingleQuote_staysInsideTheLiteral() {
        TestDataTableFilter filter = new TestDataTableFilter("occupied_by", "startWith",
                Collections.singletonList("x"), false);

        String condition = UsersOccupyStatisticUtils.databaseFiltering(filter, "stats.occupied_by", "O'Reilly");

        assertEquals("UPPER(stats.occupied_by) LIKE UPPER('O''Reilly%')", condition);
    }

    @Test
    void setOrderForUsersStats_columnName_isUsedAsItIs() {
        assertEquals("ORDER BY table_title DESC",
                UsersOccupyStatisticUtils.setOrderForUsersStats(new TestDataTableOrder("table_title", OrderType.DESC)));
    }

    @Test
    void setOrderForUsersStats_quotedDateAlias_isUsedAsItIs() {
        TestDataTableOrder order = new TestDataTableOrder("\"2024-05-01\"", OrderType.ASC);

        assertEquals("ORDER BY \"2024-05-01\" ASC", UsersOccupyStatisticUtils.setOrderForUsersStats(order));
    }

    @Test
    void setOrderForUsersStats_columnWithSqlAfterIt_isRejected() {
        TestDataTableOrder order = new TestDataTableOrder("occupied_by, (SELECT 1)", OrderType.ASC);

        assertThrows(IllegalArgumentException.class, () -> UsersOccupyStatisticUtils.setOrderForUsersStats(order));
    }
}

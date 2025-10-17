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

import org.qubership.atp.tdm.AbstractTestDataTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class QueryEvaluatorTest extends AbstractTestDataTest {

    @Autowired
    private QueryEvaluator queryEvaluator;

    @AfterEach
    public void after() {
        deleteTestDataTableIfExists("query_evaluator_table");
    }

    @Test
    public void queryEvaluator_evaluateQueryWithMacrosses_macrossesSuccessfullyReplaced() {
        String tableName = "query_evaluator_table";
        createTestDataTable(tableName);

        String query = "select name, object_id, object_type_id \n"
                + "from nc_objects \n"
                + "where OBJECT_ID not in (${select \"sim\" from tdm_table where \"SELECTED\" = false}) \n"
                + "and not object_type_id in (${select \"Operator ID\" from tdm_table})\n"
                + "and rownum < 3";

        String expectedQuery = "select name, object_id, object_type_id \n"
                + "from nc_objects \n"
                + "where OBJECT_ID not in (8901260720040140811, 8901260720040140822, 8901260720040140973, "
                + "8901260720040141084, 8901260720040140975, 8901260720040141106) \n"
                + "and not object_type_id in (2501, 2502, 2503, 2504, 2505, 2506)\n"
                + "and rownum < 3";

        String actualQuery = queryEvaluator.evaluate(tableName, query);

        Assertions.assertEquals(expectedQuery, actualQuery);
    }
}

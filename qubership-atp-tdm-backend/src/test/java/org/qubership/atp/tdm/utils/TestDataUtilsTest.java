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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;


public class TestDataUtilsTest {

    @Test
    public void columnsNames_getColumnsNamesFromQuery_columnsNamesReturned() {
        List<String> expectedColumnsNames = Arrays.asList("name", "OBJECT_ID", "description");

        String query = "select name, OBJECT_ID, description from nc_objects "
                + "where object_id in (select object_id from nc_references where ...)";

        List<String> actualColumnsNames = TestDataUtils.getColumnsNamesFromQuery(query);

        Assertions.assertEquals(expectedColumnsNames, actualColumnsNames);
    }

    @Test
    public void characters_escapeCharacters_charactersEscaped() {
        Assertions.assertEquals("string''s", TestDataUtils.escapeCharacters("string's"));
    }
}

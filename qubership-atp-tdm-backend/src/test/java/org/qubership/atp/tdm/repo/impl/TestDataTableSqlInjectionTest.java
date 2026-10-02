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

package org.qubership.atp.tdm.repo.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.qubership.atp.tdm.AbstractTestDataTest;
import org.qubership.atp.tdm.exceptions.db.TdmDbCheckColumnNameException;
import org.qubership.atp.tdm.model.table.OrderType;
import org.qubership.atp.tdm.model.table.TestDataTableFilter;
import org.qubership.atp.tdm.model.table.TestDataTableOrder;
import org.qubership.atp.tdm.utils.TestDataTableConvertor;

/**
 * Fails when a column name or a value from a request can change the SQL statement that reads or updates test data.
 *
 * <p>A column name that contains a double quote must be rejected, and a value that contains a single quote must be
 * written or matched as it is. {@code updateRows} and {@code addInfoToRow} used to put the request text into the
 * statement unescaped, and a crafted value or column name could update other columns or other rows.</p>
 */
class TestDataTableSqlInjectionTest extends AbstractTestDataTest {

    private static final String SIM = "8901260720040140822";
    private static final String OTHER_SIM = "8901260720040140811";

    private String tableName;

    @BeforeEach
    void createTable() {
        tableName = TestDataTableConvertor.generateTestDataTableName();
        createTestDataTable(tableName);
    }

    @AfterEach
    void dropTable() {
        deleteTestDataTableIfExists(tableName);
    }

    @Test
    void addInfoToRow_valueWithSingleQuote_isAppendedAsItIs() {
        String value = "x'), \"Status\" = ('changed";

        int updated = testDataTableRepository.addInfoToRow(tableName, filterBySim(SIM),
                Collections.singletonMap("environment", value));

        assertEquals(1, updated);
        assertEquals("ZLAB02\r\n" + value, row(SIM).get("environment"));
        assertEquals("52", row(SIM).get("Status"));
    }

    @Test
    void updateRows_valueWithSingleQuote_isWrittenAsItIs() {
        String value = "O'Reilly', \"Status\" = 'changed";

        int updated = testDataTableRepository.updateRows(tableName, filterBySim(SIM),
                Collections.singletonMap("environment", value));

        assertEquals(1, updated);
        assertEquals(value, row(SIM).get("environment"));
        assertEquals("52", row(SIM).get("Status"));
    }

    @Test
    void updateRows_filterValueWithSingleQuote_matchesTheRowWithThatValue() {
        TestDataTableFilter filter = new TestDataTableFilter("Assignment", "equals",
                Collections.singletonList("Test Automation' 1"), true);

        int updated = testDataTableRepository.updateRows(tableName, Collections.singletonList(filter),
                Collections.singletonMap("environment", "updated"));

        assertEquals(1, updated);
        assertEquals("updated", row(OTHER_SIM).get("environment"));
    }

    @Test
    void updateRows_setColumnWithDoubleQuote_isRejected() {
        Map<String, String> data = Collections.singletonMap("environment\" = 'x', \"Status", "changed");

        assertThrows(TdmDbCheckColumnNameException.class,
                () -> testDataTableRepository.updateRows(tableName, filterBySim(SIM), data));
        assertEquals("52", row(SIM).get("Status"));
    }

    @Test
    void addInfoToRow_setColumnWithDoubleQuote_isRejected() {
        Map<String, String> data = Collections.singletonMap("environment\" = 'x', \"Status", "changed");

        assertThrows(TdmDbCheckColumnNameException.class,
                () -> testDataTableRepository.addInfoToRow(tableName, filterBySim(SIM), data));
        assertEquals("52", row(SIM).get("Status"));
    }

    @Test
    void updateRows_filterColumnWithDoubleQuote_isRejected() {
        TestDataTableFilter filter = new TestDataTableFilter("sim\" IS NOT NULL OR \"sim", "equals",
                Collections.singletonList(SIM), true);
        List<TestDataTableFilter> filters = Collections.singletonList(filter);
        Map<String, String> data = Collections.singletonMap("environment", "changed");

        assertThrows(TdmDbCheckColumnNameException.class,
                () -> testDataTableRepository.updateRows(tableName, filters, data));
        assertEquals("ZLAB01", row(OTHER_SIM).get("environment"));
    }

    @Test
    void getTestData_filterColumnWithDoubleQuote_isRejected() {
        TestDataTableFilter filter = new TestDataTableFilter("sim\" IS NOT NULL OR \"sim", "equals",
                Collections.singletonList(SIM), true);
        List<TestDataTableFilter> filters = Collections.singletonList(filter);

        assertThrows(TdmDbCheckColumnNameException.class,
                () -> testDataTableRepository.getTestData(false, tableName, null, null, filters, null));
    }

    @Test
    void getTestData_orderColumnWithDoubleQuote_isRejected() {
        TestDataTableOrder order = new TestDataTableOrder("sim\", (SELECT 1) \"sim", OrderType.ASC);

        assertThrows(TdmDbCheckColumnNameException.class,
                () -> testDataTableRepository.getTestData(false, tableName, null, null, null, order));
    }

    private static List<TestDataTableFilter> filterBySim(String sim) {
        return Collections.singletonList(
                new TestDataTableFilter("sim", "equals", Collections.singletonList(sim), true));
    }

    private Map<String, Object> row(String sim) {
        return testDataTableRepository.getFullTestData(tableName).getData().stream()
                .filter(r -> sim.equals(String.valueOf(r.get("sim"))))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No row with sim " + sim));
    }
}

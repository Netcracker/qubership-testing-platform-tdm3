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

package org.qubership.atp.tdm.model.table.conditions.type;

import com.healthmarketscience.sqlbuilder.BinaryCondition;
import com.healthmarketscience.sqlbuilder.CustomSql;
import com.healthmarketscience.sqlbuilder.custom.postgresql.PgBinaryCondition;

public class TestDataTypeConditions {

    public static BinaryCondition getOccupiedBinaryCondition() {
        return getBinaryCondition("true");
    }

    public static BinaryCondition getAvailableBinaryCondition() {
        return getBinaryCondition("false");
    }

    private static BinaryCondition getBinaryCondition(String value) {
        return PgBinaryCondition.equalTo(new CustomSql("SELECTED"), new CustomSql(value));
    }
}

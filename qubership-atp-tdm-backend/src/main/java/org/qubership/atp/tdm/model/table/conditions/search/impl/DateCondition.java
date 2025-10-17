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

package org.qubership.atp.tdm.model.table.conditions.search.impl;

import org.qubership.atp.tdm.model.table.conditions.search.SearchCondition;

import com.healthmarketscience.sqlbuilder.BinaryCondition;
import com.healthmarketscience.sqlbuilder.CustomSql;
import com.healthmarketscience.sqlbuilder.custom.postgresql.PgBinaryCondition;

public class DateCondition implements SearchCondition {

    private static final String FROM_EXTENSION = "00:00:00";
    private static final String TO_EXTENSION = "23:59:59";
    private String position;

    public DateCondition(String position) {
        this.position = position;
    }

    @Override
    public BinaryCondition create(CustomSql customSql, String value) {
        switch (position) {
            case "FROM":
                return PgBinaryCondition.greaterThanOrEq(customSql, value + " " + FROM_EXTENSION);
            case "TO":
            default:
                return PgBinaryCondition.lessThanOrEq(customSql, value + " " + TO_EXTENSION);
        }
    }
}

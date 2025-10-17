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

package org.qubership.atp.tdm.model.table.conditions.factories;

import org.qubership.atp.tdm.model.table.conditions.search.SearchCondition;
import org.qubership.atp.tdm.model.table.conditions.search.SearchConditionType;
import org.qubership.atp.tdm.model.table.conditions.search.impl.ContainsCondition;
import org.qubership.atp.tdm.model.table.conditions.search.impl.DateCondition;
import org.qubership.atp.tdm.model.table.conditions.search.impl.EqualsCondition;
import org.qubership.atp.tdm.model.table.conditions.search.impl.StartWithCondition;

public class SearchConditionFactory {

    /**
     * Get search condition.
     */
    public static SearchCondition getCondition(String conditionType, boolean caseSensitive) {
        switch (SearchConditionType.find(conditionType)) {
            case CONTAINS:
                return new ContainsCondition(caseSensitive);
            case START_WITH:
                return new StartWithCondition(caseSensitive);
            case EQUALS:
                return new EqualsCondition();
            case FROM:
                return new DateCondition("FROM");
            case TO:
                return new DateCondition("TO");
            default:
                throw new IllegalArgumentException("Unknown search condition type: " + conditionType);
        }
    }
}

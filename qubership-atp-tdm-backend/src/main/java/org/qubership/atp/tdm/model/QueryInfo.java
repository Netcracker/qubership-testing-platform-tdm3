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

package org.qubership.atp.tdm.model;

import java.util.ArrayList;
import java.util.List;

import org.qubership.atp.tdm.model.table.OrderType;
import org.qubership.atp.tdm.model.table.TestDataTableFilter;
import org.qubership.atp.tdm.model.table.TestDataTableOrder;
import org.qubership.atp.tdm.model.table.TestDataType;
import org.qubership.atp.tdm.model.table.conditions.factories.SearchConditionFactory;
import org.qubership.atp.tdm.model.table.conditions.factories.TestDataTypeConditionFactory;
import org.qubership.atp.tdm.model.table.conditions.search.SearchCondition;
import org.qubership.atp.tdm.utils.TestDataUtils;

import com.healthmarketscience.sqlbuilder.BinaryCondition;
import com.healthmarketscience.sqlbuilder.ComboCondition;
import com.healthmarketscience.sqlbuilder.Condition;
import com.healthmarketscience.sqlbuilder.CustomSql;
import com.healthmarketscience.sqlbuilder.FunctionCall;
import com.healthmarketscience.sqlbuilder.OrderObject;
import com.healthmarketscience.sqlbuilder.SelectQuery;
import com.healthmarketscience.sqlbuilder.dbspec.Column;
import com.healthmarketscience.sqlbuilder.dbspec.basic.DbColumn;
import com.healthmarketscience.sqlbuilder.dbspec.basic.DbSchema;
import com.healthmarketscience.sqlbuilder.dbspec.basic.DbSpec;
import com.healthmarketscience.sqlbuilder.dbspec.basic.DbTable;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@EqualsAndHashCode
@ToString
public class QueryInfo {

    private static final String DB_COLUMN_DEFAULT_TYPE = "varchar";

    private SelectQuery query;
    private SelectQuery countQuery;

    /**
     * New Builder.
     * @param tableName - table name.
     * @param testDataType - test data type.
     * @return - builder.
     */
    public static Builder newBuilder(String tableName, TestDataType testDataType) {
        return new QueryInfo().new Builder()
                .init(tableName)
                .setTestDataType(testDataType);
    }

    /**
     * New Builder.
     * @param tableName - table name.
     * @param columnNames - column names.
     * @param testDataType - test data type.
     * @return - builder.
     */
    public static Builder newBuilder(String tableName, List<String> columnNames, TestDataType testDataType) {
        return new QueryInfo().new Builder()
                .init(tableName, columnNames)
                .setTestDataType(testDataType);
    }

    public class Builder {

        private Builder() {
            // private constructor
        }

        private Builder init(String tableName) {
            query = new SelectQuery().addAllColumns().addCustomFromTable(tableName);
            initCountQuery(tableName);
            return this;
        }

        private Builder init(String tableName, List<String> columnNames) {
            DbSpec spec = new DbSpec();
            DbSchema schema = spec.addDefaultSchema();
            DbTable dbTable = schema.addTable(tableName);
            Column[] columns = columnNames.stream()
                    .map(columnName -> new DbColumn(dbTable, "\"" + columnName + "\"", DB_COLUMN_DEFAULT_TYPE))
                    .toArray(Column[]::new);
            query = new SelectQuery().addFromTable(dbTable);
            query.addColumns(columns);
            initCountQuery(tableName);
            return this;
        }

        private void initCountQuery(String tableName) {
            countQuery = new SelectQuery().addCustomColumns(FunctionCall.countAll())
                    .addCustomFromTable(tableName);
        }

        private Builder setTestDataType(TestDataType testDataType) {
            if (!TestDataType.ALL.equals(testDataType)) {
                BinaryCondition binaryCondition = TestDataTypeConditionFactory.getCondition(testDataType);
                query.addCondition(binaryCondition);
                countQuery.addCondition(binaryCondition);
            }
            return this;
        }

        /**
         * Sets offset.
         */
        public Builder setOffset(Integer offset) {
            query.setOffset(offset);
            return this;
        }

        /**
         * Sets limit.
         */
        public Builder setLimit(Integer limit) {
            query.setFetchNext(limit);
            return this;
        }

        /**
         * Sets filters.
         */
        public Builder setFilters(List<TestDataTableFilter> filters) {
            List<Condition> conditions = new ArrayList<>();
            for (TestDataTableFilter filter : filters) {
                List<String> filterValues = filter.getValues();
                for (String filterValue : filterValues) {
                    filterValue = TestDataUtils.escapeCharacters(filterValue);
                    SearchCondition searchCondition =
                            SearchConditionFactory.getCondition(filter.getSearchCondition(),
                                    filter.isCaseSensitive());
                    CustomSql column = new CustomSql("\"" + filter.getColumn() + "\"");
                    BinaryCondition binaryCondition = searchCondition.create(column, filterValue);
                    conditions.add(ComboCondition.or(binaryCondition));
                }
                query.addCondition(ComboCondition.or(conditions.toArray()));
                countQuery.addCondition(ComboCondition.or(conditions.toArray()));
                conditions.clear();
            }
            return this;
        }

        /**
         * Sets ordering.
         */
        public Builder setOrder(TestDataTableOrder testDataTableOrder) {
            CustomSql column = new CustomSql("\"" + testDataTableOrder.getColumnName() + "\"");
            OrderObject.Dir dir = OrderObject.Dir.ASCENDING;
            if (OrderType.DESC.equals(testDataTableOrder.getOrderType())) {
                dir = OrderObject.Dir.DESCENDING;
            }
            query.addCustomOrdering(column, dir);
            return this;
        }

        public QueryInfo build() {
            return QueryInfo.this;
        }
    }
}

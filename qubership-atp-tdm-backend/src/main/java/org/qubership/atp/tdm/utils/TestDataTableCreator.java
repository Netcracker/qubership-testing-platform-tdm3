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

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.qubership.atp.tdm.repo.impl.SystemColumns;

import com.healthmarketscience.sqlbuilder.CreateTableQuery;
import com.healthmarketscience.sqlbuilder.InsertQuery;
import com.healthmarketscience.sqlbuilder.dbspec.basic.DbColumn;
import com.healthmarketscience.sqlbuilder.dbspec.basic.DbSchema;
import com.healthmarketscience.sqlbuilder.dbspec.basic.DbSpec;
import com.healthmarketscience.sqlbuilder.dbspec.basic.DbTable;
import jakarta.annotation.Nonnull;
import lombok.Data;

@Data
public class TestDataTableCreator {

    private static final String DEFAULT_COLUMN_TYPE = "varchar";
    private static final SimpleDateFormat DB_DATE_FORMATTER =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);

    private final DbTable dbTable;

    private Map<String, DbColumn> dbColumns = new HashMap<>();

    /**
     * Constructor for initializing dbTable object and add rowIdColumn.
     *
     * @param tableName table name.
     */
    public TestDataTableCreator(@Nonnull String tableName) {
        DbSpec spec = new DbSpec();
        DbSchema schema = spec.addDefaultSchema();
        dbTable = schema.addTable(tableName);
        dbColumns.put(SystemColumns.ROW_ID.getName(),
                dbTable.addColumn("\"" + SystemColumns.ROW_ID.getName() + "\"",
                        "uuid", null).setDefaultValue(UUID.randomUUID()));
        dbColumns.put(SystemColumns.SELECTED.getName(),
                dbTable.addColumn("\"" + SystemColumns.SELECTED.getName() + "\"",
                        "BOOLEAN", null).setDefaultValue("false"));
        dbColumns.put(SystemColumns.OCCUPIED_DATE.getName(),
                dbTable.addColumn("\"" + SystemColumns.OCCUPIED_DATE.getName() + "\"",
                        "TIMESTAMP", null));
        dbColumns.put(SystemColumns.OCCUPIED_BY.getName(),
                dbTable.addColumn("\"" + SystemColumns.OCCUPIED_BY.getName() + "\"",
                        DEFAULT_COLUMN_TYPE, null));
        dbColumns.put(SystemColumns.CREATED_WHEN.getName(),
                dbTable.addColumn("\"" + SystemColumns.CREATED_WHEN.getName() + "\"",
                        "TIMESTAMP", null));
    }

    public DbColumn buildColumn(@Nonnull String columnName) {
        return dbTable.addColumn("\"" + columnName + "\"",
                DEFAULT_COLUMN_TYPE, null);
    }

    public String createTableQuery() {
        return new CreateTableQuery(dbTable, true).validate().toString();
    }

    public InsertRowQuery initInsertRowQuery() {
        return new InsertRowQuery();
    }

    public InsertRowQuery initInsertRowQueryForImportData() {
        return new InsertRowQuery(true);
    }

    public class InsertRowQuery {

        private InsertQuery insertQuery;

        InsertRowQuery() {
            insertQuery = new InsertQuery(dbTable);
            insertQuery.addColumn(dbColumns.get(SystemColumns.ROW_ID.getName()), UUID.randomUUID());
            insertQuery.addColumn(dbColumns.get(SystemColumns.CREATED_WHEN.getName()),
                    DB_DATE_FORMATTER.format(new Timestamp(new Date().getTime())));
        }

        InsertRowQuery(boolean importData) {
            insertQuery = new InsertQuery(dbTable);
        }

        public void addRowColumn(@Nonnull DbColumn dbColumn, @Nonnull Object value) {
            insertQuery.addColumn(dbColumn, value);
        }

        public String createQuery() {
            return insertQuery.validate().toString();
        }
    }
}

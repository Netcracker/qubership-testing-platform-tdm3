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

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@ToString
public class TestDataTableImportInfo {

    @Id
    @Column(name = "table_name")
    private String tableName;
    @Column(name = "table_query")
    private String tableQuery;
    @Column(name = "query_timeout")
    private Integer queryTimeout;
    @Column(name = "update_by_query")
    private String updateByQuery;

    /**
     * TestDataTable Import Info.
     * @param tableName table name
     * @param tableQuery table query
     * @param queryTimeout query timeout
     */
    public TestDataTableImportInfo(String tableName, String tableQuery, Integer queryTimeout) {
        this.tableName = tableName;
        this.tableQuery = tableQuery;
        this.queryTimeout = queryTimeout;
    }
}

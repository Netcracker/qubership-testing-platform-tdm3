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

package org.qubership.atp.tdm.model.table.column;

import org.qubership.atp.tdm.model.ColumnType;
import org.qubership.atp.tdm.model.FilterType;
import org.qubership.atp.tdm.model.table.OrderType;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@ToString
@Entity
public class TestDataTableColumn {

    @EmbeddedId
    private TestDataTableColumnIdentity identity;
    @Enumerated(EnumType.STRING)
    @Column(name = "column_type")
    private ColumnType columnType;
    @Column(name = "column_link")
    private String columnLink;
    @Column(name = "bulk_link")
    private boolean bulkLink;
    @Transient
    private OrderType orderType;
    @Transient
    private FilterType filterType;

    /**
     * Constructor.
     */
    public TestDataTableColumn(TestDataTableColumnIdentity identity) {
        this.identity = identity;
        this.columnType = ColumnType.TEXT;
        this.filterType = FilterType.TEXT;
    }

    /**
     * Constructor.
     */
    public TestDataTableColumn(TestDataTableColumnIdentity identity, ColumnType columnType, FilterType filterType) {
        this.identity = identity;
        this.columnType = columnType;
        this.filterType = filterType;
    }

    /**
     * Constructor.
     */
    public TestDataTableColumn(TestDataTableColumnIdentity identity, ColumnType columnType, FilterType filterType,
                               String columnLink, boolean bulkLink) {
        this.identity = identity;
        this.columnType = columnType;
        this.columnLink = columnLink;
        this.filterType = filterType;
        this.bulkLink = bulkLink;
    }
}

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

package org.qubership.atp.tdm.model.table;

import java.util.List;

import org.qubership.atp.tdm.utils.TableColumnValuesConverter;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
public class TableColumnValues {
    @Schema(description = "Database table name of the table.")
    @Id
    @Column(name = "table_name")
    private String tableName;

    @Schema(description = "Title of the table.")
    @Column(name = "table_title")
    private String tableTitle;

    @Schema(description = "Column values to count as available for this table's row in "
            + "AvailableDataByColumnStats.")
    @Column(name = "vals")
    @Convert(converter = TableColumnValuesConverter.class)
    private List<String> values;
}

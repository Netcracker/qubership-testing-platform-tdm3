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

package org.qubership.atp.tdm.model.rest;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "One search condition on a column, used by the ATP action row requests to find the row "
        + "to act on.")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ApiDataFilter {
    @Schema(description = "Name of the column to search in.")
    @JsonProperty("name-column")
    private String column;
    @Schema(description = "\"Contains\", \"startWith\", \"Equals\", \"From\", or \"To\", case-insensitive.")
    @JsonProperty("search-criterion")
    private String searchCondition;
    @Schema(description = "Value to compare the column against.")
    @JsonProperty("search-value")
    private String value;
    @Schema(description = "Matches the column case-sensitively. Ignored by \"From\" and \"To\".")
    private boolean caseSensitive;
}

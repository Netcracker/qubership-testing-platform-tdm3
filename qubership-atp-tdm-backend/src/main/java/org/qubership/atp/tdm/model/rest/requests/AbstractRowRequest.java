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

package org.qubership.atp.tdm.model.rest.requests;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.qubership.atp.tdm.model.rest.ApiDataFilter;
import org.qubership.atp.tdm.model.table.TestDataTableFilter;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public abstract class AbstractRowRequest {

    @Schema(description = "Row to act on: every condition must match. The row is found among the available rows, "
            + "except where the request's own operation reads the occupied rows instead.")
    @JsonProperty("search-row-parameters-set")
    protected List<ApiDataFilter> filters;

    /**
     * Returns {@link #filters} converted to the shape the repository layer's search conditions expect.
     *
     * <p>Ignored by Jackson: its return type differs from {@link #filters}, and without {@code @JsonIgnore} it
     * would replace {@code ApiDataFilter} with this method's return type in the generated request schema.</p>
     */
    @JsonIgnore
    public List<TestDataTableFilter> getFilters() {
        return filters.stream().map(filter -> new TestDataTableFilter(filter.getColumn(), filter.getSearchCondition(),
                Collections.singletonList(filter.getValue()), filter.isCaseSensitive())).collect(Collectors.toList());
    }
}

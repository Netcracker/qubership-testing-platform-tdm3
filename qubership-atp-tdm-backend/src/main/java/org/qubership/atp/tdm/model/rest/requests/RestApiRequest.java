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

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "The request body of every atp-action-controller operation. Each operation reads only "
        + "projectName, envName, systemName, and title-table, plus the one list field that names it.")
@Data
public class RestApiRequest {

    @Schema(description = "Project name from PROJECTS_INFO.")
    private String projectName;
    @Schema(description = "Environment name. A trailing timestamp such as \" 2024-05-01T10:15:30\" is ignored. "
            + "When missing, the first table with this title in the project is used.")
    private String envName;
    @Schema(description = "System name. When missing, the first table with this title in the project is used.")
    private String systemName;
    @Schema(description = "Title of the table.")
    @JsonProperty("title-table")
    private String titleTable;
    @Schema(description = "Rows to insert, one map of column name to value per row. Read by /insert-records.")
    @JsonProperty("insert-records")
    private List<Map<String, Object>> records;
    @Schema(description = "Read by /occupy-records.")
    @JsonProperty("occupy-row-requests")
    private List<OccupyRowRequest> occupyRowRequests;
    @Schema(description = "Read by /occupy-records-full-row.")
    @JsonProperty("occupy-full-row-requests")
    private List<OccupyFullRowRequest> occupyFullRowRequests;
    @Schema(description = "Read by /release-records.")
    @JsonProperty("release-row-requests")
    private List<ReleaseRowRequest> releaseRowRequests;
    @Schema(description = "Read by /update-records.")
    @JsonProperty("update-row-requests")
    private List<UpdateRowRequest> updateRowRequests;
    @Schema(description = "Read by /get-record and /get-records.")
    @JsonProperty("get-row-requests")
    private List<GetRowRequest> getRowRequests;
    @Schema(description = "Read by /add-info-to-row.")
    @JsonProperty("add-info-to-row-requests")
    private List<AddInfoToRowRequest> addInfoToRowRequests;

    @JsonProperty("environment")
    private void unpackEnvironment(Map<String, Object> environment) {
        this.projectName = (String) environment.get("projectName");
        this.envName = (String) environment.get("envName");
        this.systemName = (String) environment.get("systemName");
    }
}

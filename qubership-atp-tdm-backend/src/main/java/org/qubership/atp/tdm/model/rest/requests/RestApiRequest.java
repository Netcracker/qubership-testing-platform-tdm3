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
import lombok.Data;

@Data
public class RestApiRequest {

    private String projectName;
    private String envName;
    private String systemName;
    @JsonProperty("title-table")
    private String titleTable;
    @JsonProperty("insert-records")
    private List<Map<String, Object>> records;
    @JsonProperty("occupy-row-requests")
    private List<OccupyRowRequest> occupyRowRequests;
    @JsonProperty("occupy-full-row-requests")
    private List<OccupyFullRowRequest> occupyFullRowRequests;
    @JsonProperty("release-row-requests")
    private List<ReleaseRowRequest> releaseRowRequests;
    @JsonProperty("update-row-requests")
    private List<UpdateRowRequest> updateRowRequests;
    @JsonProperty("get-row-requests")
    private List<GetRowRequest> getRowRequests;
    @JsonProperty("add-info-to-row-requests")
    private List<AddInfoToRowRequest> addInfoToRowRequests;

    @JsonProperty("environment")
    private void unpackEnvironment(Map<String, Object> environment) {
        this.projectName = (String) environment.get("projectName");
        this.envName = (String) environment.get("envName");
        this.systemName = (String) environment.get("systemName");
    }
}

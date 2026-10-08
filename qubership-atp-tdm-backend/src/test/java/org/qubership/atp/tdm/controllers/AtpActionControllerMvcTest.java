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

package org.qubership.atp.tdm.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.qubership.atp.tdm.AbstractTestDataTest;
import org.qubership.atp.tdm.model.TestDataOccupyStatistic;
import org.qubership.atp.tdm.model.TestDataTableCatalog;
import org.qubership.atp.tdm.model.rest.ApiDataFilter;
import org.qubership.atp.tdm.model.rest.requests.OccupyFullRowRequest;
import org.qubership.atp.tdm.model.rest.requests.OccupyRowRequest;
import org.qubership.atp.tdm.model.rest.requests.RestApiRequest;
import org.qubership.atp.tdm.repo.OccupyStatisticRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Fails when an occupy endpoint of {@link AtpActionController} stops requiring {@code occupiedBy}, or stores the
 * user name differently from the one in the request.
 *
 * <p>The requests send {@code occupiedBy} percent-encoded in the query string, so the user name passes through
 * the same URL decoding as a real call.</p>
 */
@AutoConfigureMockMvc
class AtpActionControllerMvcTest extends AbstractTestDataTest {

    private static final String OCCUPY_PATH = "/api/tdm/rest/occupy-records";
    private static final String OCCUPY_FULL_ROW_PATH = "/api/tdm/rest/occupy-records-full-row";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private OccupyStatisticRepository occupyStatisticRepository;

    @BeforeEach
    void setUp() {
        when(environmentsService.getLazyProjectByName(any())).thenReturn(lazyProject);
        when(environmentsService.getLazyEnvironmentByName(any(), any())).thenReturn(lazyEnvironment);
        when(environmentsService.getLazySystemByName(any(), any(), any())).thenReturn(lazySystem);
        when(environmentsService.getConnectionsSystemById(any(), any())).thenReturn(connections);
    }

    @Test
    void occupyRecords_occupiedByMissing_returns400() throws Exception {
        mockMvc.perform(post(OCCUPY_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void occupyRecordsFullRow_occupiedByMissing_returns400() throws Exception {
        mockMvc.perform(post(OCCUPY_FULL_ROW_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"Иван Петров", "Александр", "Müller-Łukasz", "a.b, c;d: \"e\" (f) [g] & h%_ +=#?/"})
    void occupyRecords_occupiedByWithSpacesNationalCharsAndPunctuation_storedUnchanged(String occupiedBy)
            throws Exception {
        String tableName = "tdm_api_test_mvc_occupy_user_name";
        TestDataTableCatalog catalog = createTable(tableName, "TDM API Test Mvc Occupy User Name");
        OccupyRowRequest occupyRowRequest = new OccupyRowRequest();
        occupyRowRequest.setNameColumnResponse("Assignment");
        occupyRowRequest.setFilters(Collections.singletonList(filter()));
        RestApiRequest request = request(catalog);
        request.setOccupyRowRequests(Collections.singletonList(occupyRowRequest));

        mockMvc.perform(post(uri(OCCUPY_PATH, occupiedBy))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("SUCCESS"));

        verifyOccupiedBy(tableName, occupiedBy);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Иван Петров", "Александр", "Müller-Łukasz", "a.b, c;d: \"e\" (f) [g] & h%_ +=#?/"})
    void occupyRecordsFullRow_occupiedByWithSpacesNationalCharsAndPunctuation_storedUnchanged(String occupiedBy)
            throws Exception {
        String tableName = "tdm_api_test_mvc_occupy_full_row_user_name";
        TestDataTableCatalog catalog = createTable(tableName, "TDM API Test Mvc Occupy Full Row User Name");
        OccupyFullRowRequest occupyRowRequest = new OccupyFullRowRequest();
        occupyRowRequest.setResponseColumnNames(Collections.singletonList("Assignment"));
        occupyRowRequest.setFilters(Collections.singletonList(filter()));
        RestApiRequest request = request(catalog);
        request.setOccupyFullRowRequests(Collections.singletonList(occupyRowRequest));

        mockMvc.perform(post(uri(OCCUPY_FULL_ROW_PATH, occupiedBy))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("SUCCESS"));

        verifyOccupiedBy(tableName, occupiedBy);
    }

    private TestDataTableCatalog createTable(String tableName, String tableTitle) {
        TestDataTableCatalog catalog = createTestDataTableCatalog(projectId, systemId, environmentId, tableTitle,
                tableName);
        createTestDataTable(tableName);
        return catalog;
    }

    private static ApiDataFilter filter() {
        return new ApiDataFilter("sim", "Contains", "12607200401410", false);
    }

    private static RestApiRequest request(TestDataTableCatalog catalog) {
        RestApiRequest request = new RestApiRequest();
        request.setProjectName(lazyProject.getName());
        request.setEnvName(lazyEnvironment.getName());
        request.setSystemName(system.getName());
        request.setTitleTable(catalog.getTableTitle());
        return request;
    }

    /**
     * Builds the request URI with {@code occupiedBy} percent-encoded the way a client sends it.
     */
    private static URI uri(String path, String occupiedBy) {
        return UriComponentsBuilder.fromPath(path)
                .queryParam("occupiedBy", "{occupiedBy}")
                .encode()
                .buildAndExpand(occupiedBy)
                .toUri();
    }

    private void verifyOccupiedBy(String tableName, String occupiedBy) {
        List<TestDataOccupyStatistic> statistics = new ArrayList<>();
        for (TestDataOccupyStatistic statistic
                : occupyStatisticRepository.findAllByProjectIdAndSystemId(projectId, systemId)) {
            if (tableName.equals(statistic.getTableName())) {
                statistics.add(statistic);
            }
        }
        List<Object> occupiedByInTable = new ArrayList<>();
        for (Map<String, Object> row
                : testDataTableRepository.getTestData(true, tableName, null, null, null, null).getData()) {
            if (Objects.nonNull(row.get("OCCUPIED_BY"))) {
                occupiedByInTable.add(row.get("OCCUPIED_BY"));
            }
        }
        occupyStatisticRepository.deleteAll(statistics);
        deleteTestDataTableIfExists(tableName);
        catalogRepository.deleteByTableName(tableName);

        assertEquals(Collections.singletonList(occupiedBy), occupiedByInTable);
        assertEquals(1, statistics.size());
        assertEquals(occupiedBy, statistics.get(0).getOccupiedBy());
    }
}

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

package org.qubership.atp.tdm.benchmarks.facades;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import jakarta.annotation.Nonnull;

import org.apache.commons.lang3.StringUtils;

import org.qubership.atp.tdm.model.table.TestDataTable;
import org.qubership.atp.tdm.model.table.TestDataTableFilter;
import org.qubership.atp.tdm.repo.TestDataTableRepository;
import org.qubership.atp.tdm.service.TestDataService;

public class GetDataFacade extends GeneralFacade {

    private static final String TEST_DATA_TABLE_FILTER_EQUALS = "tdm_benchmark_test_data_filter_equals";
    private static final String TEST_DATA_TABLE_FILTER_CONTAINS = "tdm_benchmark_test_data_filter_contains";
    private static final String TEST_DATA_TABLE_FILTER_DATES = "tdm_benchmark_test_data_filter_dates";
    private static final String TEST_DATA_TABLE_PAGINATION = "tdm_benchmark_test_data_pagination";

    private final List<TestDataTableFilter> equalFilters;
    private final List<TestDataTableFilter> containsFilters;
    private final List<TestDataTableFilter> dateFilters;

    public GetDataFacade(@Nonnull TestDataService testDataService,
                         @Nonnull TestDataTableRepository testDataTableRepository) {
        super(testDataService, testDataTableRepository);
        equalFilters = prepareFilterEquals();
        containsFilters = prepareFilterContains();
        dateFilters = prepareFilterDates();
    }

    public List<TestDataTableFilter> prepareFilterEquals() {
        return Collections.singletonList(new TestDataTableFilter("SIM", "equals",
                filterValues(), false));
    }

    public TestDataTable getTestDataFilterEquals() {
        return testDataService.getTestData(TEST_DATA_TABLE_FILTER_EQUALS, null, null,
                equalFilters, null, false);
    }

    public List<TestDataTableFilter> prepareFilterContains() {
        return Collections.singletonList(new TestDataTableFilter("SIM", "contains",
                filterValues(), false));
    }

    public TestDataTable getTestDataFilterContains() {
        return testDataService.getTestData(TEST_DATA_TABLE_FILTER_CONTAINS, null, null,
                containsFilters, null, false);
    }

    public List<TestDataTableFilter> prepareFilterDates() {
        LocalDate currentDate = LocalDate.now();
        return Arrays.asList(
                new TestDataTableFilter("CREATED_WHEN", "From",
                        Collections.singletonList(currentDate.toString()), false),
                new TestDataTableFilter("CREATED_WHEN", "To",
                        Collections.singletonList(currentDate.toString()), false));
    }

    public TestDataTable getTestDataFilterDates() {
        return testDataService.getTestData(TEST_DATA_TABLE_FILTER_DATES, null, null,
                dateFilters, null, false);
    }

    public TestDataTable getTestDataPagination() {
        return testDataService.getTestData(TEST_DATA_TABLE_PAGINATION, 0, 10, null, null, false);
    }

    public String getTableName(String filterName) {
        switch (filterName) {
            case "FilterEquals":
                return TEST_DATA_TABLE_FILTER_EQUALS;
            case "FilterContains":
                return TEST_DATA_TABLE_FILTER_CONTAINS;
            case "FilterDates":
                return TEST_DATA_TABLE_FILTER_DATES;
            case "Pagination":
                return TEST_DATA_TABLE_PAGINATION;
            default:
                return StringUtils.EMPTY;
        }
    }

    private List<String> filterValues() {
        return Collections.singletonList(TEST_DATA_SEARCH_VALUE);
    }
}

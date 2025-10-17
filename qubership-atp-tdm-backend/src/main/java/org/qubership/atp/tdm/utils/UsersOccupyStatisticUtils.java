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

import static org.qubership.atp.tdm.utils.TestDataQueries.GET_OCCUPIED_BY_USERS_TABLE;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.qubership.atp.tdm.env.configurator.model.LazyEnvironment;
import org.qubership.atp.tdm.env.configurator.model.LazySystem;
import org.qubership.atp.tdm.env.configurator.service.EnvironmentsService;
import org.qubership.atp.tdm.model.statistics.OccupiedDataByUsersStatistics;
import org.qubership.atp.tdm.model.statistics.UsersOccupyFields;
import org.qubership.atp.tdm.model.statistics.UsersOccupyStatisticRequest;
import org.qubership.atp.tdm.model.table.OrderType;
import org.qubership.atp.tdm.model.table.TestDataTableFilter;
import org.qubership.atp.tdm.model.table.TestDataTableOrder;
import org.qubership.atp.tdm.model.table.conditions.search.SearchConditionType;

import jakarta.persistence.Query;

public class UsersOccupyStatisticUtils {

    private static final String FILTER_TEMPLATE = "%s LIKE %s";
    private static final String UPPER_CASE_TEMPLATE = "UPPER(%s)";
    private static final String ORDER_BY_TEMPLATE = "ORDER BY %s %s";
    private static final String IN_LIST_FILTER_TEMPLATE = "%s IN (%s)";
    private static final String EMPTY_LIST_FILTER_TEMPLATE = "%s IN (NULL)";
    private static final String ENVIRONMENT_FIELD = "catalog.environment_id";
    private static final String SYSTEM_FIELD = "stats.system_id";
    private static final String STATS_PREFIX = "stats.";
    private static final String SUMM_TEMPLATE = " sum(case when occupied_date = '%s' "
            + " then amount else 0 end) AS \"%s\",";

    /**
     * Generate SQL native query to get data about occupy data with users.
     *
     * @param request             request data
     * @param environmentsService Environment service
     * @return SQL native query.
     */
    public static String generateRequest(UsersOccupyStatisticRequest request, EnvironmentsService environmentsService) {
        return String.format(GET_OCCUPIED_BY_USERS_TABLE,
                generateSummFields(request.getDateFrom(), request.getDateTo()),
                request.getProjectId(),
                request.getDateFrom(),
                request.getDateTo(),
                setFiltersForUsersStats(request, environmentsService),
                setOrderForUsersStats(request.getDataTableOrder()));
    }

    /**
     * Generate fields string by template.
     *
     * @param dateFrom first field date
     * @param dateTo   lase field date
     * @return String of fields
     */
    public static String generateSummFields(String dateFrom, String dateTo) {
        List<String> datesBetween = getDatesBetween(
                LocalDate.parse(dateFrom),
                LocalDate.parse(dateTo));
        StringBuilder stringBuilder = new StringBuilder();
        datesBetween.forEach(
                date -> {
                    stringBuilder.append(String.format(
                            SUMM_TEMPLATE,
                            LocalDate.parse(date),
                            date));
                }
        );
        stringBuilder.deleteCharAt(stringBuilder.length() - 1);
        return stringBuilder.toString();
    }

    /**
     * Get list of dates between two date.
     *
     * @param startDate Start day
     * @param endDate   Finish day
     * @return list of string with date YYYY-MM-DD
     */
    public static List<String> getDatesBetween(
            LocalDate startDate, LocalDate endDate) {
        long numOfDaysBetween = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        return IntStream.iterate(0, i -> i + 1)
                .limit(numOfDaysBetween)
                .mapToObj(i -> startDate.plusDays(i).format(DateTimeFormatter.ISO_LOCAL_DATE))
                .collect(Collectors.toList());
    }

    /**
     * Set pagination to query.
     *
     * @param query   native query
     * @param request source request
     * @return Query with offset and limit
     */
    public static Query setPagination(Query query, UsersOccupyStatisticRequest request) {
        return query
                .setMaxResults(Math.toIntExact(request.getLimit()))
                .setFirstResult(Math.toIntExact(request.getOffset()));
    }

    /**
     * Set filters for user stats.
     *
     * @param request             Source request
     * @param environmentsService EnvironmentService
     * @return String with all filters
     */
    public static String setFiltersForUsersStats(
            UsersOccupyStatisticRequest request,
            EnvironmentsService environmentsService) {
        if (!CollectionUtils.isNotEmpty(request.getFilters())) {
            return "";
        }
        StringBuilder filters = new StringBuilder("AND ");
        request.getFilters().forEach(
                filter -> {
                    String preparedFilter = chooseFilterType(filter, request.getProjectId(), environmentsService);
                    if (!StringUtils.isEmpty(preparedFilter)) {
                        filters.append(preparedFilter).append(" AND ");
                    }
                }
        );
        return filters.substring(0, filters.length() - 4);
    }

    /**
     * Generate filter string.
     *
     * @param filter              filter
     * @param projectId           projectId
     * @param environmentsService EnvironmentService
     * @return String with one filter
     */
    public static String chooseFilterType(
            TestDataTableFilter filter,
            UUID projectId,
            EnvironmentsService environmentsService) {
        String value = filter.getValues().get(0);
        String field = STATS_PREFIX + filter.getColumn();
        SearchConditionType conditionType = SearchConditionType.find(filter.getSearchCondition());
        switch (UsersOccupyFields.find(filter.getColumn())) {
            case SYSTEM:
                field = SYSTEM_FIELD;
                String systemSearchValue = value;
                List<LazySystem> lazySystemList = environmentsService
                        .getLazySystemsByProjectIdWithConnections(projectId)
                        .stream()
                        .filter(
                                system -> {
                                    if (SearchConditionType.CONTAINS.equals(conditionType)) {
                                        return containByCaseInStream(filter, system.getName(), systemSearchValue);
                                    } else if (SearchConditionType.START_WITH.equals(conditionType)) {
                                        return startWithByCaseInStream(filter, system.getName(), systemSearchValue);
                                    } else {
                                        return false;
                                    }
                                }).collect(Collectors.toList());
                if (CollectionUtils.isNotEmpty(lazySystemList)) {
                    value = lazySystemList.stream().map(LazySystem::getId)
                            .map(UUID::toString)
                            .collect(Collectors.joining("','", "'", "'"));
                    return String.format(IN_LIST_FILTER_TEMPLATE, field, value);
                } else {
                    return String.format(EMPTY_LIST_FILTER_TEMPLATE, field);
                }
            case ENVIRONMENT:
                String envSearchValue = value;
                field = ENVIRONMENT_FIELD;
                List<LazyEnvironment> lazyEnvironments = environmentsService.getLazyEnvironments(projectId)
                        .stream()
                        .filter(
                                environment -> {
                                    if (SearchConditionType.CONTAINS.equals(conditionType)) {
                                        return containByCaseInStream(filter, environment.getName(), envSearchValue);
                                    } else if (SearchConditionType.START_WITH.equals(conditionType)) {
                                        return startWithByCaseInStream(filter, environment.getName(), envSearchValue);
                                    } else {
                                        return false;
                                    }
                                }).collect(Collectors.toList());
                if (CollectionUtils.isNotEmpty(lazyEnvironments)) {
                    value = lazyEnvironments.stream()
                            .map(LazyEnvironment::getId)
                            .map(UUID::toString)
                            .collect(Collectors.joining("','", "'", "'"));
                    return String.format(IN_LIST_FILTER_TEMPLATE, field, value);
                } else {
                    return String.format(EMPTY_LIST_FILTER_TEMPLATE, field);
                }
            default:
                return databaseFiltering(filter, field, value);
        }
    }

    /**
     * Generate filter for fields from database.
     *
     * @param filter Filter
     * @param field  Field
     * @param value  Value
     * @return String of tiler
     */
    public static String databaseFiltering(TestDataTableFilter filter, String field, String value) {
        switch (SearchConditionType.find(filter.getSearchCondition())) {
            case CONTAINS:
                value = "'%" + value + "%'";
                break;
            case START_WITH:
                value = "'" + value + "%'";
                break;
            default:
                return "";
        }
        if (!filter.isCaseSensitive()) {
            field = String.format(UPPER_CASE_TEMPLATE, field);
            value = String.format(UPPER_CASE_TEMPLATE, value);
        }
        return String.format(FILTER_TEMPLATE, field, value);
    }

    /**
     * Compare values by "startWith" filter with case.
     *
     * @param filter      Filter
     * @param envName     Environment name
     * @param searchValue Search value
     * @return Comparing result
     */
    public static boolean startWithByCaseInStream(TestDataTableFilter filter, String envName, String searchValue) {
        if (filter.isCaseSensitive()) {
            return envName.startsWith(searchValue);
        } else {
            return envName.toLowerCase().startsWith(searchValue.toLowerCase());
        }
    }

    /**
     * Compare values by "contain" filter with case.
     *
     * @param filter      Filter
     * @param envName     Environment name
     * @param searchValue Search value
     * @return Comparing result
     */
    public static boolean containByCaseInStream(TestDataTableFilter filter, String envName, String searchValue) {
        if (filter.isCaseSensitive()) {
            return envName.contains(searchValue);
        } else {
            return envName.toLowerCase().contains(searchValue.toLowerCase());
        }
    }

    /**
     * Set order for users stats query.
     *
     * @param order Order type
     * @return String of order
     */
    public static String setOrderForUsersStats(TestDataTableOrder order) {
        if (order == null) {
            order = new TestDataTableOrder("occupied_by", OrderType.ASC);
        }
        return String.format(ORDER_BY_TEMPLATE, order.getColumnName(), order.getOrderType(OrderType.ASC).toString());
    }

    /**
     * Map objects from DB to OccupiedDataByUsersStatistics model.
     *
     * @param objects   list of objects
     * @param startDate start date
     * @return List of OccupiedDataByUsersStatistics
     */
    public static List<OccupiedDataByUsersStatistics> mapObjectsToEntity(List<Object[]> objects, LocalDate startDate) {
        List<OccupiedDataByUsersStatistics> occupiedDataByUsersStatistics = new ArrayList<>();
        for (Object[] object : objects) {
            OccupiedDataByUsersStatistics statistic = new OccupiedDataByUsersStatistics(
                    (String) object[0],
                    (String) object[2],
                    (String) object[1]);
            for (int j = 3; j < object.length; j++) {
                statistic.addData(startDate.plusDays(j - 3), ((BigDecimal) object[j]).longValue());
            }
            Map<LocalDate, Long> sortedMap = new LinkedHashMap<>();
            statistic.getData().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEachOrdered(x -> sortedMap.put(x.getKey(), x.getValue()));
            statistic.setData(sortedMap);
            occupiedDataByUsersStatistics.add(statistic);
        }
        return occupiedDataByUsersStatistics;
    }
}

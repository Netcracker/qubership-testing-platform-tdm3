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

package org.qubership.atp.tdm.repo.impl;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.qubership.atp.tdm.exceptions.internal.TdmStatisticsException;
import org.qubership.atp.tdm.model.TestDataOccupyStatistic;
import org.qubership.atp.tdm.model.TestDataTableCatalog;
import org.qubership.atp.tdm.model.statistics.ConsumedStatistics;
import org.qubership.atp.tdm.model.statistics.ConsumedStatisticsItem;
import org.qubership.atp.tdm.model.statistics.DateStatistics;
import org.qubership.atp.tdm.model.statistics.DateStatisticsItem;
import org.qubership.atp.tdm.model.statistics.GeneralStatisticsItem;
import org.qubership.atp.tdm.model.statistics.OutdatedStatistics;
import org.qubership.atp.tdm.model.statistics.OutdatedStatisticsInner;
import org.qubership.atp.tdm.model.statistics.OutdatedStatisticsItem;
import org.qubership.atp.tdm.model.statistics.StatisticsItem;
import org.qubership.atp.tdm.model.statistics.report.StatisticsReport;
import org.qubership.atp.tdm.repo.ProjectInformationRepository;
import org.qubership.atp.tdm.repo.StatisticsRepository;
import org.qubership.atp.tdm.repo.impl.extractors.ConsumedStatisticsExtractor;
import org.qubership.atp.tdm.repo.impl.extractors.GeneralStatisticsExtractor;
import org.qubership.atp.tdm.repo.impl.extractors.OutdatedStatisticsExtractor;
import org.qubership.atp.tdm.repo.impl.extractors.TestDataExtractorProvider;
import org.qubership.atp.tdm.utils.DataUtils;
import org.qubership.atp.tdm.utils.TestDataQueries;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Repository
public class StatisticsRepositoryImpl implements StatisticsRepository {

    private static final String NA = "N/A";

    private final JdbcTemplate jdbcTemplate;
    private final TestDataExtractorProvider extractorProvider;
    private final ProjectInformationRepository projectInformationRepository;

    /**
     * TestDataRepositoryImpl Constructor.
     */
    @Autowired
    public StatisticsRepositoryImpl(@Nonnull JdbcTemplate jdbcTemplate,
                                    @Nonnull TestDataExtractorProvider extractorProvider,
                                    @Nonnull ProjectInformationRepository projectInformationRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.extractorProvider = extractorProvider;
        this.projectInformationRepository = projectInformationRepository;
    }

    @Override
    public List<GeneralStatisticsItem> getTestDataAvailability(@Nonnull List<TestDataTableCatalog> catalogList,
                                                               @Nonnull UUID projectId) {
        List<GeneralStatisticsItem> listStatisticsItems = new ArrayList<>();
        String timeZone = getTimeZone(projectId);
        Map<String, String> timeStampsMap = DataUtils.generateTimeStampDailyRange(timeZone);
        catalogList.forEach(item -> {
            GeneralStatisticsItem statisticsItem = getGeneralStatisticsItem(item, timeStampsMap);
            if (Objects.nonNull(statisticsItem)) {
                UUID system = item.getSystemId();
                if (system != null) {
                    statisticsItem.setSystem(system.toString());
                }
                listStatisticsItems.add(statisticsItem);
            }
        });
        listStatisticsItems.sort(Comparator.comparing(GeneralStatisticsItem::getContext));
        return listStatisticsItems;
    }

    @Override
    public ConsumedStatistics getTestDataConsumption(@Nonnull List<TestDataOccupyStatistic> occupyStatisticList,
                                                     @Nonnull UUID projectId, @Nonnull LocalDate dateFrom,
                                                     @Nonnull LocalDate dateTo) {
        ConsumedStatistics consumedStatistics = new ConsumedStatistics();
        consumedStatistics.setDates(DataUtils.getStatisticsInterval(dateFrom, dateTo));
        List<ConsumedStatisticsItem> listStatisticsItems = new ArrayList<>();
        occupyStatisticList.forEach(occupyStatisticItem -> {
            ConsumedStatisticsItem statisticsItem = new ConsumedStatisticsItem(occupyStatisticItem.getTableTitle());
            ConsumedStatisticsExtractor extractor = extractorProvider.consumedStatisticsExtractor();
            List<Map<LocalDate, Long>> dbOutput = jdbcTemplate.query(TestDataQueries.GET_TEST_DATA_CONSUMPTION_ITEM,
                    extractor, occupyStatisticItem.getTableName().toLowerCase(),
                    dateFrom.toString(), dateTo.toString());
            List<Long> consumed = calculateStatistic(dbOutput, dateFrom, dateTo, statisticsItem, occupyStatisticItem);
            statisticsItem.setConsumed(consumed);
            listStatisticsItems.add(statisticsItem);
        });
        listStatisticsItems.sort(Comparator.comparing(ConsumedStatisticsItem::getContext));
        consumedStatistics.setItems(listStatisticsItems);
        return consumedStatistics;
    }

    private List<Long> calculateStatistic(List<Map<LocalDate, Long>> dbOutput, LocalDate dateFrom, LocalDate dateTo,
                                          StatisticsItem statisticsItem, TestDataOccupyStatistic occupyStatisticItem) {
        List<Long> consumed = new ArrayList<>();
        long count;
        switch (DataUtils.statisticsInterval) {
            case YEARS:
                do {
                    count = 0L;
                    for (Map<LocalDate, Long> outIt : dbOutput) {
                        for (Map.Entry<LocalDate, Long> entry : outIt.entrySet()) {
                            if (dateFrom.getYear() == entry.getKey().getYear()) {
                                count += entry.getValue();
                            }
                        }
                    }
                    consumed.add(count);
                    dateFrom = dateFrom.plusYears(1);
                } while (!dateFrom.isAfter(dateTo));
                break;
            case WEEKS:
                do {
                    count = 0L;
                    for (Map<LocalDate, Long> outIt : dbOutput) {
                        for (Map.Entry<LocalDate, Long> entry : outIt.entrySet()) {
                            if (dateFrom.getYear() == entry.getKey().getYear()
                                    && dateFrom.getMonth() == entry.getKey().getMonth()
                                    && (entry.getKey().isEqual(dateFrom) || entry.getKey().isAfter(dateFrom))
                                    && entry.getKey().isBefore(dateFrom.plusWeeks(1))) {
                                count += entry.getValue();
                            }
                        }
                    }
                    consumed.add(count);
                    dateFrom = dateFrom.plusWeeks(1);
                } while (!dateFrom.isAfter(dateTo));
                break;
            case DAYS:
                do {
                    count = 0L;
                    for (Map<LocalDate, Long> outIt : dbOutput) {
                        for (Map.Entry<LocalDate, Long> entry : outIt.entrySet()) {
                            if (dateFrom.getYear() == entry.getKey().getYear()
                                    && dateFrom.getMonth() == entry.getKey().getMonth()
                                    && dateFrom.getDayOfMonth() == entry.getKey().getDayOfMonth()) {
                                count += entry.getValue();
                            }
                        }
                    }
                    consumed.add(count);
                    dateFrom = dateFrom.plusDays(1);
                } while (!dateFrom.isAfter(dateTo));
                break;
            default:
                do {
                    count = 0L;
                    for (Map<LocalDate, Long> outIt : dbOutput) {
                        for (Map.Entry<LocalDate, Long> entry : outIt.entrySet()) {
                            if (dateFrom.getYear() == entry.getKey().getYear()
                                    && dateFrom.getMonth() == entry.getKey().getMonth()) {
                                count += entry.getValue();
                            }
                        }
                    }
                    consumed.add(count);
                    dateFrom = dateFrom.plusMonths(1);
                } while (!dateFrom.isAfter(dateTo));
                break;
        }
        UUID system = occupyStatisticItem.getSystemId();
        if (system != null) {
            statisticsItem.setSystem(system.toString());
        }

        return consumed;
    }

    @Override
    public OutdatedStatistics getTestDataOutdatedConsumption(@Nonnull List<TestDataTableCatalog> catalogList,
                                                             @Nonnull UUID projectId, @Nonnull LocalDate dateFrom,
                                                             @Nonnull LocalDate dateTo, int expirationDate) {
        OutdatedStatistics outdatedStatistics = new OutdatedStatistics();
        outdatedStatistics.setDates(DataUtils.getStatisticsInterval(dateFrom, dateTo));
        List<OutdatedStatisticsItem> listStatisticsItems = new ArrayList<>();
        catalogList.forEach(occupyStatisticItem -> {
            LocalDate iterDate = dateFrom;
            OutdatedStatisticsItem statisticsItem = new OutdatedStatisticsItem(occupyStatisticItem.getTableTitle());
            List<Long> created = new ArrayList<>();
            List<Long> consumed = new ArrayList<>();
            List<Long> outdated = new ArrayList<>();
            List<OutdatedStatisticsInner> dbOutput;

            try {
                OutdatedStatisticsExtractor extractor = extractorProvider.outdatedStatisticsExtractor();
                String query = String.format(TestDataQueries.GET_TEST_DATA_OUTDATED_ITEM,
                        occupyStatisticItem.getTableName().toLowerCase());
                dbOutput = jdbcTemplate.query(query, extractor, dateFrom.toString(), dateTo.toString(),
                        occupyStatisticItem.getTableName().toLowerCase(),
                        occupyStatisticItem.getTableName().toLowerCase(),
                        dateFrom.plusDays(expirationDate).toString());
            } catch (Exception e) {
                log.error(String.format(TdmStatisticsException.DEFAULT_MESSAGE,
                        occupyStatisticItem.getTableName()), e);
                throw new TdmStatisticsException(occupyStatisticItem.getTableName());
            }

            if (Objects.nonNull(dbOutput)) {
                long countCreated;
                long countConsumed;
                long countOutdated;
                switch (DataUtils.statisticsInterval) {
                    case YEARS:
                        do {
                            countCreated = 0L;
                            countConsumed = 0L;
                            countOutdated = 0L;
                            for (OutdatedStatisticsInner outdatedStatisticsItem : dbOutput) {
                                if (iterDate.getYear() == outdatedStatisticsItem.getDate().getYear()) {
                                    countCreated += outdatedStatisticsItem.getCreated();
                                    countConsumed += outdatedStatisticsItem.getConsumed();
                                    countOutdated += outdatedStatisticsItem.getOutdated();
                                }
                            }
                            created.add(countCreated);
                            consumed.add(countConsumed);
                            outdated.add(countOutdated);
                            iterDate = iterDate.plusYears(1);
                        } while (!iterDate.isAfter(dateTo));
                        break;
                    case WEEKS:
                        do {
                            countCreated = 0L;
                            countConsumed = 0L;
                            countOutdated = 0L;
                            for (OutdatedStatisticsInner outdatedStatisticsItem : dbOutput) {
                                if (iterDate.getYear() == outdatedStatisticsItem.getDate().getYear()
                                        && iterDate.getMonth() == outdatedStatisticsItem.getDate().getMonth()
                                        && (outdatedStatisticsItem.getDate().isEqual(iterDate)
                                        || outdatedStatisticsItem.getDate().isAfter(iterDate))
                                        && outdatedStatisticsItem.getDate().isBefore(iterDate.plusWeeks(1))) {
                                    countCreated += outdatedStatisticsItem.getCreated();
                                    countConsumed += outdatedStatisticsItem.getConsumed();
                                    countOutdated += outdatedStatisticsItem.getOutdated();
                                }
                            }
                            created.add(countCreated);
                            consumed.add(countConsumed);
                            outdated.add(countOutdated);
                            iterDate = iterDate.plusWeeks(1);
                        } while (!iterDate.isAfter(dateTo));
                        break;
                    case DAYS:
                        do {
                            countCreated = 0L;
                            countConsumed = 0L;
                            countOutdated = 0L;
                            for (OutdatedStatisticsInner outdatedStatisticsItem : dbOutput) {
                                if (iterDate.getYear() == outdatedStatisticsItem.getDate().getYear()
                                        && iterDate.getMonth() == outdatedStatisticsItem.getDate().getMonth()
                                        && iterDate.getDayOfMonth() == outdatedStatisticsItem.getDate()
                                        .getDayOfMonth()) {
                                    countCreated += outdatedStatisticsItem.getCreated();
                                    countConsumed += outdatedStatisticsItem.getConsumed();
                                    countOutdated += outdatedStatisticsItem.getOutdated();
                                }
                            }
                            created.add(countCreated);
                            consumed.add(countConsumed);
                            outdated.add(countOutdated);
                            iterDate = iterDate.plusDays(1);
                        } while (!iterDate.isAfter(dateTo));
                        break;
                    default:
                        do {
                            countCreated = 0L;
                            countConsumed = 0L;
                            countOutdated = 0L;
                            for (OutdatedStatisticsInner outdatedStatisticsItem : dbOutput) {
                                if (iterDate.getYear() == outdatedStatisticsItem.getDate().getYear()
                                        && iterDate.getMonth() == outdatedStatisticsItem.getDate().getMonth()) {
                                    countCreated += outdatedStatisticsItem.getCreated();
                                    countConsumed += outdatedStatisticsItem.getConsumed();
                                    countOutdated += outdatedStatisticsItem.getOutdated();
                                }
                            }
                            created.add(countCreated);
                            consumed.add(countConsumed);
                            outdated.add(countOutdated);
                            iterDate = iterDate.plusMonths(1);
                        } while (!iterDate.isAfter(dateTo));
                        break;
                }
                UUID system = occupyStatisticItem.getSystemId();
                if (system != null) {
                    statisticsItem.setSystem(system.toString());
                }
                statisticsItem.setCreated(created);
                statisticsItem.setConsumed(consumed);
                statisticsItem.setOutdated(outdated);
                listStatisticsItems.add(statisticsItem);
            } else {
                log.warn("Outdated data in table:[{}] not found.", occupyStatisticItem.getTableName());
            }
        });
        listStatisticsItems.sort(Comparator.comparing(OutdatedStatisticsItem::getContext));
        outdatedStatistics.setItems(listStatisticsItems);
        return outdatedStatistics;
    }

    @Override
    public DateStatistics getTestDataCreatedWhen(@Nonnull List<TestDataOccupyStatistic> occupyStatisticList,
                                                 @Nonnull UUID projectId, @Nonnull LocalDate dateFrom,
                                                 @Nonnull LocalDate dateTo) {
        DateStatistics dateStatistics = new DateStatistics();
        dateStatistics.setDates(DataUtils.getStatisticsInterval(dateFrom, dateTo));
        List<DateStatisticsItem> listStatisticsItems = new ArrayList<>();
        occupyStatisticList.forEach(occupyStatisticItem -> {
            DateStatisticsItem statisticsItem = new DateStatisticsItem(occupyStatisticItem.getTableTitle());
            ConsumedStatisticsExtractor extractor = extractorProvider.consumedStatisticsExtractor();
            List<Map<LocalDate, Long>> dbOutput = jdbcTemplate.query(TestDataQueries.GET_STATISTIC_CREATED_WHEN,
                    extractor, occupyStatisticItem.getTableName().toLowerCase(),
                    dateFrom.toString(), dateTo.toString());
            List<Long> consumed = calculateStatistic(dbOutput, dateFrom, dateTo, statisticsItem, occupyStatisticItem);
            statisticsItem.setCreated(consumed);
            listStatisticsItems.add(statisticsItem);
        });
        listStatisticsItems.sort(Comparator.comparing(DateStatisticsItem::getContext));
        dateStatistics.setItems(listStatisticsItems);
        return dateStatistics;
    }

    @Override
    public List<StatisticsReport> getTestDataMonitoringStatistics(@Nonnull List<TestDataTableCatalog> catalogList,
                                                                  @Nonnull UUID projectId) {
        List<StatisticsReport> statisticsReport = new ArrayList<>();
        String timeZone = getTimeZone(projectId);
        Map<String, String> timeStampsMap = DataUtils.generateTimeStampDailyRange(timeZone);
        catalogList.forEach(item -> {
            GeneralStatisticsItem statisticsItem = getGeneralStatisticsItem(item, timeStampsMap);
            String system = Objects.isNull(item.getSystemId()) ? NA : String.valueOf(item.getSystemId());
            statisticsReport.add(new StatisticsReport(NA, system, statisticsItem));
        });
        return statisticsReport;
    }

    @Override
    public List<String> alterOccupiedDateColumn(List<String> tableNames) {
        log.info("Adding missing column \"OCCUPIED_DATE\"");
        List<String> result = new ArrayList<>();
        for (String tableName : tableNames) {
            log.info("Processing table: " + tableName);
            jdbcTemplate.update(String.format(TestDataQueries.ALTER_OCCUPIED_DATE_COLUMN, tableName));
            int updated = jdbcTemplate.update(String.format(TestDataQueries.UPDATE_OCCUPIED_DATE, tableName));
            if (updated > 0) {
                String message = "OCCUPIED_DATE Column was updated in the table: " + tableName
                        + " . Affected rows: " + updated;
                log.info(message);
                result.add(message);
            }
        }
        log.info("Adding column insert finished.");
        return result;
    }

    private String getTimeZone(UUID projectId) {
        return projectInformationRepository
                .getProjectInformationTableByProjectId(projectId).getTimeZone();
    }

    private GeneralStatisticsItem getGeneralStatisticsItem(TestDataTableCatalog item, Map<String, String> map) {
        GeneralStatisticsExtractor extractor = extractorProvider.generalStatisticsExtractor(item.getTableTitle());
        GeneralStatisticsItem statisticsItem = jdbcTemplate.query(
                String.format(TestDataQueries.GET_TEST_DATA_AVAILABILITY_ITEM,
                        item.getTableName().toLowerCase(), item.getTableName().toLowerCase(),
                        item.getTableName().toLowerCase(), map.get("startTimeStamp"),
                        map.get("endTimeStamp"), item.getTableName().toLowerCase()), extractor);
        return statisticsItem;
    }
}

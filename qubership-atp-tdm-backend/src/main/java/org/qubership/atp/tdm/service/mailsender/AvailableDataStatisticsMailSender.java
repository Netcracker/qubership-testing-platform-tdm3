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

package org.qubership.atp.tdm.service.mailsender;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.qubership.atp.integration.configuration.model.MailRequest;
import org.qubership.atp.integration.configuration.service.MailSenderService;
import org.qubership.atp.tdm.env.configurator.service.EnvironmentsService;
import org.qubership.atp.tdm.exceptions.file.TdmMultipartFileException;
import org.qubership.atp.tdm.exceptions.internal.TdmGetImageFromHighchartException;
import org.qubership.atp.tdm.model.mail.charts.ChartSeries;
import org.qubership.atp.tdm.model.statistics.TestAvailableDataMonitoring;
import org.qubership.atp.tdm.model.statistics.available.AvailableDataByColumnStats;
import org.qubership.atp.tdm.model.statistics.available.TableAvailableDataStats;
import org.qubership.atp.tdm.service.StatisticsService;
import org.qubership.atp.tdm.service.client.HighchartsFeignClient;
import org.qubership.atp.tdm.utils.AvailableStatisticUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class AvailableDataStatisticsMailSender {

    private static final String IMAGE_TEMPLATE = "<img src=\"cid:%s\" />";
    private final String mailSenderFrom;
    private final HighchartsFeignClient highchartsFeignClient;
    private final Configuration configuration;
    private final StatisticsService statisticsService;
    private final MailSenderService mailSender;
    private final String mailSenderSubject;
    private final String mailSenderTemplate;
    private final String mailSenderPath;
    private final EnvironmentsService environmentsService;
    //    @SuppressWarnings("CPD-START") //got from application.properties
    private final String highchartJsonPath;
    //    @SuppressWarnings("CPD-START") //got from application.properties
    private final String highchartJsonTemplate;

    /**
     * AvailableDataStatisticsMailSender Constructor.
     */
    @Autowired
    private AvailableDataStatisticsMailSender(@Nonnull Configuration configuration,
                                              @Nonnull StatisticsService statisticsService,
                                              @Nonnull MailSenderService mailSender,
                                              @Nonnull EnvironmentsService environmentsService,
                                              @Value("${mail.sender.available.statistics.subject}")
                                              String mailSenderSubject,
                                              @Value("${mail.sender.from}") String mailSenderFrom,
                                              @Value("${mail.sender.available.statistics.template}")
                                              String mailSenderTemplate,
                                              @Value("${highcharts.template.path}") String highchartJsonTemplatePath,
                                              @Value("${mail.sender.available.statistics.path}") String mailSenderPath,
                                              @Value("${highcharts.template}") String highchartJsonTemplate,
                                              HighchartsFeignClient highchartsFeignClient) {
        this.configuration = configuration;
        this.statisticsService = statisticsService;
        this.mailSender = mailSender;
        this.mailSenderSubject = mailSenderSubject;
        this.mailSenderTemplate = mailSenderTemplate;
        this.mailSenderPath = mailSenderPath;
        this.environmentsService = environmentsService;
        this.highchartJsonPath = highchartJsonTemplatePath;
        this.highchartJsonTemplate = highchartJsonTemplate;
        this.highchartsFeignClient = highchartsFeignClient;
        this.mailSenderFrom = mailSenderFrom;
    }

    /**
     * Send email with available data stats.
     */
    public void send(UUID systemId, UUID environmentId) {
        log.info("Sending available data statistic for environmentId {} and systemId {}", environmentId, systemId);
        TestAvailableDataMonitoring monitoring =
                statisticsService.getAvailableDataMonitoringConfig(systemId, environmentId);
        log.trace("Monitoring config: {}", monitoring);

        String projectName = environmentsService.getLazyProjectById(environmentsService
                .getLazyEnvironment(environmentId)
                .getProjectId()).getName();

        MailRequest mailRequest = new MailRequest();
        mailRequest.setFrom(mailSenderFrom);
        mailRequest.setTo(monitoring.getRecipients());
        mailRequest.setSubject(String.format(mailSenderSubject, projectName));

        try {
            AvailableDataByColumnStats statistics = statisticsService.getAvailableDataInColumn(systemId, environmentId);
            log.trace("Statistic: {}", statistics);
            String environmentName = environmentsService.getEnvNameById(environmentId);
            List<MultipartFile> images = buildImages(statistics);
            String content = buildMessageContent(configuration, statistics, environmentName,
                    monitoring.getThreshold(), images.stream()
                            .map(image -> String.format(IMAGE_TEMPLATE, image.getOriginalFilename()))
                            .collect(Collectors.toList()));
            log.trace("Message content: {}", content);
            mailRequest.setContent(content);
            mailSender.sendWithInline(mailRequest, images);
        } catch (Exception e) {
            String content = e.getMessage();
            mailRequest.setContent(content);
            mailSender.send(mailRequest);
        }
    }

    private String buildMessageContent(Configuration freemarkerConfig, AvailableDataByColumnStats messageContent,
                                       String environmentName, int threshold, List<String> highcharts) {
        log.debug("Build message content: message content {}, environmentName {}, threshold {}, highcharts {}",
                messageContent, environmentName, threshold, highcharts);
        try {
            freemarkerConfig.setDirectoryForTemplateLoading(new File(mailSenderPath));
            Template template = freemarkerConfig.getTemplate(mailSenderTemplate);
            Writer writer = new StringWriter();
            template.process(new HashMap<String, Object>() {
                {
                    put("environment", environmentName);
                    put("statistic", messageContent);
                    put("threshold", threshold);
                    put("highcharts", highcharts);
                }
            }, writer);
            writer.flush();
            return writer.toString();
        } catch (IOException | TemplateException bmc) {
            String errorMsg = "Error observed while building message content for Test Data users monitoring. ";
            String content = errorMsg + bmc.getMessage();
            log.error(errorMsg, bmc);
            return content;
        }
    }

    private List<MultipartFile> buildImages(AvailableDataByColumnStats statistics) {
        log.debug("Building image for stats: {}", statistics);
        List<MultipartFile> images = new ArrayList<>();
        List<String> currentCategories = new ArrayList<>();
        List<ChartSeries> chartSeriesList = new ArrayList<>();
        int currentColumnCount = 0;
        Iterator<TableAvailableDataStats> statsIterator = statistics.getStatistics().iterator();
        while (statsIterator.hasNext()) {
            TableAvailableDataStats stats = statsIterator.next();
            if (currentColumnCount == 0) {
                currentCategories.addAll(stats.getOptions().keySet());
                currentColumnCount = stats.getOptions().size();
                ChartSeries series = new ChartSeries(
                        false,
                        new ArrayList<>(stats.getOptions().values()),
                        stats.getTableTitle(),
                        AvailableStatisticUtils.getColorByIndex(statistics.getStatistics().indexOf(stats))
                );
                chartSeriesList.add(series);
            } else if (currentColumnCount + stats.getOptions().size() < 30) {
                currentCategories.add(StringUtils.EMPTY);
                currentCategories.addAll(stats.getOptions().keySet());
                List<Integer> valueList = new ArrayList<>(Collections.nCopies(currentColumnCount + 1, 0));
                currentColumnCount += (stats.getOptions().size() + 1);
                valueList.addAll(stats.getOptions().values());
                ChartSeries series = new ChartSeries(false, valueList, stats.getTableTitle(),
                        AvailableStatisticUtils.getColorByIndex(statistics.getStatistics().indexOf(stats)));
                chartSeriesList.add(series);
            } else {
                try {
                    images.add(getImageFromHighchartService(currentCategories, chartSeriesList));
                } catch (Exception e) {
                    log.error("Cannot create MultipartFile for categories {} and chart series list {}",
                            currentCategories, chartSeriesList, e);
                    throw e;
                }
                currentCategories.clear();
                chartSeriesList.clear();
                currentCategories.addAll(stats.getOptions().keySet());
                currentColumnCount = stats.getOptions().size();
                ChartSeries series = new ChartSeries(false, new ArrayList<>(stats.getOptions().values()),
                        stats.getTableTitle(),
                        AvailableStatisticUtils.getColorByIndex(statistics.getStatistics().indexOf(stats)));
                chartSeriesList.add(series);
            }
            if (!statsIterator.hasNext()) {
                try {
                    images.add(getImageFromHighchartService(currentCategories, chartSeriesList));
                } catch (Exception e) {
                    log.error("Cannot create MultipartFile for categories {} and chart series list {}",
                            currentCategories, chartSeriesList, e);
                    throw e;
                }
            }
        }
        return images;
    }

    private MultipartFile getImageFromHighchartService(List<String> categories, List<ChartSeries> chartSeriesList) {
        log.debug("Getting image from highchart service for categories {} and chart series list {}", categories,
                chartSeriesList);

        ResponseEntity<Resource> response = highchartsFeignClient.create(
                AvailableStatisticUtils.buildHighChartConfigurationBody(
                        highchartJsonPath + highchartJsonTemplate, categories, chartSeriesList));

        if (!response.getStatusCode().is2xxSuccessful()) {
            String errorMessage = String.format("Status code isn't successfull: %s", response.getStatusCode().value());
            log.error(errorMessage);
            throw new TdmGetImageFromHighchartException(errorMessage);
        }
        String fileName = System.currentTimeMillis() + ".png";
        MockMultipartFile multipartFileToSend = null;

        try (InputStream stream = response.getBody().getInputStream()) {
            multipartFileToSend = new MockMultipartFile("application", fileName,
                    MediaType.APPLICATION_OCTET_STREAM.toString(), stream);
        } catch (Exception e) {
            log.error(String.format(TdmMultipartFileException.DEFAULT_MESSAGE, e.getMessage()), e);
            throw new TdmMultipartFileException(e.getMessage());
        }
        return multipartFileToSend;
    }
}

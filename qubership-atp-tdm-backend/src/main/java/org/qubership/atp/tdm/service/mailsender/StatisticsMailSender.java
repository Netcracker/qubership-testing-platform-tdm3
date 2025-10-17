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
import java.io.StringWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.qubership.atp.integration.configuration.model.MailRequest;
import org.qubership.atp.integration.configuration.service.MailSenderService;
import org.qubership.atp.tdm.env.configurator.model.LazyProject;
import org.qubership.atp.tdm.env.configurator.service.EnvironmentsService;
import org.qubership.atp.tdm.model.statistics.TestDataTableMonitoring;
import org.qubership.atp.tdm.model.statistics.report.StatisticsReportObject;
import org.qubership.atp.tdm.service.StatisticsService;
import org.qubership.atp.tdm.service.impl.MetricService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class StatisticsMailSender {

    private final String mailSenderFrom;
    private final Configuration configuration;
    private final StatisticsService statisticsService;
    private final MailSenderService mailSender;
    private final MetricService metricService;
    private final String mailSenderSubject;
    private final EnvironmentsService environmentsService;
    private final String mailSenderTemplate;
    private final String mailSenderPath;
    @Value("${mail.sender.enable:true}")
    private boolean mailSenderEnable;

    /**
     * StatisticsMailSender Constructor.
     */
    @Autowired
    private StatisticsMailSender(@Value("${mail.sender.from}") String mailSenderFrom,
                                 @Nonnull Configuration configuration,
                                 @Nonnull StatisticsService statisticsService,
                                 @Nonnull MailSenderService mailSender,
                                 @Nonnull MetricService metricService,
                                 @Nonnull EnvironmentsService environmentsService,
                                 @Value("${mail.sender.statistics.subject}") String mailSenderSubject,
                                 @Value("${mail.sender.statistics.template}") String mailSenderTemplate,
                                 @Value("${mail.sender.statistics.path}") String mailSenderPath) {
        this.mailSenderFrom = mailSenderFrom;
        this.configuration = configuration;
        this.statisticsService = statisticsService;
        this.mailSender = mailSender;
        this.metricService = metricService;
        this.mailSenderSubject = mailSenderSubject;
        this.mailSenderTemplate = mailSenderTemplate;
        this.mailSenderPath = mailSenderPath;
        this.environmentsService = environmentsService;
    }

    /**
     * Send e-mail.
     * @param projectId - search parameter
     */
    public void send(String projectId) {
        if (mailSenderEnable) {
            metricService.executeStatisticsJob(projectId);
            TestDataTableMonitoring monitoring = statisticsService.getMonitoringSchedule(UUID.fromString(projectId));
            MailRequest mailRequest = new MailRequest();
            mailRequest.setFrom(mailSenderFrom);
            mailRequest.setTo(monitoring.getRecipients());
            try {
                UUID projId = UUID.fromString(projectId);
                StatisticsReportObject statisticsReportObject = statisticsService
                        .getTestDataMonitoringStatistics(projId, monitoring.getThreshold());
                mailRequest.setSubject(String.format(mailSenderSubject, statisticsReportObject.getProjectName()));
                mailRequest.setContent(buildMessageContent(configuration, statisticsReportObject));
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("projectId", projId);
                mailRequest.setMetadata(metadata);
                mailSender.send(mailRequest);
            } catch (Exception e) {
                String projectName = projectId;
                try {
                    LazyProject lazyProjectById = environmentsService.getLazyProjectById(UUID.fromString(projectId));
                    projectName = lazyProjectById.getName();
                } catch (Exception er) {
                    log.error(String.format("Error while get project by id: %s", projectId));
                }
                mailRequest.setSubject(String.format(mailSenderSubject, projectName));
                String messageError = "Error statistics send mail. Message: " + e.getMessage();
                mailRequest.setContent(messageError);
                mailSender.send(mailRequest);
                throw e;
            }
        }
    }

    private String buildMessageContent(Configuration freemarkerConfig, StatisticsReportObject messageContent) {
        try {
            freemarkerConfig.setDirectoryForTemplateLoading(new File(mailSenderPath));
            Template template = freemarkerConfig.getTemplate(mailSenderTemplate);
            Writer writer = new StringWriter();
            template.process(new HashMap<String, Object>() {
                {
                    put("downToThreshold", messageContent.getDownToThreshold());
                    put("upToThreshold", messageContent.getUpToThreshold());
                }
            }, writer);
            writer.flush();
            return writer.toString();
        } catch (IOException | TemplateException bmc) {
            String errorMsg = "Error observed while building message content for Test Data monitoring.";
            log.error(errorMsg, bmc);
            return errorMsg;
        }
    }
}

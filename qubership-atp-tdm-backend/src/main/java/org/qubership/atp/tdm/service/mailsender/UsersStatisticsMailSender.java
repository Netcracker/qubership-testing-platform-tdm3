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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.qubership.atp.integration.configuration.model.MailRequest;
import org.qubership.atp.integration.configuration.service.MailSenderService;
import org.qubership.atp.tdm.env.configurator.service.EnvironmentsService;
import org.qubership.atp.tdm.model.statistics.TestDataTableUsersMonitoring;
import org.qubership.atp.tdm.model.statistics.report.UsersStatisticsReportObject;
import org.qubership.atp.tdm.service.StatisticsService;
import org.qubership.atp.tdm.service.impl.MetricService;
import org.qubership.atp.tdm.utils.DataUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
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
public class UsersStatisticsMailSender {

    private static final String EMPTY_HTML_CONTENT = "Statistic by Users.\n"
            + "Please, find CSV file with statistic in attachment";
    private final String mailSenderFrom;
    private final Configuration configuration;
    private final StatisticsService statisticsService;
    private final MailSenderService mailSender;
    private final MetricService metricService;
    private final EnvironmentsService environmentsService;
    private final String mailSenderSubject;
    private final String mailSenderTemplate;
    private final String mailSenderPath;
    @Value("${mail.sender.enable:true}")
    private boolean mailSenderEnable;

    /**
     * UsersStatisticsMailSender Constructor.
     */
    @Autowired
    private UsersStatisticsMailSender(@Value("${mail.sender.from}") String mailSenderFrom,
                                      @Nonnull Configuration configuration,
                                      @Nonnull StatisticsService statisticsService,
                                      @Nonnull MailSenderService mailSenderService,
                                      @Nonnull MetricService metricService,
                                      @Nonnull EnvironmentsService environmentsService,
                                      @Value("${mail.sender.users.statistics.subject}") String mailSenderSubject,
                                      @Value("${mail.sender.users.statistics.template}") String mailSenderTemplate,
                                      @Value("${mail.sender.users.statistics.path}") String mailSenderPath) {
        this.mailSenderFrom = mailSenderFrom;
        this.configuration = configuration;
        this.statisticsService = statisticsService;
        this.mailSender = mailSenderService;
        this.metricService = metricService;
        this.environmentsService = environmentsService;
        this.mailSenderSubject = mailSenderSubject;
        this.mailSenderTemplate = mailSenderTemplate;
        this.mailSenderPath = mailSenderPath;
    }

    /**
     * Send e-mail.
     * @param projectId - search parameter
     */
    public void send(String projectId) throws Exception {
        if (mailSenderEnable) {
            UUID projId = UUID.fromString(projectId);
            metricService.executeStatisticsUserJob(projectId);
            TestDataTableUsersMonitoring monitoring =
                    statisticsService.getUsersMonitoringSchedule(projId);

            MailRequest mailRequest = new MailRequest();
            mailRequest.setFrom(mailSenderFrom);
            mailRequest.setTo(monitoring.getRecipients());
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("projectId", projId);
            mailRequest.setMetadata(metadata);

            try {
                UsersStatisticsReportObject usersStatisticsReportObject =
                        statisticsService.getUsersStatisticsReport(monitoring);
                String content = EMPTY_HTML_CONTENT;
                if (monitoring.isHtmlReport()) {
                    content = buildMessageContent(configuration, usersStatisticsReportObject);
                }
                mailRequest.setSubject(String.format(mailSenderSubject, usersStatisticsReportObject.getProjectName()));
                mailRequest.setContent(content);
                if (monitoring.isCsvReport()) {
                    File attachment = statisticsService.getCsvReportByUsers(monitoring.getProjectId(),
                            monitoring.getDaysCount());
                    List<MultipartFile> multipartFiles = new ArrayList<>();

                    try (InputStream stream = new ByteArrayInputStream(Files.readAllBytes(attachment.toPath()))) {
                        MockMultipartFile multipartFileToSend = new MockMultipartFile("application",
                                attachment.getName(), MediaType.APPLICATION_OCTET_STREAM.toString(), stream);
                        multipartFiles.add(multipartFileToSend);
                        mailSender.send(mailRequest, multipartFiles);
                    } catch (IOException exc) {
                        log.error("Failed to attach files to message for {}", monitoring.getRecipients(), exc);
                        throw exc;
                    } finally {
                        DataUtils.deleteFile(attachment.toPath());
                    }
                } else {
                    mailSender.send(mailRequest);
                }
            } catch (Exception e) {
                String messageError = "Error user statistics send mail. Message: " + e.getMessage();
                String projectName = projectId;
                try {
                    projectName = environmentsService.getLazyProjectById(UUID.fromString(projectId)).getName();
                } catch (Exception ex) {
                    log.error("Project by Id not found.", ex);
                }
                mailRequest.setSubject(String.format(mailSenderSubject, "Project: " + projectName));
                mailRequest.setContent(messageError);
                mailSender.send(mailRequest);
                throw e;
            }
        }
    }

    private String buildMessageContent(Configuration freemarkerConfig, UsersStatisticsReportObject messageContent) {
        try {
            freemarkerConfig.setDirectoryForTemplateLoading(new File(mailSenderPath));
            Template template = freemarkerConfig.getTemplate(mailSenderTemplate);
            Writer writer = new StringWriter();
            template.process(new HashMap<String, Object>() {
                {
                    put("elements", messageContent.getElements());
                }
            }, writer);
            writer.flush();
            return writer.toString();
        } catch (IOException | TemplateException bmc) {
            String errorMsg = "Error observed while building message content for Test Data users monitoring.";
            log.error(errorMsg, bmc);
            return errorMsg;
        }
    }
}

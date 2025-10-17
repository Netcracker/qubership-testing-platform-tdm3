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

package org.qubership.atp.tdm.model.mail.bulkaction;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.qubership.atp.integration.configuration.model.MailRequest;
import org.qubership.atp.integration.configuration.service.MailSenderService;
import org.qubership.atp.tdm.model.bulkaction.BulkActionContext;
import org.qubership.atp.tdm.model.bulkaction.BulkActionResult;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AbstractBulkActionMailSender {

    Configuration configuration;
    MailSenderService mailSender;
    String mailSenderSubject;
    String mailSenderTemplate;
    String mailSenderPath;
    boolean mailSenderEnable;
    String mailSenderFrom;

    /**
     * Sends bulk action results via email.
     */
    public void send(@Nonnull BulkActionContext bulkActionContext, UUID projectId) {
        if (mailSenderEnable) {
            MailRequest mailRequest = new MailRequest();
            mailRequest.setFrom(mailSenderFrom);
            mailRequest.setSubject(String.format(mailSenderSubject, bulkActionContext.getId()));
            mailRequest.setTo(bulkActionContext.getRecipients());
            mailRequest.setContent(buildMessageContent(configuration, bulkActionContext.getProjectName(),
                    bulkActionContext.getEnvironmentName(), bulkActionContext.getSystemName(),
                    bulkActionContext.getResults()));
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("projectId", projectId);
            mailRequest.setMetadata(metadata);
            mailSender.send(mailRequest);
        }
    }

    private String buildMessageContent(@Nonnull Configuration freemarkerConfig, @Nonnull String projectName,
                                       @Nonnull String environmentName, @Nonnull String systemName,
                                       @Nonnull List<BulkActionResult> results) {
        try {
            freemarkerConfig.setDirectoryForTemplateLoading(new File(mailSenderPath));
            Template template = freemarkerConfig.getTemplate(mailSenderTemplate);
            Writer writer = new StringWriter();
            template.process(new HashMap<String, Object>() {
                {
                    put("projectName", projectName);
                    put("environmentName", environmentName);
                    put("systemName", systemName);
                    put("items", results);
                }
            }, writer);
            writer.flush();
            return writer.toString();
        } catch (IOException | TemplateException e) {
            String errorMsg = "Error observed while building message content for bulk action statistic";
            log.error(errorMsg, e);
            return errorMsg;
        }
    }
}

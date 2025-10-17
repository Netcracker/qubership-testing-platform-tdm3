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

import org.qubership.atp.integration.configuration.service.MailSenderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import freemarker.template.Configuration;
import jakarta.annotation.Nonnull;

@Component
public class BulkDropMailSender extends AbstractBulkActionMailSender {

    /**
     * BulkDropMailSender Constructor.
     */
    @Autowired
    private BulkDropMailSender(@Nonnull Configuration configuration,
                               @Nonnull MailSenderService mailSender,
                               @Value("${mail.sender.bulk.drop.subject}") String mailSenderSubject,
                               @Value("${mail.sender.bulk.drop.template}") String mailSenderTemplate,
                               @Value("${mail.sender.bulk.drop.path}") String mailSenderPath,
                               @Value("${mail.sender.enable:true}") boolean mailSenderEnable,
                               @Value("${mail.sender.from}") String mailSenderFrom) {
        this.configuration = configuration;
        this.mailSender = mailSender;
        this.mailSenderSubject = mailSenderSubject;
        this.mailSenderTemplate = mailSenderTemplate;
        this.mailSenderPath = mailSenderPath;
        this.mailSenderEnable = mailSenderEnable;
        this.mailSenderFrom = mailSenderFrom;
    }
}

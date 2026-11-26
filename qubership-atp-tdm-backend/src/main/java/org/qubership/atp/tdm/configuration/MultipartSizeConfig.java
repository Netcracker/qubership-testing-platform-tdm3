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

package org.qubership.atp.tdm.configuration;

import org.springframework.boot.autoconfigure.web.servlet.MultipartProperties;
import org.springframework.boot.web.embedded.undertow.UndertowServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.undertow.UndertowOptions;

/**
 * Applies the multipart request limit of Spring to Undertow.
 */
@Configuration
public class MultipartSizeConfig {

    /**
     * Sets the Undertow limit of a multipart body to {@code spring.servlet.multipart.max-request-size}.
     *
     * <p>Undertow limits a multipart body to 2 MiB by default, whatever the servlet allows, and Spring Boot sets no
     * value of its own.</p>
     *
     * @param multipart the multipart settings of Spring
     * @return the customizer of the web server
     */
    @Bean
    public WebServerFactoryCustomizer<UndertowServletWebServerFactory> multipartSizeCustomizer(
            MultipartProperties multipart) {
        return factory -> factory.addBuilderCustomizers(builder -> builder.setServerOption(
                UndertowOptions.MULTIPART_MAX_ENTITY_SIZE, multipart.getMaxRequestSize().toBytes()));
    }
}

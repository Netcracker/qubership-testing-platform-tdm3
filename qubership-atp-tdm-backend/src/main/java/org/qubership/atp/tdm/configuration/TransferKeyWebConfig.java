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

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Registers {@link TransferKeyFilter} for the database transfer endpoints.
 */
@Configuration
public class TransferKeyWebConfig {

    /** URL pattern of every database transfer endpoint, including the path itself without a trailing slash. */
    static final String TRANSFER_URL_PATTERN = "/api/tdm/database/*";

    /**
     * Registers the filter ahead of every other filter, so that no request to a transfer endpoint, including one that
     * matches no controller method, reaches another component without the key.
     *
     * @param key the configured transfer key, may be empty
     * @return the registration of the filter
     */
    @Bean
    public FilterRegistrationBean<TransferKeyFilter> transferKeyFilter(
            @Value("${tdm.db.transfer.key:}") String key) {
        FilterRegistrationBean<TransferKeyFilter> registration = new FilterRegistrationBean<>(
                new TransferKeyFilter(key));
        registration.addUrlPatterns(TRANSFER_URL_PATTERN);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}

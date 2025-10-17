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

import java.util.HashMap;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import liquibase.integration.spring.SpringLiquibase;
import lombok.Getter;

@Getter
@Configuration
@EnableConfigurationProperties(LiquibaseProperties.class)
public class SpringLiquibaseConfig {

    private DataSource dataSource;
    private LiquibaseProperties properties;

    @Value("${spring.application.name}")
    private String applicationName;
    @Value("${service.entities.migration.enabled:false}")
    private String serviceEntitiesMigrationEnabled;


    public SpringLiquibaseConfig(DataSource dataSource, LiquibaseProperties properties) {
        this.dataSource = dataSource;
        this.properties = properties;
    }

    /**
     * Create spring liquibase config.
     */
    @Bean("liquibase")
    public SpringLiquibase liquibase() {
        SpringLiquibase liquibase = new BeanAwareSpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog(this.properties.getChangeLog());
        liquibase.setContexts(this.properties.getContexts());
        liquibase.setDefaultSchema(this.properties.getDefaultSchema());
        liquibase.setDropFirst(this.properties.isDropFirst());
        liquibase.setShouldRun(this.properties.isEnabled());
        liquibase.setLabels(this.properties.getLabelFilter());
        Map<String, String> parameters = this.properties.getParameters();
        if (null == parameters) {
            parameters = new HashMap<>();
        }
        parameters.put("spring.application.name", applicationName);
        parameters.put("service.entities.migration.enabled", serviceEntitiesMigrationEnabled);
        liquibase.setChangeLogParameters(parameters);
        liquibase.setRollbackFile(this.properties.getRollbackFile());
        return liquibase;
    }
}

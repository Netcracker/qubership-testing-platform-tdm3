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

import java.io.IOException;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.spi.JobFactory;
import org.qubership.atp.tdm.utils.scheduler.AutowiringSpringBeanJobFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;

import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;

@Configuration
@Slf4j
public class QuartzConfig {

    private final ApplicationContext applicationContext;
    private final Properties props;

    /**
     * Builds Quartz config object to interact with a scheduler.
     */
    @Autowired
    public QuartzConfig(@Qualifier(AtpWebConfig.APP_PROPERTIES) Properties props,
                        ApplicationContext applicationContext) {
        this.props = new Properties();
        this.props.putAll(props.entrySet().stream()
                .filter(e -> e.getKey().toString().startsWith("org.quartz"))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
        this.applicationContext = applicationContext;
    }


    /**
     * Returns a JobFactory based on applicationContext.
     *
     * @return A JobFactory instance
     */
    @Bean
    public JobFactory jobFactory() {
        AutowiringSpringBeanJobFactory jobFactory = new AutowiringSpringBeanJobFactory();
        jobFactory.setApplicationContext(applicationContext);
        return jobFactory;
    }

    /**
     * Returns a scheduler based on properties from quartz.properties file.
     *
     * @return A scheduler instance or <tt>null</tt> if scheduler has been turned off
     */
    @Nullable
    @Bean
    public Scheduler scheduler(SchedulerFactoryBean schedulerFactoryBean) {
        Scheduler scheduler = schedulerFactoryBean != null ? schedulerFactoryBean.getScheduler() : null;
        if (scheduler != null) {
            try {
                scheduler.start();
            } catch (SchedulerException e) {
                log.error("Error while occurred starting scheduler", e);
            }
        }
        return scheduler;
    }

    /**
     * Returns a SchedulerFactoryBean based on JobFactory and DataSource.
     *
     * @return A SchedulerFactoryBean instance
     */
    @Bean
    public SchedulerFactoryBean schedulerFactoryBean(DataSource dataSource, JobFactory jobFactory)
            throws IOException {
        SchedulerFactoryBean factory = new SchedulerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setJobFactory(jobFactory);
        factory.setQuartzProperties(props);
        return factory;
    }
}

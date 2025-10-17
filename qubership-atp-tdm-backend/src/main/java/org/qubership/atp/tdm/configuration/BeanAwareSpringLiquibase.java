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

import org.springframework.context.ApplicationContext;
import org.springframework.core.io.ResourceLoader;

import liquibase.integration.spring.SpringLiquibase;

public class BeanAwareSpringLiquibase extends SpringLiquibase {

    private static ResourceLoader applicationContext;

    public BeanAwareSpringLiquibase() {
    }

    /**
     * Static method for get beans in liquibase task changes.
     */
    public static final <T> T getBean(Class<T> beanClass) throws Exception {
        if (ApplicationContext.class.isInstance(applicationContext)) {
            return ((ApplicationContext) applicationContext).getBean(beanClass);
        } else {
            throw new Exception("Resource loader is not an instance of ApplicationContext");
        }
    }

    @Override
    public void setResourceLoader(ResourceLoader resourceLoader) {
        super.setResourceLoader(resourceLoader);
        applicationContext = resourceLoader;
    }
}

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

package org.qubership.atp.tdm.migration.v2;

import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collectors;

import org.qubership.atp.auth.springbootstarter.entities.ServiceEntities;
import org.qubership.atp.auth.springbootstarter.services.UsersService;
import org.qubership.atp.tdm.configuration.BeanAwareSpringLiquibase;
import org.qubership.atp.tdm.utils.UsersManagementEntities;

import liquibase.change.custom.CustomTaskChange;
import liquibase.database.Database;
import liquibase.exception.SetupException;
import liquibase.exception.ValidationErrors;
import liquibase.resource.ResourceAccessor;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;

public class ServiceEntitiesMigrationCustomChange implements CustomTaskChange {

    private static final UUID SERVICE_ENTITIES_ID = UUID.fromString("c8ff5598-dd0f-4d18-9cda-a8ddacf4953b");
    @Getter
    @Setter
    private String serviceName;

    @SneakyThrows
    @Override
    public void execute(Database database) {
        ServiceEntities entities = new ServiceEntities();
        entities.setUuid(SERVICE_ENTITIES_ID);
        entities.setService(serviceName);
        entities.setEntities(Arrays.stream(UsersManagementEntities.values())
                .map(UsersManagementEntities::getName)
                .collect(Collectors.toList()));

        UsersService usersService = BeanAwareSpringLiquibase.getBean(UsersService.class);
        usersService.sendEntities(entities);
    }

    @Override
    public String getConfirmationMessage() {
        return null;
    }

    @Override
    public void setUp() throws SetupException {

    }

    @Override
    public void setFileOpener(ResourceAccessor resourceAccessor) {

    }

    @Override
    public ValidationErrors validate(Database database) {
        return null;
    }
}

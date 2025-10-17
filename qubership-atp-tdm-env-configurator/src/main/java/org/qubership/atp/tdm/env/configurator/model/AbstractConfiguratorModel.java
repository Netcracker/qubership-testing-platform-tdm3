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

package org.qubership.atp.tdm.env.configurator.model;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.function.Predicate;

import org.qubership.atp.tdm.env.configurator.utils.Utils;

import lombok.Data;

@Data
public class AbstractConfiguratorModel {

    private UUID id;
    private String name;
    private String description;
    private String created;
    private String createdBy;
    private String modified;
    private String modifiedBy;

    protected <T extends AbstractConfiguratorModel> T getByName(List<T> in, String name) throws NoSuchElementException {
        return getBy(in, object -> name.equals(object.getName()));
    }

    protected <T extends AbstractConfiguratorModel> T getById(List<T> in, UUID id) throws NoSuchElementException {
        return getBy(in, object -> id.equals(object.getId()));
    }

    protected <T extends AbstractConfiguratorModel> T getBy(List<T> in, Predicate<AbstractConfiguratorModel> predicate)
            throws NoSuchElementException {
        return Utils.collectionWithoutNull(in)
                .stream()
                .filter(predicate)
                .findAny()
                .orElseThrow(() -> {
                    String message = "Not found object with :[%s].";
                    return new NoSuchElementException(String.format(message, id));
                });
    }
}

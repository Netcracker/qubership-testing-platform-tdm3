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

package org.qubership.atp.tdm.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import jakarta.persistence.AttributeConverter;

public class TableColumnValuesConverter implements AttributeConverter<List<String>, String> {

    private static final String DELIMETER = ";";

    @Override
    public String convertToDatabaseColumn(List<String> strings) {
        return String.join(DELIMETER,strings);
    }

    @Override
    public List<String> convertToEntityAttribute(String s) {
        return new ArrayList<>(Arrays.asList(s.split(DELIMETER)));
    }
}

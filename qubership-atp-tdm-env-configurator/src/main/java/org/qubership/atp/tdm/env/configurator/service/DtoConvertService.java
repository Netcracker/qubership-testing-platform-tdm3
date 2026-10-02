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

package org.qubership.atp.tdm.env.configurator.service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Maps a model class to another by matching field names and types, through {@link ModelMapper}.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DtoConvertService {

    protected final ModelMapper modelMapper;

    /**
     * Maps {@code from} to a new instance of {@code to}.
     */
    public <T> T convert(Object from, Class<T> to) {
        return modelMapper.map(from, to);
    }

    /**
     * Maps every element of {@code from} to {@code to}, or returns an empty list if {@code from} is {@code null}.
     */
    public <T> List<T> convertList(List from, Class<T> to) {
        if (from == null) {
            return new ArrayList<>();
        }
        return (List) from.stream().map(o -> convert(o, to)).collect(Collectors.toList());
    }
}

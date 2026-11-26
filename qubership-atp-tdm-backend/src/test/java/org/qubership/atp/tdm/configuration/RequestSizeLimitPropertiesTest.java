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

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.util.unit.DataSize;

/**
 * Fails when {@code server.undertow.max-http-post-size} does not exceed an upload limit of the service, with the
 * default values of {@code application.properties}. Undertow checks its own limit before any other and rejects a
 * larger request with an empty body.
 */
class RequestSizeLimitPropertiesTest {

    private static DataSize defaultOf(String property) throws IOException {
        MutablePropertySources sources = new MutablePropertySources();
        sources.addLast(new PropertiesPropertySource("application", PropertiesLoaderUtils.loadProperties(
                new FileSystemResource("src/main/config/application.properties"))));
        return DataSize.parse(new PropertySourcesPropertyResolver(sources).getRequiredProperty(property));
    }

    @ParameterizedTest
    @ValueSource(strings = {"spring.servlet.multipart.max-request-size", "tdm.db.transfer.max-size"})
    void serverLimit_exceedsTheUploadLimit(String uploadLimit) throws IOException {
        DataSize server = defaultOf("server.undertow.max-http-post-size");
        DataSize upload = defaultOf(uploadLimit);

        assertTrue(server.compareTo(upload) > 0,
                "server.undertow.max-http-post-size is " + server + ", and " + uploadLimit + " is " + upload);
    }
}

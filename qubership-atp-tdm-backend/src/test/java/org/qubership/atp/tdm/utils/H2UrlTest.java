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

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class H2UrlTest {

    @ParameterizedTest
    @ValueSource(strings = {
        "jdbc:h2:file:./database/atptdm;DB_CLOSE_ON_EXIT=FALSE",
        "jdbc:h2:file:./database/atp3-tdm-be;MODE=PostgreSQL",
        "jdbc:h2:./database/atptdm",
        "jdbc:h2:~/atptdm",
        "jdbc:h2:atptdm"})
    void isFileDatabase_fileUrl_returnsTrue(String url) {
        assertEquals(true, H2Url.isFileDatabase(url), url);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {
        "jdbc:h2:mem:test",
        "jdbc:h2:mem:",
        "jdbc:h2:tcp://localhost/~/test",
        "jdbc:h2:ssl://localhost/~/test",
        "jdbc:h2:zip:~/db.zip!/test",
        "jdbc:postgresql://localhost/test"})
    void isFileDatabase_notFileUrl_returnsFalse(String url) {
        assertEquals(false, H2Url.isFileDatabase(url), String.valueOf(url));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "jdbc:h2:file:./database/atptdm;MODE=PostgreSQL",
        "jdbc:h2:./database/atptdm",
        "jdbc:h2:file:C:\\data\\atptdm",
        "jdbc:h2:atptdm"})
    void databaseName_fileUrl_returnsLastPathSegment(String url) {
        assertEquals("atptdm", H2Url.databaseName(url), url);
    }
}

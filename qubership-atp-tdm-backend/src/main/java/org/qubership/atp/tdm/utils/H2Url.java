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

import java.util.List;

/**
 * Reads the parts of an H2 JDBC URL that the database transfer needs.
 */
public final class H2Url {

    private static final String PREFIX = "jdbc:h2:";
    private static final String FILE_PREFIX = "file:";
    private static final List<String> NON_FILE_PREFIXES = List.of("mem:", "tcp:", "ssl:", "zip:");

    private H2Url() {
    }

    /**
     * Tells whether the URL opens a database that is stored in files on the local file system.
     *
     * <p>A name without a prefix, such as {@code jdbc:h2:./database/atptdm}, is a file database in H2.</p>
     *
     * @param url a JDBC URL, may be {@code null}
     * @return {@code false} for an in-memory, remote, or zip database, and for a URL that is not an H2 URL
     */
    public static boolean isFileDatabase(String url) {
        if (url == null || !url.startsWith(PREFIX)) {
            return false;
        }
        String location = location(url);
        if (location.isEmpty()) {
            return false;
        }
        return NON_FILE_PREFIXES.stream().noneMatch(location::startsWith);
    }

    /**
     * Returns the last path segment of the database location without the settings that follow {@code ;}.
     *
     * @param url a JDBC URL of an H2 file database
     * @return the name of the database, for example {@code atptdm} for {@code jdbc:h2:file:./database/atptdm;MODE=...}
     */
    public static String databaseName(String url) {
        String location = location(url);
        if (location.startsWith(FILE_PREFIX)) {
            location = location.substring(FILE_PREFIX.length());
        }
        int separator = Math.max(location.lastIndexOf('/'), location.lastIndexOf('\\'));
        return location.substring(separator + 1);
    }

    private static String location(String url) {
        String location = url.substring(PREFIX.length());
        int settings = location.indexOf(';');
        return settings < 0 ? location : location.substring(0, settings);
    }
}

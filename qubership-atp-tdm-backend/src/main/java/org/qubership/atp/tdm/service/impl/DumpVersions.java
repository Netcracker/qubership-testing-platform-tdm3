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

package org.qubership.atp.tdm.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.qubership.atp.tdm.exceptions.transfer.DatabaseTransferException;
import org.springframework.http.HttpStatus;

/**
 * Decides whether a service accepts a dump of another version.
 */
public final class DumpVersions {

    /** The placeholder version of a development run. It is not a release. */
    static final String LOCAL_VERSION = "0.0.1.LOCAL";

    private static final Pattern VERSION = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)(?:[.\\-+].*)?");

    private DumpVersions() {
    }

    /**
     * Applies the version rule of the transfer.
     *
     * <p>A qualifier such as {@code -SNAPSHOT} is ignored. A dump from a newer version is rejected, and a dump from
     * an older major version is accepted with a warning. A service on {@value #LOCAL_VERSION} counts as newer than
     * every other version.</p>
     *
     * @param dumpVersion    the version in the dump header
     * @param serviceVersion the version of the running service
     * @return the warnings to record, which are empty when the versions raise no concern
     * @throws DatabaseTransferException with status {@code 400} when the dump version is not a version or is newer
     *                                   than the service
     */
    public static List<String> check(String dumpVersion, String serviceVersion) {
        List<String> warnings = new ArrayList<>();
        int[] dump = parse(dumpVersion);
        if (dump == null) {
            throw new DatabaseTransferException(HttpStatus.BAD_REQUEST,
                    "The dump header has no valid service version: '" + dumpVersion + "'.");
        }
        int[] service = parse(serviceVersion);
        if (service == null) {
            warnings.add("The version of this service is '" + serviceVersion + "', so the version of the dump "
                    + dumpVersion + " was not compared with it.");
            return warnings;
        }
        boolean serviceIsLocal = LOCAL_VERSION.equalsIgnoreCase(serviceVersion);
        boolean dumpIsLocal = LOCAL_VERSION.equalsIgnoreCase(dumpVersion);
        if (serviceIsLocal) {
            if (!dumpIsLocal) {
                warnings.add("The dump comes from " + dumpVersion + " and this service runs " + serviceVersion
                        + ", a development version that counts as newer than any release.");
            }
            return warnings;
        }
        if (compare(dump, service) > 0) {
            throw new DatabaseTransferException(HttpStatus.BAD_REQUEST, "The dump comes from " + dumpVersion
                    + ", which is newer than this service (" + serviceVersion + "). Downgrades are not supported.");
        }
        if (dump[0] < service[0]) {
            warnings.add("The dump comes from " + dumpVersion + ", an older major version than this service ("
                    + serviceVersion + ").");
        }
        return warnings;
    }

    private static int[] parse(String version) {
        if (version == null) {
            return null;
        }
        Matcher matcher = VERSION.matcher(version);
        if (!matcher.matches()) {
            return null;
        }
        try {
            return new int[] {Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)),
                    Integer.parseInt(matcher.group(3))};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int compare(int[] left, int[] right) {
        for (int i = 0; i < left.length; i++) {
            if (left[i] != right[i]) {
                return Integer.compare(left[i], right[i]);
            }
        }
        return 0;
    }
}

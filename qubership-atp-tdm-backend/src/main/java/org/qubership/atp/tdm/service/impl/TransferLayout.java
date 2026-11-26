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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

import org.qubership.atp.tdm.utils.H2Url;

/**
 * The paths that the database transfer uses: the files of the database and the staging directory next to them.
 */
public final class TransferLayout {

    /** Extensions of the files that an H2 file database keeps, after the database name. */
    private static final List<String> DATABASE_EXTENSIONS = List.of(".mv.db", ".trace.db");

    private final Path databaseBase;
    private final Path stagingDir;

    private TransferLayout(Path databaseBase, Path stagingDir) {
        this.databaseBase = databaseBase;
        this.stagingDir = stagingDir;
    }

    /**
     * Derives the layout from the JDBC URL of an H2 file database.
     *
     * @param jdbcUrl    the URL of the database, which {@link H2Url#isFileDatabase(String)} accepts
     * @param stagingDir the configured staging directory, or an empty value for {@code transfer} next to the
     *                   database files
     * @return the layout
     */
    public static TransferLayout of(String jdbcUrl, String stagingDir) {
        Path base = H2Url.databasePath(jdbcUrl);
        Path staging = stagingDir == null || stagingDir.isBlank()
                ? base.resolveSibling("transfer")
                : Paths.get(stagingDir).toAbsolutePath().normalize();
        return new TransferLayout(base, staging);
    }

    /** The directory that holds the database files. */
    public Path databaseDir() {
        return databaseBase.getParent();
    }

    /** The files of the database that exist now. */
    public List<Path> databaseFiles() {
        return allDatabaseFiles().filter(path -> path.toFile().exists()).toList();
    }

    private Stream<Path> allDatabaseFiles() {
        return DATABASE_EXTENSIONS.stream()
                .map(extension -> databaseBase.resolveSibling(databaseBase.getFileName() + extension));
    }

    /** The staging directory. */
    public Path stagingDir() {
        return stagingDir;
    }

    /** The upload in progress. */
    public Path incoming() {
        return stagingDir.resolve("incoming.sql.gz.part");
    }

    /** The staged or approved dump. */
    public Path pending() {
        return stagingDir.resolve("pending.sql.gz");
    }

    /** The file that records the state of the import. */
    public Path statusFile() {
        return stagingDir.resolve("status.json");
    }

    /** The directory that holds one subdirectory for each replaced database. */
    public Path backupRoot() {
        return stagingDir.resolve("backup");
    }

    /** The directory that holds the dump of the last failed restore. */
    public Path failedDir() {
        return stagingDir.resolve("failed");
    }
}

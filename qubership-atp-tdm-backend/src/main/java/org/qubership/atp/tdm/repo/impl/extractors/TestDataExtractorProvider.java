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

package org.qubership.atp.tdm.repo.impl.extractors;

import org.qubership.atp.tdm.model.ExportFileType;
import org.qubership.atp.tdm.model.table.TestDataTableOrder;
import org.qubership.atp.tdm.model.table.TestDataType;
import org.qubership.atp.tdm.service.ColumnService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.AllArgsConstructor;

@Component
@AllArgsConstructor
public class TestDataExtractorProvider {

    private final JdbcTemplate jdbcTemplate;
    private final ColumnService columnService;

    public TestDataTableExtractor simpleExtractor(@Nonnull String tableName, @Nonnull String countQuery) {
        return new TestDataTableExtractor(columnService, jdbcTemplate, tableName, countQuery);
    }

    public TestDataTableExtractor simpleExtractor(@Nonnull String tableName, @Nonnull String countQuery,
                                                  @Nonnull TestDataType testDataType,
                                                  @Nullable TestDataTableOrder testDataTableOrder) {
        return new TestDataTableExtractor(columnService, jdbcTemplate, tableName, countQuery, testDataType,
                testDataTableOrder);
    }

    public TestDataTableMultipleExtractor multipleExtractor(@Nonnull String tableName,
                                                            @Nonnull TestDataType testDataType) {
        return new TestDataTableMultipleExtractor(columnService, tableName, testDataType);
    }

    public TestDataTableAsFileExtractor fileExtractor(@Nonnull String tableName, ExportFileType exportFileType) {
        return new TestDataTableAsFileExtractor(columnService, tableName, exportFileType);
    }

    public GeneralStatisticsExtractor generalStatisticsExtractor(@Nonnull String tableTitle) {
        return new GeneralStatisticsExtractor(tableTitle);
    }

    public OutdatedStatisticsExtractor outdatedStatisticsExtractor() {
        return new OutdatedStatisticsExtractor();
    }

    public ConsumedStatisticsExtractor consumedStatisticsExtractor() {
        return new ConsumedStatisticsExtractor();
    }
}

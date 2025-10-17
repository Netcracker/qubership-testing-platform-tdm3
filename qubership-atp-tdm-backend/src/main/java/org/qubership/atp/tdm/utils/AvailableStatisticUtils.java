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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.qubership.atp.tdm.model.mail.charts.ChartSeries;
import org.qubership.atp.tdm.model.table.TableColumnValues;

import com.google.gson.Gson;

public class AvailableStatisticUtils {

    private static final int CATEGORY_COEF = 20;

    private static final List<String> colors = new ArrayList<>(Arrays.asList("#09A4F1",
            "#00BB5B",
            "#FFB02E",
            "#6F0AAA",
            "#CB0077",
            "#FFFD00",
            "#5FD2B5",
            "#0C5AA6",
            "#A64600",
            "#F26D93"));

    public static String getColorByIndex(int index) {
        return colors.get(index % colors.size());
    }

    /**
     * Build High Chart Configuration Body.
     */
    public static String buildHighChartConfigurationBody(String pathToTemplate, List<String> categories,
                                                         List<ChartSeries> chartSeriesList) {
        String jsonString;
        try {
            categories = categories.stream().map(string -> string.replace("\"", "\\\"")).collect(Collectors.toList());
            File file = new File(pathToTemplate);
            String canonical = file.getCanonicalPath();
            jsonString = StringUtils.join(Files.readAllLines(Paths.get(canonical)), "");
            jsonString = String.format(jsonString,
                    categories.size() * CATEGORY_COEF + 100,
                    StringUtils.join(categories, "\",\""),
                    new Gson().toJson(chartSeriesList));
        } catch (IOException e) {
            throw new RuntimeException("Wrong highchart configuration: " + e.getMessage(), e);
        }
        return jsonString;
    }

    /**
     * Available Data Query.
     */
    public static String availableDataQuery(TableColumnValues columnValues, String activeColumn) {
        return String.format(TestDataQueries.GET_AVAILABLE_DATA_FOR_EACH_VALUE,
                activeColumn,
                columnValues.getTableName(),
                activeColumn,
                String.join("','", columnValues.getValues()),
                activeColumn);
    }
}

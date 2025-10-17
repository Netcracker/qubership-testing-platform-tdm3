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

package org.qubership.atp.tdm;

import static org.apache.poi.ss.usermodel.Row.MissingCellPolicy.CREATE_NULL_AS_BLANK;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public class ExcelRowsReader {

    private static final int SHEET_NUMBER = 0;

    public static <T> Stream<List<String>> read(File file) {
        try (InputStream inp = new BufferedInputStream(new FileInputStream(file))) {
            try (XSSFWorkbook wb = new XSSFWorkbook(inp)) {
                XSSFSheet sheet = wb.getSheetAt(SHEET_NUMBER);
                Stream<Row> stream = StreamSupport.stream(sheet.spliterator(), false);
                return stream.map(rowsMapper());
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Problems processing excel file: " + file.getName(), ex);
        }
    }

    private static Function<Row, List<String>> rowsMapper() {
        return (row) -> {
            List<String> list = new ArrayList<>();
            for (int i = 0; i < row.getPhysicalNumberOfCells(); ++i) {
                list.add(row.getCell(i, CREATE_NULL_AS_BLANK).toString());
            }
            return list;
        };
    }
}

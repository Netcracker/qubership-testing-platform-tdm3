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
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.qubership.atp.tdm.exceptions.file.TdmBuildCvsFileException;
import org.qubership.atp.tdm.model.table.column.TestDataTableColumn;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TestDataTableConvertor {

    private static final String TEST_TABLE_NAME_PREFIX = "TDM_";
    private static final String EXCEL_EXT = ".xlsx";
    private static final String CSV_EXT = ".csv";
    private static final int NUM_OF_BODY_ROW = 2;

    private static final int BATCH_SIZE = 200;

    public static String generateTestDataTableName() {
        return TEST_TABLE_NAME_PREFIX + UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * Create an .xlsx file from given table.
     *
     * @return fn .xlsx file
     */
    public static File convertTableToExcelFile(String tableName, List<TestDataTableColumn> columns,
                                               ResultSet resultSet) throws IOException, SQLException {
        File file = new File(Files.createTempFile(tableName, EXCEL_EXT).toString());
        try (FileOutputStream outputStream = new FileOutputStream(file); SXSSFWorkbook workbook = new SXSSFWorkbook()) {
            SXSSFSheet sheet = workbook.createSheet();
            sheet.setRandomAccessWindowSize(100);
            while (resultSet.next()) {
                int rowCount = resultSet.getRow();
                if (rowCount - 1 == 0) {
                    Row row = sheet.createRow(rowCount - 1);
                    formExcelRow(rowCount - 1, columns, row, resultSet);
                }
                Row row = sheet.createRow(rowCount);
                formExcelRow(rowCount + NUM_OF_BODY_ROW, columns, row, resultSet);
            }
            workbook.write(outputStream);
        } finally {
            file.deleteOnExit();
        }
        return file;
    }

    /**
     * Method for forming excel row.
     *
     * @param columns   - list of columns.
     * @param row       - row from sheet to work.
     * @param resultSet - resultSet for SQL queries.
     * @throws SQLException - sql exception.
     */
    private static void formExcelRow(int rowCount, List<TestDataTableColumn> columns, Row row,
                                     ResultSet resultSet) throws SQLException {
        for (int colCount = 1; colCount < columns.size(); colCount++) {
            //colCount-1 or empty column will be in the start of the file
            Cell cell = row.createCell(colCount - 1);
            Consumer<String> consumer = cell::setCellValue;
            String columnName = columns.get(colCount).getIdentity().getColumnName();
            setCellsForExcelFile(rowCount, columnName, resultSet, consumer);
        }
    }

    /**
     * Create an .csv file from given table.
     *
     * @return fn .csv file
     */
    public static File convertTableToCsvFile(String tableName, List<TestDataTableColumn> columns,
                                             ResultSet resultSet) throws IOException, SQLException {
        File file = new File(Files.createTempFile(tableName, CSV_EXT).toString());
        try (CSVPrinter printer = new CSVPrinter(new FileWriter(file), CSVFormat.EXCEL)) {
            while (resultSet.next()) {
                int rowCount = resultSet.getRow();
                if (rowCount - 1 == 0) {
                    formCsvRow(columns, rowCount - 1, resultSet, printer, tableName);
                    printer.println();
                }
                formCsvRow(columns, rowCount + NUM_OF_BODY_ROW, resultSet, printer, tableName);
                printer.println();
                if (rowCount % BATCH_SIZE == 0) {
                    printer.flush();
                }
            }
        } finally {
            file.deleteOnExit();
        }
        return file;
    }

    /**
     * Method for forming csv row.
     *
     * @param columns   - list of columns.
     * @param rowCount  - row iter count.
     * @param resultSet - resultSet for SQL queries.
     * @param printer   - csv printer.
     * @param tableName - table name.
     * @throws SQLException - sql exception.
     */
    private static void formCsvRow(List<TestDataTableColumn> columns, int rowCount, ResultSet resultSet,
                                   CSVPrinter printer, String tableName) throws SQLException {
        for (int colCount = 1; colCount < columns.size(); colCount++) {
            String columnName = columns.get(colCount).getIdentity().getColumnName();
            setCellsForCsvFile(rowCount, colCount, columnName, resultSet, value -> {
                try {
                    printer.print(value);
                } catch (IOException e) {
                    log.error(String.format(TdmBuildCvsFileException.DEFAULT_MESSAGE, tableName), e);
                    throw new TdmBuildCvsFileException(tableName);
                }
            });
        }
    }

    /**
     * Set full table to write in excel file for user.
     *
     * @param columnName - name column.
     * @param resultSet  - resultSet for SQL queries.
     * @param consumer   - consumer for work.
     */
    private static void setCellsForExcelFile(int rowCount, String columnName, ResultSet resultSet,
                                             Consumer<String> consumer) throws SQLException {
        if (rowCount == 0) {
            setBodyCellsForFile(columnName, consumer);
        } else {
            String cellValue = "";
            if (Objects.nonNull(resultSet.getObject(columnName))) {
                cellValue = resultSet.getObject(columnName).toString();
            }
            setBodyCellsForFile(cellValue, consumer);
        }
    }

    /**
     * Method to set cells in csv file.
     *
     * @param rowCount   - row iter count.
     * @param colCount   - column iter count.
     * @param columnName - name of a column.
     * @param resultSet  - resultSet for SQL queries.
     * @param consumer   -  consumer for work.
     * @throws SQLException - sql exception.
     */
    private static void setCellsForCsvFile(int rowCount, int colCount, String columnName, ResultSet resultSet,
                                           Consumer<String> consumer) throws SQLException {
        if (rowCount == 0 && colCount > 0) {
            setBodyCellsForFile(columnName, consumer);
        } else {
            String cellValue = "";
            if (Objects.nonNull(resultSet.getObject(columnName))) {
                cellValue = resultSet.getObject(columnName).toString();
            }
            setBodyCellsForFile(cellValue, consumer);
        }
    }

    /**
     * Set body cells for table to write in file for user.
     *
     * @param cellValue - values for cell.
     * @param consumer  - consumer for work.
     */
    private static void setBodyCellsForFile(String cellValue, Consumer<String> consumer) {
        consumer.accept(cellValue);
    }

    /**
     * Linker placeholders resolver.
     * @param hashMap - columns hashmap.
     * @param template - column link.
     * @return - renewed link.
     */
    public static String replaceParams(Map<String, Object> hashMap, String template) {
        return hashMap.entrySet().stream()
                .reduce(template, (s, e) -> String.valueOf(s).replace("${" + e.getKey() + "}",
                                String.valueOf(e.getValue())),
                        (s, s2) -> s);
    }
}

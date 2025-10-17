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

package org.qubership.atp.tdm.repo.impl.loader;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.xml.parsers.SAXParserFactory;

import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.eventusermodel.ReadOnlySharedStringsTable;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler.SheetContentsHandler;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.qubership.atp.tdm.exceptions.internal.TdmTestDataParsingException;
import org.qubership.atp.tdm.model.table.TestDataTable;
import org.qubership.atp.tdm.model.table.column.TestDataTableColumn;
import org.qubership.atp.tdm.model.table.column.TestDataTableColumnIdentity;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TestDataExcelLoader {
    private final OPCPackage xlsxPackage;
    private final TestDataTable testDataTable = new TestDataTable();
    private final Map<Integer, String> columns = new HashMap<>();
    private List<Map<String, Object>> rows = new ArrayList<>();

    /**
     * Full constructor.
     *
     * @param pkg - xlsx package.
     */
    public TestDataExcelLoader(OPCPackage pkg) {
        this.xlsxPackage = pkg;
    }

    /**
     * Process sheet.
     *
     * @throws Exception if xssf reader is empty.
     */
    public TestDataTable process() throws Exception {
        log.info("Excel parsing started.");
        ReadOnlySharedStringsTable strings = new ReadOnlySharedStringsTable(this.xlsxPackage);
        XSSFReader xssfReader = new XSSFReader(this.xlsxPackage);

        try (InputStream stream = xssfReader.getSheetsData().next()) {
            processSheet(strings, new TestDataSheetContentsHandler(), stream);
        }

        testDataTable.setColumns(columns.values().stream()
                .map(c -> new TestDataTableColumn(new TestDataTableColumnIdentity("", c)))
                .collect(Collectors.toList()));
        testDataTable.setData(rows);

        log.info("Excel parsing finished.");

        return testDataTable;
    }

    private void processSheet(ReadOnlySharedStringsTable strings, SheetContentsHandler sheetHandler,
                              InputStream inputStream) throws Exception {
        DataFormatter formatter = new DataFormatter();
        InputSource sheetSource = new InputSource(inputStream);
        try {
            XMLReader sheetParser = newXMLReader();
            ContentHandler handler = new org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler(null,
                    null, strings, sheetHandler, formatter, false);
            sheetParser.setContentHandler(handler);
            sheetParser.parse(sheetSource);
        } catch (SAXException e) {
            log.error(TdmTestDataParsingException.DEFAULT_MESSAGE, e);
            throw new TdmTestDataParsingException();
        }
    }

    /**
     * Creates a secure XMLReader with XXE (XML External Entity) protection.
     * This method configures a SAX parser factory with security features disabled
     * to prevent XML external entity attacks.
     *
     * @return a configured XMLReader instance with security features enabled
     * @throws SAXException if unable to create a safe XMLReader or configure the parser factory
     */
    public static XMLReader newXMLReader() throws SAXException {
        try {
            SAXParserFactory saxFactory = SAXParserFactory.newInstance();
            saxFactory.setNamespaceAware(true);
            saxFactory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            saxFactory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            saxFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

            return saxFactory.newSAXParser().getXMLReader();
        } catch (Exception e) {
            throw new SAXException("Unable to create safe XMLReader", e);
        }
    }

    private class TestDataSheetContentsHandler implements SheetContentsHandler {
        private final String[] systemColumns = {"SELECTED", "OCCUPIED_DATE", "OCCUPIED_BY", "CREATED_WHEN"};
        private List<Integer> workCol = new ArrayList<>();
        private Map<String, Object> row;
        private int currentRow = -1;

        @Override
        public void startRow(int rowNum) {
            currentRow = rowNum;
            if (currentRow > 0) {
                row = new HashMap<>();
            }
        }

        @Override
        public void endRow(int rowNum) {
            if (currentRow > 0) {
                rows.add(row);
            }
        }

        @Override
        public void cell(String cellReference, String formattedValue, XSSFComment comment) {
            int currentCol = new CellReference(cellReference).getCol() + 1;
            if (currentRow == 0) {
                if (!Arrays.asList(systemColumns).contains(formattedValue)) {
                    columns.put(currentCol, formattedValue);
                    workCol.add(currentCol);
                }
            } else {
                if (workCol.contains(currentCol)) {
                    row.put(columns.get(currentCol), formattedValue);
                }
            }
        }

        @Override
        public void headerFooter(String s, boolean b, String s1) {
        }
    }
}

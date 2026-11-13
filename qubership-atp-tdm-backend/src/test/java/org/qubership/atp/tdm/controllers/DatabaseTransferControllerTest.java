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

package org.qubership.atp.tdm.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.qubership.atp.tdm.configuration.GlobalExceptionHandler;
import org.qubership.atp.tdm.configuration.TransferKeyInterceptor;
import org.qubership.atp.tdm.exceptions.transfer.DatabaseTransferException;
import org.qubership.atp.tdm.service.DatabaseExport;
import org.qubership.atp.tdm.service.DatabaseTransferService;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Fails when the export endpoint answers without the transfer key, or answers with the wrong status or headers.
 */
class DatabaseTransferControllerTest {

    private static final String KEY = "0123456789abcdef0123456789abcdef";
    private static final String EXPORT_PATH = "/api/tdm/database/export";

    private DatabaseTransferService service;
    private DatabaseExport export;

    @BeforeEach
    void setUp() throws Exception {
        service = mock(DatabaseTransferService.class);
        export = mock(DatabaseExport.class);
        when(export.fileName()).thenReturn("tdm-atptdm-20261113-101530.sql.gz");
        doAnswer(invocation -> {
            OutputStream target = invocation.getArgument(0);
            target.write("dump".getBytes(StandardCharsets.UTF_8));
            return null;
        }).when(export).writeTo(any());
        when(service.beginExport()).thenReturn(export);
    }

    private MockMvc mockMvc(String configuredKey) {
        return MockMvcBuilders.standaloneSetup(new DatabaseTransferController(service))
                .addMappedInterceptors(new String[] {"/api/tdm/database/**"},
                        new TransferKeyInterceptor(configuredKey))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void export_rightKey_streamsTheDumpAsAGzipAttachment() throws Exception {
        mockMvc(KEY).perform(get(EXPORT_PATH).header(TransferKeyInterceptor.KEY_HEADER, KEY))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/gzip"))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"tdm-atptdm-20261113-101530.sql.gz\""))
                .andExpect(content().string("dump"));

        verify(export).close();
    }

    @Test
    void export_keyUnset_answers404AndStartsNoExport() throws Exception {
        mockMvc("").perform(get(EXPORT_PATH).header(TransferKeyInterceptor.KEY_HEADER, KEY))
                .andExpect(status().isNotFound());

        verify(service, never()).beginExport();
    }

    @Test
    void export_wrongKey_answers403AndStartsNoExport() throws Exception {
        mockMvc(KEY).perform(get(EXPORT_PATH).header(TransferKeyInterceptor.KEY_HEADER, "wrong"))
                .andExpect(status().isForbidden());

        verify(service, never()).beginExport();
    }

    @Test
    void export_exportRefusedByTheService_answersWithItsStatusAndMessage() throws Exception {
        when(service.beginExport())
                .thenThrow(new DatabaseTransferException(HttpStatus.CONFLICT, "Another export is already running."));

        mockMvc(KEY).perform(get(EXPORT_PATH).header(TransferKeyInterceptor.KEY_HEADER, KEY))
                .andExpect(status().isConflict())
                .andExpect(content().string("Another export is already running."));
    }

    @Test
    void export_writeFails_closesTheExport() throws Exception {
        doAnswer(invocation -> {
            throw new IllegalStateException("boom");
        }).when(export).writeTo(any());

        try {
            mockMvc(KEY).perform(get(EXPORT_PATH).header(TransferKeyInterceptor.KEY_HEADER, KEY));
        } catch (Exception expected) {
            // The servlet layer wraps the failure; the test checks that the export slot is released.
        }

        verify(export).close();
    }
}

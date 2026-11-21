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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.qubership.atp.tdm.configuration.GlobalExceptionHandler;
import org.qubership.atp.tdm.configuration.TransferKeyFilter;
import org.qubership.atp.tdm.exceptions.transfer.DatabaseTransferException;
import org.qubership.atp.tdm.model.transfer.TransferState;
import org.qubership.atp.tdm.model.transfer.TransferStatus;
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
                .addFilters(new TransferKeyFilter(configuredKey))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void export_rightKey_streamsTheDumpAsAGzipAttachment() throws Exception {
        mockMvc(KEY).perform(get(EXPORT_PATH).header(TransferKeyFilter.KEY_HEADER, KEY))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/gzip"))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"tdm-atptdm-20261113-101530.sql.gz\""))
                .andExpect(content().string("dump"));

        verify(export).close();
    }

    @Test
    void export_keyUnset_answers404AndStartsNoExport() throws Exception {
        mockMvc("").perform(get(EXPORT_PATH).header(TransferKeyFilter.KEY_HEADER, KEY))
                .andExpect(status().isNotFound());

        verify(service, never()).beginExport();
    }

    @Test
    void export_wrongKey_answers403AndStartsNoExport() throws Exception {
        mockMvc(KEY).perform(get(EXPORT_PATH).header(TransferKeyFilter.KEY_HEADER, "wrong"))
                .andExpect(status().isForbidden());

        verify(service, never()).beginExport();
    }

    @Test
    void export_exportRefusedByTheService_answersWithItsStatusAndMessage() throws Exception {
        when(service.beginExport())
                .thenThrow(new DatabaseTransferException(HttpStatus.CONFLICT, "Another export is already running."));

        mockMvc(KEY).perform(get(EXPORT_PATH).header(TransferKeyFilter.KEY_HEADER, KEY))
                .andExpect(status().isConflict())
                .andExpect(content().string("Another export is already running."));
    }

    private static final String IMPORT_PATH = "/api/tdm/database/import";

    private static TransferStatus statusIn(TransferState state) {
        TransferStatus status = new TransferStatus();
        status.setState(state);
        return status;
    }

    @Test
    void stageImport_rightKey_answers202WithTheStatusAndPassesTheBody() throws Exception {
        when(service.stageImport(any(), org.mockito.ArgumentMatchers.eq(4L))).thenAnswer(invocation -> {
            byte[] body = ((InputStream) invocation.getArgument(0)).readAllBytes();
            TransferStatus staged = statusIn(TransferState.STAGED);
            staged.setDumpServiceVersion(new String(body, StandardCharsets.UTF_8));
            return staged;
        });

        mockMvc(KEY).perform(post(IMPORT_PATH).header(TransferKeyFilter.KEY_HEADER, KEY)
                        .contentType("application/gzip").content("body"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.state").value("STAGED"))
                .andExpect(jsonPath("$.dumpServiceVersion").value("body"));
    }

    @Test
    void stageImport_otherContentType_answers415() throws Exception {
        mockMvc(KEY).perform(post(IMPORT_PATH).header(TransferKeyFilter.KEY_HEADER, KEY)
                        .contentType("text/plain").content("body"))
                .andExpect(status().isUnsupportedMediaType());

        verify(service, never()).stageImport(any(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void stageImport_invalidDump_answersWithTheStatusAndTheReason() throws Exception {
        when(service.stageImport(any(), org.mockito.ArgumentMatchers.anyLong())).thenThrow(
                new DatabaseTransferException(HttpStatus.BAD_REQUEST, "Invalid dump: no header."));

        mockMvc(KEY).perform(post(IMPORT_PATH).header(TransferKeyFilter.KEY_HEADER, KEY)
                        .contentType("application/gzip").content("body"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Invalid dump: no header."));
    }

    @Test
    void importOperations_wrongKey_answer403AndTouchNothing() throws Exception {
        MockMvc mockMvc = mockMvc(KEY);

        mockMvc.perform(post(IMPORT_PATH).header(TransferKeyFilter.KEY_HEADER, "wrong")
                .contentType("application/gzip").content("body")).andExpect(status().isForbidden());
        mockMvc.perform(get(IMPORT_PATH)).andExpect(status().isForbidden());
        mockMvc.perform(delete(IMPORT_PATH)).andExpect(status().isForbidden());
        mockMvc.perform(post(IMPORT_PATH + "/apply").param("confirm", "REPLACE_ALL_DATA"))
                .andExpect(status().isForbidden());

        org.mockito.Mockito.verifyNoInteractions(service);
    }

    @Test
    void importOperations_keyUnset_answer404() throws Exception {
        MockMvc mockMvc = mockMvc("");

        mockMvc.perform(get(IMPORT_PATH)).andExpect(status().isNotFound());
        mockMvc.perform(delete(IMPORT_PATH)).andExpect(status().isNotFound());
        mockMvc.perform(post(IMPORT_PATH + "/apply")).andExpect(status().isNotFound());

        org.mockito.Mockito.verifyNoInteractions(service);
    }

    /**
     * A request that matches no controller method fails in the handler lookup, before an interceptor would run. The
     * key check has to answer such a request too, so that a client without the key learns nothing about the endpoint.
     */
    @Test
    void requestsNoControllerMethodMatches_withoutTheKey_answer403() throws Exception {
        MockMvc mockMvc = mockMvc(KEY);

        mockMvc.perform(post(IMPORT_PATH).contentType("text/plain").content("body"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(IMPORT_PATH + "/apply")).andExpect(status().isForbidden());
        mockMvc.perform(delete(EXPORT_PATH)).andExpect(status().isForbidden());

        org.mockito.Mockito.verifyNoInteractions(service);
    }

    @Test
    void requestsNoControllerMethodMatches_keyUnset_answer404() throws Exception {
        MockMvc mockMvc = mockMvc("");

        mockMvc.perform(post(IMPORT_PATH).contentType("text/plain").content("body"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(IMPORT_PATH + "/apply")).andExpect(status().isNotFound());
        mockMvc.perform(delete(EXPORT_PATH)).andExpect(status().isNotFound());
    }

    @Test
    void requestsNoControllerMethodMatches_rightKey_stillAnswerWithTheFrameworkStatus() throws Exception {
        MockMvc mockMvc = mockMvc(KEY);

        mockMvc.perform(get(IMPORT_PATH + "/apply").header(TransferKeyFilter.KEY_HEADER, KEY))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(delete(EXPORT_PATH).header(TransferKeyFilter.KEY_HEADER, KEY))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void importStatus_returnsTheRecordedStatus() throws Exception {
        when(service.importStatus()).thenReturn(statusIn(TransferState.APPLIED));

        mockMvc(KEY).perform(get(IMPORT_PATH).header(TransferKeyFilter.KEY_HEADER, KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("APPLIED"));
    }

    @Test
    void cancelImport_answers204() throws Exception {
        mockMvc(KEY).perform(delete(IMPORT_PATH).header(TransferKeyFilter.KEY_HEADER, KEY))
                .andExpect(status().isNoContent());

        verify(service).cancelImport();
    }

    @Test
    void applyImport_withoutRestartParameter_restartsByDefault() throws Exception {
        when(service.approveImport("REPLACE_ALL_DATA", true)).thenReturn(statusIn(TransferState.APPROVED));

        mockMvc(KEY).perform(post(IMPORT_PATH + "/apply").param("confirm", "REPLACE_ALL_DATA")
                        .header(TransferKeyFilter.KEY_HEADER, KEY))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.state").value("APPROVED"));
    }

    @Test
    void applyImport_restartFalse_passesItOn() throws Exception {
        when(service.approveImport("REPLACE_ALL_DATA", false)).thenReturn(statusIn(TransferState.APPROVED));

        mockMvc(KEY).perform(post(IMPORT_PATH + "/apply").param("confirm", "REPLACE_ALL_DATA")
                        .param("restart", "false").header(TransferKeyFilter.KEY_HEADER, KEY))
                .andExpect(status().isAccepted());

        verify(service).approveImport("REPLACE_ALL_DATA", false);
    }

    @Test
    void applyImport_missingConfirmation_passesNullAndAnswersWithTheServiceStatus() throws Exception {
        when(service.approveImport(null, true)).thenThrow(
                new DatabaseTransferException(HttpStatus.BAD_REQUEST, "Pass confirm=REPLACE_ALL_DATA."));

        mockMvc(KEY).perform(post(IMPORT_PATH + "/apply").header(TransferKeyFilter.KEY_HEADER, KEY))
                .andExpect(status().isBadRequest());
    }

    @Test
    void export_writeFails_closesTheExport() throws Exception {
        doAnswer(invocation -> {
            throw new IllegalStateException("boom");
        }).when(export).writeTo(any());

        try {
            mockMvc(KEY).perform(get(EXPORT_PATH).header(TransferKeyFilter.KEY_HEADER, KEY));
        } catch (Exception expected) {
            // The servlet layer wraps the failure; the test checks that the export slot is released.
        }

        verify(export).close();
    }
}

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

package org.qubership.atp.tdm.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.web.servlet.MultipartProperties;
import org.springframework.boot.web.embedded.undertow.UndertowServletWebServerFactory;
import org.springframework.boot.web.server.WebServer;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.util.unit.DataSize;

import io.undertow.UndertowOptions;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Fails when Undertow limits a multipart upload to something other than
 * {@code spring.servlet.multipart.max-request-size}. The test starts a real Undertow server, because the limit is
 * invisible to a mock request.
 */
class MultipartSizeConfigTest {

    private static final int MEBIBYTE = 1024 * 1024;

    private WebServer server;

    @BeforeEach
    void startServer() {
        MultipartProperties multipart = new MultipartProperties();
        multipart.setMaxFileSize(DataSize.ofMegabytes(10));
        multipart.setMaxRequestSize(DataSize.ofMegabytes(4));
        UndertowServletWebServerFactory factory = new UndertowServletWebServerFactory(0);
        new MultipartSizeConfig().multipartSizeCustomizer(multipart).customize(factory);
        // What server.undertow.max-http-post-size does in the service. Undertow checks it first, for every body.
        factory.addBuilderCustomizers(builder -> builder.setServerOption(UndertowOptions.MAX_ENTITY_SIZE,
                DataSize.ofMegabytes(20).toBytes()));
        ServletRegistrationBean<HttpServlet> servlet = new ServletRegistrationBean<>(new PartSizeServlet(), "/*");
        servlet.setMultipartConfig(multipart.createMultipartConfig());
        server = factory.getWebServer(servlet);
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    private HttpResponse<String> upload(int fileSize) throws Exception {
        String body = "--b\r\nContent-Disposition: form-data; name=\"file\"; filename=\"a\"\r\n\r\n"
                + "x".repeat(fileSize) + "\r\n--b--\r\n";
        HttpRequest request = HttpRequest
                .newBuilder(URI.create("http://localhost:" + server.getPort() + "/upload"))
                .header("Content-Type", "multipart/form-data; boundary=b")
                .POST(BodyPublishers.ofString(body))
                .build();
        return HttpClient.newHttpClient().send(request, BodyHandlers.ofString());
    }

    @Test
    void upload_overTheUndertowDefaultAndUnderTheRequestLimit_isAccepted() throws Exception {
        HttpResponse<String> response = upload(3 * MEBIBYTE);

        assertEquals(200, response.statusCode());
        assertEquals(Integer.toString(3 * MEBIBYTE), response.body());
    }

    @Test
    void upload_overTheRequestLimit_isRejected() throws Exception {
        try {
            assertNotEquals(200, upload(5 * MEBIBYTE).statusCode());
        } catch (IOException e) {
            // The server may close the connection before the client has sent the whole body.
        }
    }

    /** Answers with the size of the uploaded file. */
    private static final class PartSizeServlet extends HttpServlet {

        @Override
        protected void doPost(HttpServletRequest request, HttpServletResponse response)
                throws IOException, ServletException {
            response.getWriter().write(Long.toString(request.getPart("file").getSize()));
        }
    }
}

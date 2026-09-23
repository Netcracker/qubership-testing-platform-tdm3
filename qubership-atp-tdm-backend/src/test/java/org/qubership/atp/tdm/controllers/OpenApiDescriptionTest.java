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

import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.qubership.atp.tdm.AbstractTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Fails when the REST API differs from the committed OpenAPI description in {@code docs/openapi.json}.
 *
 * <p>The test builds the description with springdoc from the controllers and compares it with the committed
 * file as JSON, so line endings and formatting do not matter. After a change to a controller or to a request or
 * response model, regenerate the file with {@link #UPDATE_COMMAND} from the repository root, and commit it together
 * with the change.</p>
 */
@AutoConfigureMockMvc
class OpenApiDescriptionTest extends AbstractTest {

    /** Path of the committed description, relative to the repository root. */
    private static final String DESCRIPTION_NAME = "docs/openapi.json";

    private static final Path COMMITTED_DESCRIPTION = Path.of("..").resolve(DESCRIPTION_NAME);

    private static final String UPDATE_PROPERTY = "openapi.update";

    private static final String UPDATE_COMMAND = "mvn -P github -pl qubership-atp-tdm-backend -am test"
            + " -Dtest=OpenApiDescriptionTest -Dsurefire.failIfNoSpecifiedTests=false -D" + UPDATE_PROPERTY + "=true";

    private final ObjectMapper mapper = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Test
    void committedDescriptionMatchesControllers() throws Exception {
        String body = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        ObjectNode actual = (ObjectNode) mapper.readTree(body);
        // MockMvc reports http://localhost as the server, which says nothing about a real deployment.
        actual.remove("servers");

        if (Boolean.getBoolean(UPDATE_PROPERTY)) {
            write(actual);
            return;
        }
        if (!Files.exists(COMMITTED_DESCRIPTION)) {
            fail(DESCRIPTION_NAME + " does not exist. Generate it from the repository root with:\n"
                    + UPDATE_COMMAND);
        }
        JsonNode committed = mapper.readTree(COMMITTED_DESCRIPTION.toFile());
        if (!committed.equals(actual)) {
            fail("The REST API differs from " + DESCRIPTION_NAME + ".\n"
                    + String.join("\n", differences(committed, actual)) + "\n"
                    + "If the change is intended, regenerate the file from the repository root and commit it:\n"
                    + UPDATE_COMMAND);
        }
    }

    private void write(JsonNode description) throws Exception {
        DefaultPrettyPrinter printer = new DefaultPrettyPrinter()
                .withObjectIndenter(new DefaultIndenter("  ", "\n"))
                .withArrayIndenter(new DefaultIndenter("  ", "\n"));
        String json = mapper.writer(printer).writeValueAsString(description) + "\n";
        Files.createDirectories(COMMITTED_DESCRIPTION.getParent());
        Files.writeString(COMMITTED_DESCRIPTION, json, StandardCharsets.UTF_8);
    }

    /**
     * Lists the paths and schemas that were added, removed, or changed, so the failure names what to review.
     * Differences outside {@code paths} and {@code components.schemas} are reported as one line.
     */
    private static List<String> differences(JsonNode committed, JsonNode actual) {
        List<String> result = new ArrayList<>();
        compareMembers("path", committed.path("paths"), actual.path("paths"), result);
        compareMembers("schema", committed.path("components").path("schemas"),
                actual.path("components").path("schemas"), result);
        if (result.isEmpty()) {
            result.add("  changed: a part of the description outside paths and schemas");
        }
        return result;
    }

    private static void compareMembers(String kind, JsonNode committed, JsonNode actual, List<String> result) {
        for (Iterator<Map.Entry<String, JsonNode>> it = actual.fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> member = it.next();
            JsonNode old = committed.get(member.getKey());
            if (old == null) {
                result.add("  added " + kind + ": " + member.getKey());
            } else if (!old.equals(member.getValue())) {
                result.add("  changed " + kind + ": " + member.getKey());
            }
        }
        for (Iterator<String> it = committed.fieldNames(); it.hasNext(); ) {
            String name = it.next();
            if (!actual.has(name)) {
                result.add("  removed " + kind + ": " + name);
            }
        }
    }
}

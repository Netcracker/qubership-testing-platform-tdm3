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

package org.qubership.atp.tdm.matchers;


import java.io.IOException;
import java.util.Objects;

import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

public class JsonMatcher extends TypeSafeMatcher<String> {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final ObjectMapper INDENT_OUTPUT = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private final String er;

    private JsonMatcher(String er) {
        this.er = er;
    }

    public static JsonMatcher isMinified(String prettyEr) throws IOException {
        return is(doMinifiedOutput(prettyEr));
    }

    private static JsonMatcher is(String er) {
        return new JsonMatcher(er);
    }

    private static String tryDoIndentOutput(String json) {
        try {
            return INDENT_OUTPUT.writeValueAsString(OBJECT_MAPPER.readTree(json));
        } catch (Throwable ignore) {
            return json;
        }
    }

    private static String doMinifiedOutput(String json) throws IOException {
        return OBJECT_MAPPER.writeValueAsString(OBJECT_MAPPER.readTree(json));
    }

    @Override
    protected boolean matchesSafely(String ar) {
        return Objects.equals(er, ar);
    }

    @Override
    public void describeTo(Description description) {
        description.appendText("json: ").appendText(tryDoIndentOutput(er));
    }

    @Override
    protected void describeMismatchSafely(String ar, Description mismatchDescription) {
        mismatchDescription.appendText("was: ").appendText(tryDoIndentOutput(ar));
    }
}

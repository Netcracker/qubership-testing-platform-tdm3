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

package org.qubership.atp.tdm.service.impl;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a dump once and rejects it unless it is a TDM3 dump that holds only the statement kinds of a dump of the live
 * database.
 *
 * <p>The dump is executable SQL, so the validator splits it into statements itself, with a tokenizer that knows
 * quoted strings and quoted identifiers, and judges each statement by the words outside quotes. The tokenizer refuses
 * every construct where it could disagree with H2 about where a statement or a quoted text ends: a comment inside a
 * statement, a block comment, and a dollar-quoted string.</p>
 *
 * <p>The filter lowers the risk of running a crafted dump and does not remove it, because H2 is a large SQL
 * dialect.</p>
 */
public final class DumpValidator {

    /** Longest statement the validator buffers, in characters. */
    static final int MAX_STATEMENT_CHARS = 16 * 1024 * 1024;

    private static final int MAX_HEADER_CHARS = 1024;

    private static final Pattern HEADER = Pattern.compile(
            "-- TDM3-DUMP v1 service=(\\S+) h2=(\\S+) created=(\\S+) database=(\\S+)");

    /**
     * The only alias a TDM3 database holds, which a Liquibase changeset creates. It is accepted by its exact text,
     * so that another target is rejected.
     */
    private static final String UUID_ALIAS = "CREATE FORCE ALIAS \"PUBLIC\".\"UUID_GENERATE_V4\" "
            + "FOR 'org.h2.value.ValueUuid.getNewRandom'";

    // The patterns below match the skeleton of a statement: upper-cased, whitespace collapsed, each quoted identifier
    // replaced by a double quote, and each string literal replaced by a single quote.
    private static final Pattern USER_STATEMENT =
            Pattern.compile("(CREATE|ALTER|DROP) (USER|ROLE) .*|(GRANT|REVOKE) .*");
    private static final Pattern CREATE_TABLE = Pattern.compile("CREATE CACHED TABLE \".*");
    private static final Pattern ADD_CONSTRAINT = Pattern.compile("ALTER TABLE \".* ADD CONSTRAINT .*");
    private static final Pattern CREATE_INDEX = Pattern.compile("CREATE (UNIQUE )?(HASH )?INDEX \".*");
    private static final Pattern INSERT = Pattern.compile("INSERT INTO \"(?:\\.\")?(?: ?\\([^)]*\\))? VALUES(.*)");
    private static final Pattern FORBIDDEN_WORD = Pattern.compile("\\b(SELECT|CALL|FILE_READ|FILE_WRITE|CSVREAD|CSVWRITE"
            + "|LINK_SCHEMA|RUNSCRIPT|SCRIPT|ALIAS|TRIGGER|AGGREGATE|SHUTDOWN|EXECUTE|ENGINE|JAVA_OBJECT)\\b");

    private DumpValidator() {
    }

    /**
     * Validates a dump and copies the accepted statements.
     *
     * @param dump         the decompressed text of the dump
     * @param accepted     receives the header line and every accepted statement, one per line and each ended with
     *                     {@code ;}, or {@code null} to only validate. Comments are not copied.
     * @param dropAccounts {@code true} to leave out the statements that create or change a user or a role and the
     *                     grants, {@code false} to reject them
     * @return the header of the dump
     * @throws DumpValidationException when the dump breaks a rule
     * @throws IOException             when reading the dump or writing the copy fails
     */
    public static DumpHeader validate(Reader dump, Writer accepted, boolean dropAccounts) throws IOException {
        CharCursor in = new CharCursor(dump);
        DumpHeader header = readHeader(in);
        if (accepted != null) {
            accepted.write(header.line());
            accepted.write('\n');
        }
        Statement statement = new Statement();
        int next;
        while ((next = in.read()) >= 0) {
            char ch = (char) next;
            if (ch == '\'' || ch == '"') {
                statement.append(ch);
                statement.skeleton.append(ch);
                copyQuoted(in, ch, statement);
            } else if (ch == ';') {
                finish(statement, accepted, dropAccounts);
                statement.reset();
            } else if (ch == '-' && in.peek() == '-') {
                if (!statement.isBlank()) {
                    throw new DumpValidationException("A comment starts inside a statement.");
                }
                skipLine(in);
            } else if ((ch == '/' && (in.peek() == '*' || in.peek() == '/')) || (ch == '$' && in.peek() == '$')) {
                throw new DumpValidationException("The dump uses a block comment, a slash comment, or a "
                        + "dollar-quoted string, which a TDM3 dump never contains.");
            } else {
                statement.append(ch);
                statement.appendSkeleton(ch);
            }
            if (statement.original.length() > MAX_STATEMENT_CHARS) {
                throw new DumpValidationException("A statement is longer than " + MAX_STATEMENT_CHARS + " characters.");
            }
        }
        if (!statement.isBlank()) {
            throw new DumpValidationException("The dump ends in the middle of a statement.");
        }
        return header;
    }

    private static DumpHeader readHeader(CharCursor in) throws IOException {
        StringBuilder line = new StringBuilder();
        int next;
        while ((next = in.read()) >= 0 && next != '\n') {
            line.append((char) next);
            if (line.length() > MAX_HEADER_CHARS) {
                break;
            }
        }
        String text = line.toString().stripTrailing();
        Matcher matcher = HEADER.matcher(text);
        if (!matcher.matches()) {
            throw new DumpValidationException("The first line is not the TDM3 dump header "
                    + "(\"-- TDM3-DUMP v1 service=... h2=... created=... database=...\").");
        }
        return new DumpHeader(text, matcher.group(1), matcher.group(2), matcher.group(3), matcher.group(4));
    }

    private static void copyQuoted(CharCursor in, char quote, Statement statement) throws IOException {
        while (true) {
            int next = in.read();
            if (next < 0) {
                throw new DumpValidationException("The dump ends inside a quoted text.");
            }
            statement.append((char) next);
            if (next == quote) {
                if (in.peek() != quote) {
                    return;
                }
                statement.append((char) in.read());
            }
            if (statement.original.length() > MAX_STATEMENT_CHARS) {
                throw new DumpValidationException("A statement is longer than " + MAX_STATEMENT_CHARS + " characters.");
            }
        }
    }

    private static void skipLine(CharCursor in) throws IOException {
        int next;
        while ((next = in.read()) >= 0 && next != '\n' && next != '\r') {
            // The comment text is dropped.
        }
    }

    private static void finish(Statement statement, Writer accepted, boolean dropAccounts) throws IOException {
        String skeleton = statement.skeleton.toString().strip();
        if (skeleton.isEmpty()) {
            return;
        }
        String text = statement.original.toString().strip();
        if (!UUID_ALIAS.equals(text.replaceAll("\\s+", " "))) {
            if (dropAccounts && USER_STATEMENT.matcher(skeleton).matches()) {
                return;
            }
            check(skeleton);
        }
        if (accepted != null) {
            accepted.write(text);
            accepted.write(";\n");
        }
    }

    private static void check(String skeleton) {
        boolean allowed = CREATE_TABLE.matcher(skeleton).matches()
                || ADD_CONSTRAINT.matcher(skeleton).matches()
                || CREATE_INDEX.matcher(skeleton).matches();
        Matcher insert = INSERT.matcher(skeleton);
        if (insert.matches()) {
            allowed = true;
            if (insert.group(1).indexOf('"') >= 0) {
                throw new DumpValidationException("An INSERT statement uses a quoted name after VALUES.");
            }
        }
        if (!allowed) {
            throw new DumpValidationException("The statement \"" + firstWords(skeleton)
                    + "...\" is not allowed in a TDM3 dump.");
        }
        Matcher forbidden = FORBIDDEN_WORD.matcher(skeleton);
        if (forbidden.find()) {
            throw new DumpValidationException("The statement \"" + firstWords(skeleton) + "...\" uses "
                    + forbidden.group(1) + ", which is not allowed in a TDM3 dump.");
        }
    }

    private static String firstWords(String skeleton) {
        String[] words = skeleton.split(" ", 4);
        return String.join(" ", Arrays.copyOf(words, Math.min(words.length, 3)));
    }

    /**
     * The facts that the first line of a dump records.
     *
     * @param line           the header line without its line break
     * @param serviceVersion the version of the service that wrote the dump
     * @param h2Version      the version of H2 that wrote the dump
     * @param created        when the dump was written, as written in the header
     * @param database       the name of the exported database
     */
    public record DumpHeader(String line, String serviceVersion, String h2Version, String created, String database) {
    }

    /** One statement as read from the dump, and the skeleton that the rules inspect. */
    private static final class Statement {

        private final StringBuilder original = new StringBuilder();
        private final StringBuilder skeleton = new StringBuilder();
        private boolean blank = true;

        void append(char ch) {
            original.append(ch);
            if (!Character.isWhitespace(ch)) {
                blank = false;
            }
        }

        void appendSkeleton(char ch) {
            if (Character.isWhitespace(ch)) {
                if (skeleton.length() > 0 && skeleton.charAt(skeleton.length() - 1) != ' ') {
                    skeleton.append(' ');
                }
            } else {
                // H2 matches keywords after converting ASCII letters only, so the skeleton does the same.
                skeleton.append(ch >= 'a' && ch <= 'z' ? (char) (ch - 'a' + 'A') : ch);
            }
        }

        boolean isBlank() {
            return blank;
        }

        void reset() {
            original.setLength(0);
            skeleton.setLength(0);
            blank = true;
        }
    }

    /** A reader with a one-character look-ahead, without a lock per character. */
    private static final class CharCursor {

        private final Reader reader;
        private final char[] buffer = new char[64 * 1024];
        private int position;
        private int limit;

        CharCursor(Reader reader) {
            this.reader = reader;
        }

        int read() throws IOException {
            return fill() ? buffer[position++] : -1;
        }

        int peek() throws IOException {
            return fill() ? buffer[position] : -1;
        }

        private boolean fill() throws IOException {
            while (position >= limit) {
                int count = reader.read(buffer, 0, buffer.length);
                if (count < 0) {
                    return false;
                }
                position = 0;
                limit = count;
            }
            return true;
        }
    }
}

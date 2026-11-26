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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Fails when the validator accepts a statement that can run code or read a file, or rejects what a dump of the live
 * database contains.
 */
class DumpValidatorTest {

    private static final String HEADER =
            "-- TDM3-DUMP v1 service=1.0.13 h2=2.2.224 created=2026-11-11T10:15:30Z database=atptdm\n";
    private static final String CREATE_TABLE = "CREATE CACHED TABLE \"PUBLIC\".\"T\"(\n    \"ID\" BIGINT NOT NULL,\n"
            + "    \"NOTE\" CHARACTER VARYING(100)\n);\n";

    private static DumpValidator.DumpHeader validate(String body) throws IOException {
        return DumpValidator.validate(new StringReader(HEADER + body), null, false);
    }

    private static String copy(String body, boolean dropAccounts) throws IOException {
        StringWriter out = new StringWriter();
        DumpValidator.validate(new StringReader(HEADER + body), out, dropAccounts);
        return out.toString();
    }

    @Test
    void validate_dumpOfTheLiveDatabase_isAccepted() throws IOException {
        String dump = "-- H2 2.2.224;\n"
                + "CREATE FORCE ALIAS \"PUBLIC\".\"UUID_GENERATE_V4\" FOR 'org.h2.value.ValueUuid.getNewRandom';\n"
                + CREATE_TABLE
                + "-- 2 +/- SELECT COUNT(*) FROM PUBLIC.T;\n"
                + "INSERT INTO \"PUBLIC\".\"T\"(\"ID\", \"NOTE\") VALUES\n(1, 'it''s; a -- note'),\n(2, NULL);\n"
                + "ALTER TABLE \"PUBLIC\".\"T\" ADD CONSTRAINT \"PUBLIC\".\"CONSTRAINT_1\" PRIMARY KEY(\"ID\");\n"
                + "CREATE INDEX \"PUBLIC\".\"IDX\" ON \"PUBLIC\".\"T\"(\"NOTE\");\n";

        DumpValidator.DumpHeader header = validate(dump);

        assertEquals("1.0.13", header.serviceVersion());
        assertEquals("2.2.224", header.h2Version());
        assertEquals("atptdm", header.database());
    }

    @Test
    void validate_acceptedCopy_keepsStatementsAndDropsComments() throws IOException {
        String copy = copy("-- a comment\n" + CREATE_TABLE + "INSERT INTO \"PUBLIC\".\"T\" VALUES (1, 'x');\n", false);

        assertTrue(copy.startsWith(HEADER));
        assertFalse(copy.contains("a comment"));
        assertTrue(copy.contains("CREATE CACHED TABLE \"PUBLIC\".\"T\"("));
        assertTrue(copy.contains("INSERT INTO \"PUBLIC\".\"T\" VALUES (1, 'x');"));
    }

    @Test
    void validate_dropAccounts_removesUserRoleAndGrantStatements() throws IOException {
        String copy = copy("CREATE USER IF NOT EXISTS \"TDMADMIN\" PASSWORD '' ADMIN;\n"
                + "CREATE ROLE \"R\";\nGRANT \"R\" TO \"TDMADMIN\";\n" + CREATE_TABLE, true);

        assertFalse(copy.contains("USER"));
        assertFalse(copy.contains("ROLE"));
        assertFalse(copy.contains("GRANT"));
        assertTrue(copy.contains("CREATE CACHED TABLE"));
    }

    @Test
    void validate_userStatementWithoutDropAccounts_isRejected() {
        assertThrows(DumpValidationException.class,
                () -> validate("CREATE USER IF NOT EXISTS \"TDMADMIN\" PASSWORD '' ADMIN;\n"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "CREATE FORCE ALIAS \"PUBLIC\".\"EVIL\" FOR 'java.lang.Runtime.getRuntime';",
            "CREATE FORCE ALIAS \"PUBLIC\".\"UUID_GENERATE_V4\" FOR 'java.lang.System.exit';",
            "CREATE ALIAS EXEC AS $$ void e() { } $$;",
            "CREATE TRIGGER T BEFORE INSERT ON \"PUBLIC\".\"T\" CALL 'x';",
            "CREATE AGGREGATE A FOR 'x';",
            "RUNSCRIPT FROM 'other.sql';",
            "CALL FILE_READ('/etc/passwd');",
            "SCRIPT TO 'out.sql';",
            "SHUTDOWN;",
            "SET MODE Oracle;",
            "DROP TABLE \"PUBLIC\".\"T\";",
            "INSERT INTO \"PUBLIC\".\"T\" SELECT * FROM \"PUBLIC\".\"U\";",
            "INSERT INTO \"PUBLIC\".\"T\" VALUES (FILE_READ('/etc/passwd'), 1);",
            "INSERT INTO \"PUBLIC\".\"T\" VALUES (\"FILE_READ\"('/etc/passwd'), 1);",
            "INSERT INTO \"PUBLIC\".\"T\" VALUES (SELECT'x');",
            "CREATE CACHED TABLE \"PUBLIC\".\"T\" AS SELECT * FROM CSVREAD('x.csv');",
            "CREATE CACHED TABLE \"PUBLIC\".\"T\"(\"ID\" INT) ENGINE \"com.example.Engine\";",
            "CREATE CACHED TABLE \"PUBLIC\".\"T\"(\"ID\" INT CHECK (LINK_SCHEMA('a','b','c','d','e') IS NULL));",
            "ALTER TABLE \"PUBLIC\".\"T\" ADD CONSTRAINT \"C\" CHECK (FILE_WRITE('a', 'b') > 0);"
    })
    void validate_dangerousStatement_isRejected(String statement) {
        assertThrows(DumpValidationException.class, () -> validate(CREATE_TABLE + statement + "\n"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "INSERT INTO \"PUBLIC\".\"T\" VALUES (1, 'a'); CALL FILE_READ('/etc/passwd');\n",
            "INSERT INTO \"PUBLIC\".\"T\" VALUES (1, 'a') -- hidden\n;\n",
            "INSERT INTO \"PUBLIC\".\"T\" VALUES (1, /* hidden */ 'a');\n",
            "INSERT INTO \"PUBLIC\".\"T\" VALUES (1, 'a') // hidden\n;\n",
            "INSERT INTO \"PUBLIC\".\"T\" VALUES (1, $$a$$);\n",
            "INSERT INTO \"PUBLIC\".\"T\" VALUES (1, 'unterminated);\n",
            "INSERT INTO \"PUBLIC\".\"T\" VALUES (1, 'a')\n"
    })
    void validate_statementBoundaryTricks_areRejected(String text) {
        assertThrows(DumpValidationException.class, () -> validate(CREATE_TABLE + text));
    }

    @Test
    void validate_semicolonInsideQuotedText_doesNotSplitTheStatement() throws IOException {
        validate(CREATE_TABLE + "INSERT INTO \"PUBLIC\".\"T\" VALUES (1, 'a; CALL x; b');\n");
    }

    @Test
    void validate_quotedIdentifierWithSemicolon_isOneStatement() throws IOException {
        validate("CREATE CACHED TABLE \"PUBLIC\".\"T;CALL\"(\"ID\" INT);\n");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "CREATE CACHED TABLE \"PUBLIC\".\"T\"(\"ID\" INT);\n",
            "-- TDM3-DUMP v2 service=1.0.13 h2=2.2.224 created=x database=atptdm\n",
            "-- TDM3-DUMP v1 service=1.0.13 h2=2.2.224 created=x\n",
            "SELECT 1;\n"
    })
    void validate_wrongFirstLine_isRejected(String dump) {
        assertThrows(DumpValidationException.class, () -> DumpValidator.validate(new StringReader(dump), null, false));
    }

    @Test
    void validate_headerOnly_isAccepted() throws IOException {
        validate("");
    }

    @Test
    void validate_message_namesTheStatementKindAndNeverQuotesData() {
        DumpValidationException exception = assertThrows(DumpValidationException.class,
                () -> validate("CREATE TRIGGER \"secret-value\" BEFORE INSERT ON T CALL 'x';\n"));

        assertTrue(exception.getMessage().contains("CREATE TRIGGER"), exception.getMessage());
        assertFalse(exception.getMessage().contains("secret-value"));
    }
}

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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.qubership.atp.tdm.exceptions.transfer.DatabaseTransferException;

/**
 * Fails when the version rule of the transfer lets a newer dump in or blocks an older one.
 */
class DumpVersionsTest {

    @Test
    void check_newerDump_isRejectedWith400() {
        DatabaseTransferException exception =
                assertThrows(DatabaseTransferException.class, () -> DumpVersions.check("1.0.14", "1.0.13"));

        assertEquals(400, exception.getStatus().value());
        assertTrue(exception.getMessage().contains("Downgrades are not supported"));
    }

    @Test
    void check_missingOrBrokenVersion_isRejectedWith400() {
        assertThrows(DatabaseTransferException.class, () -> DumpVersions.check("unknown", "1.0.13"));
        assertThrows(DatabaseTransferException.class, () -> DumpVersions.check("", "1.0.13"));
        assertThrows(DatabaseTransferException.class, () -> DumpVersions.check(null, "1.0.13"));
    }

    @Test
    void check_olderMinorOfTheSameMajor_givesNoWarning() {
        assertEquals(List.of(), DumpVersions.check("1.0.5", "1.5.10"));
    }

    @Test
    void check_sameVersionWithQualifier_givesNoWarning() {
        assertEquals(List.of(), DumpVersions.check("1.0.13-SNAPSHOT", "1.0.13"));
        assertEquals(List.of(), DumpVersions.check("1.0.13", "1.0.13-SNAPSHOT"));
    }

    @Test
    void check_olderMajor_isAcceptedWithAWarning() {
        List<String> warnings = DumpVersions.check("1.0.5", "5.10.20");

        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("1.0.5") && warnings.get(0).contains("5.10.20"), warnings.get(0));
    }

    @Test
    void check_developmentTarget_acceptsAReleaseWithAWarningNamingBothVersions() {
        List<String> warnings = DumpVersions.check("1.0.13", "0.0.1.LOCAL");

        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("1.0.13") && warnings.get(0).contains("0.0.1.LOCAL"), warnings.get(0));
    }

    @Test
    void check_developmentDumpIntoDevelopmentTarget_givesNoWarning() {
        assertEquals(List.of(), DumpVersions.check("0.0.1.LOCAL", "0.0.1.LOCAL"));
    }
}

/*
 *    Copyright (c) 2024-2026 SOPTIM AG
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 *
 */

package org.rdfarchitect.xmi.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

class XmiModelScannerTest {

    private static final Path EXPORT =
            Path.of("src/test/java/org/rdfarchitect/xmi/read/testdata/profiles-export.xml");

    @Test
    void scan_reportsEveryPackageThatDirectlyHoldsAMetadataClass() throws IOException {
        var profiles = scanExport();

        assertThat(profiles)
                .extracting(XmiProfile::name)
                .containsExactly("Topology", "Meßwerte", "CoreEquipment-EU");
    }

    @Test
    void scan_readsTheEncodingTheFileDeclares() throws IOException {
        // The package name holds a windows-1252 ß, which is not valid UTF-8 on its own, so a
        // reader that assumes UTF-8 cannot produce this name.
        assertThat(scanExport()).extracting(XmiProfile::name).contains("Meßwerte");
    }

    @Test
    void scan_skipsAPackageWhoseVersionClassDeclaresNoProfileUri() throws IOException {
        assertThat(scanExport()).extracting(XmiProfile::name).doesNotContain("DomainProfile");
    }

    @Test
    void scan_takesOnlyTheAttributesNamedLikeAProfileUri() throws IOException {
        // TopologyVersion also declares differenceModelURI, which is an absolute IRI but not a
        // profile URI, and baseUML and shortName, which are not IRIs at all.
        assertThat(profileNamed("Topology").namespaceCandidates())
                .containsExactly("http://iec.ch/TC57/2013/61970-456/Topology/4");
    }

    @Test
    void scan_stripsTheLeadingSpaceAReleasedProfilePutsBeforeItsUri() throws IOException {
        assertThat(profileNamed("Topology").namespaceCandidates().getFirst()).doesNotStartWith(" ");
    }

    @Test
    void scan_findsTheVersionIriConventionAsWellAsTheBaseUriOne() throws IOException {
        assertThat(profileNamed("CoreEquipment-EU").namespaceCandidates())
                .containsExactly("http://iec.ch/TC57/ns/CIM/CoreEquipment-EU/3.0");
    }

    @Test
    void scan_stopsAtTheEndOfTheModelSection() throws IOException {
        // The vendor extension repeats packages and classes of its own; reading on would take
        // them for profiles.
        assertThat(scanExport()).extracting(XmiProfile::name).doesNotContain("EA_PrimitiveTypes");
    }

    @Test
    void scan_reportsTheIdOfThePackageItFound() throws IOException {
        assertThat(profileNamed("Topology").id()).isEqualTo("EAPK_TOPOLOGY");
    }

    @Test
    void scan_onAFileThatIsNotXml_fails() {
        try (InputStream notXml =
                new ByteArrayInputStream(
                        "this is not an export".getBytes(StandardCharsets.UTF_8))) {
            assertThatThrownBy(() -> XmiModelScanner.scan(notXml))
                    .isInstanceOf(XmiReadException.class);
        } catch (IOException exception) {
            throw new AssertionError(exception);
        }
    }

    private static List<XmiProfile> scanExport() throws IOException {
        try (var export = Files.newInputStream(EXPORT)) {
            return XmiModelScanner.scan(export);
        }
    }

    private static XmiProfile profileNamed(String name) throws IOException {
        return scanExport().stream()
                .filter(profile -> name.equals(profile.name()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No profile named " + name + " was found."));
    }
}

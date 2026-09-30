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

package org.rdfarchitect.services.update.graph;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.rdfarchitect.services.update.graph.PrefixScanner.ScannedFile;

import java.util.List;
import java.util.Map;

class PrefixComparerTest {

    private static final String CIM16 = "http://iec.ch/TC57/2013/CIM-schema-cim16#";
    private static final String CIM18 = "http://iec.ch/TC57/2023/CIM-schema-cim18#";
    private static final String RDFS = "http://www.w3.org/2000/01/rdf-schema#";

    @Test
    void compare_prefixBoundToAnotherNamespaceInTheDataset_isContested() {
        var comparison =
                PrefixComparer.compare(
                        Map.of("cim", CIM16), List.of(file("dl30.ttl", Map.of("cim", CIM18))));

        assertThat(comparison)
                .singleElement()
                .satisfies(
                        entry -> {
                            assertThat(entry.prefix()).isEqualTo("cim:");
                            assertThat(entry.contested()).isTrue();
                            assertThat(entry.workspace().iri()).isEqualTo(CIM16);
                            assertThat(entry.workspace().fileNames()).isEmpty();
                            assertThat(entry.imported())
                                    .singleElement()
                                    .satisfies(
                                            imported -> {
                                                assertThat(imported.iri()).isEqualTo(CIM18);
                                                assertThat(imported.fileNames())
                                                        .containsExactly("dl30.ttl");
                                            });
                        });
    }

    @Test
    void compare_samePrefixAndNamespaceOnBothSides_isNotContested() {
        var comparison =
                PrefixComparer.compare(
                        Map.of("cim", CIM16), List.of(file("dl24.ttl", Map.of("cim", CIM16))));

        assertThat(comparison)
                .singleElement()
                .satisfies(
                        entry -> {
                            assertThat(entry.contested()).isFalse();
                            assertThat(entry.workspace().iri()).isEqualTo(CIM16);
                            assertThat(entry.imported())
                                    .singleElement()
                                    .extracting(PrefixBinding::iri)
                                    .isEqualTo(CIM16);
                        });
    }

    @Test
    void compare_prefixOnlyOneSideKnows_isListedWithoutTheOther() {
        var comparison =
                PrefixComparer.compare(
                        Map.of("dcat", "http://www.w3.org/ns/dcat#"),
                        List.of(
                                file(
                                        "dl30.ttl",
                                        Map.of("md", "http://iec.ch/TC57/61970-552/Metadata#"))));

        assertThat(comparison).extracting(PrefixComparison::prefix).containsExactly("dcat:", "md:");
        assertThat(comparison)
                .filteredOn(entry -> entry.prefix().equals("dcat:"))
                .singleElement()
                .satisfies(
                        entry -> {
                            assertThat(entry.imported()).isEmpty();
                            assertThat(entry.contested()).isFalse();
                        });
        assertThat(comparison)
                .filteredOn(entry -> entry.prefix().equals("md:"))
                .singleElement()
                .satisfies(
                        entry -> {
                            assertThat(entry.workspace()).isNull();
                            assertThat(entry.contested()).isFalse();
                        });
    }

    @Test
    void compare_filesDisagreeingAmongThemselves_areContestedWithoutTheDataset() {
        var comparison =
                PrefixComparer.compare(
                        Map.of(),
                        List.of(
                                file("a.ttl", Map.of("cim", CIM16)),
                                file("b.ttl", Map.of("cim", CIM18))));

        assertThat(comparison)
                .singleElement()
                .satisfies(
                        entry -> {
                            assertThat(entry.contested()).isTrue();
                            assertThat(entry.workspace()).isNull();
                            assertThat(entry.imported())
                                    .extracting(PrefixBinding::iri)
                                    .containsExactly(CIM16, CIM18);
                        });
    }

    @Test
    void compare_sameBindingInSeveralFiles_isOneEntryNamingThemAll() {
        var comparison =
                PrefixComparer.compare(
                        Map.of("cim", CIM16),
                        List.of(
                                file("a.ttl", Map.of("cim", CIM18)),
                                file("b.ttl", Map.of("cim", CIM18))));

        assertThat(comparison)
                .singleElement()
                .extracting(entry -> entry.imported().getFirst().fileNames())
                .isEqualTo(List.of("a.ttl", "b.ttl"));
    }

    @Test
    void compare_defaultNamespace_isComparedAsTheEmptyPrefix() {
        var comparison =
                PrefixComparer.compare(
                        Map.of("", CIM16), List.of(file("a.ttl", Map.of("", CIM18))));

        assertThat(comparison)
                .singleElement()
                .satisfies(
                        entry -> {
                            assertThat(entry.prefix()).isEqualTo(":");
                            assertThat(entry.contested()).isTrue();
                        });
    }

    @Test
    void compare_entries_areOrderedByPrefix() {
        var comparison =
                PrefixComparer.compare(
                        Map.of("rdfs", RDFS),
                        List.of(
                                file(
                                        "a.ttl",
                                        Map.of("cim", CIM16, "md", "http://example.com/md#"))));

        assertThat(comparison)
                .extracting(PrefixComparison::prefix)
                .containsExactly("cim:", "md:", "rdfs:");
    }

    @Test
    void compare_nothingToImport_stillListsTheDataset() {
        var comparison = PrefixComparer.compare(Map.of("cim", CIM16), List.of());

        assertThat(comparison)
                .singleElement()
                .satisfies(
                        entry -> {
                            assertThat(entry.imported()).isEmpty();
                            assertThat(entry.contested()).isFalse();
                        });
    }

    private ScannedFile file(String fileName, Map<String, String> prefixes) {
        return new ScannedFile(fileName, prefixes, true);
    }
}

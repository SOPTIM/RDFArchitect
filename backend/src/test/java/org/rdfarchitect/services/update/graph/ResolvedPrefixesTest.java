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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.shared.impl.PrefixMappingImpl;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.exception.graph.InvalidPrefixException;
import org.rdfarchitect.services.update.graph.PrefixResolutionDTO.Action;

import java.util.List;
import java.util.Map;

class ResolvedPrefixesTest {

    private static final String CIM16 = "http://iec.ch/TC57/2013/CIM-schema-cim16#";
    private static final String CIM18 = "http://iec.ch/TC57/2023/CIM-schema-cim18#";
    private static final String RDFS = "http://www.w3.org/2000/01/rdf-schema#";

    private final PrefixComparison comparison =
            new PrefixComparison(
                    "cim:",
                    new PrefixBinding(CIM16, List.of()),
                    List.of(new PrefixBinding(CIM18, List.of("dl30.ttl"))),
                    true);

    @Test
    void applyTo_withoutAnAnswer_leavesThePrefixWithTheDataset() {
        var resolutions = ResolvedPrefixes.of(List.of(comparison), List.of());

        assertThat(applyToFile(resolutions, Map.of("cim", CIM18))).isEmpty();
        assertThat(applyToWorkspace(resolutions, Map.of("cim", CIM16)))
                .containsExactly(entry("cim", CIM16));
    }

    @Test
    void applyTo_droppedImport_isImportedWithoutAPrefix() {
        var resolutions =
                ResolvedPrefixes.of(
                        List.of(comparison),
                        List.of(new PrefixResolutionDTO("cim:", CIM18, Action.DROP, null)));

        assertThat(applyToFile(resolutions, Map.of("cim", CIM18))).isEmpty();
    }

    @Test
    void applyTo_renamedImport_isBoundToTheNewPrefix() {
        var resolutions =
                ResolvedPrefixes.of(
                        List.of(comparison),
                        List.of(new PrefixResolutionDTO("cim:", CIM18, Action.RENAME, "cim3:")));

        assertThat(applyToFile(resolutions, Map.of("cim", CIM18)))
                .containsExactly(entry("cim3", CIM18));
    }

    @Test
    void applyTo_importKeepingThePrefix_takesItFromTheDataset() {
        var resolutions =
                ResolvedPrefixes.of(
                        List.of(comparison),
                        List.of(new PrefixResolutionDTO("cim:", CIM18, Action.KEEP, null)));

        assertThat(applyToFile(resolutions, Map.of("cim", CIM18)))
                .containsExactly(entry("cim", CIM18));
        assertThat(applyToWorkspace(resolutions, Map.of("cim", CIM16))).isEmpty();
    }

    @Test
    void applyTo_renamedDatasetBinding_makesRoomForTheImport() {
        var resolutions =
                ResolvedPrefixes.of(
                        List.of(comparison),
                        List.of(
                                new PrefixResolutionDTO("cim:", CIM16, Action.RENAME, "cim16:"),
                                new PrefixResolutionDTO("cim:", CIM18, Action.KEEP, null)));

        assertThat(applyToWorkspace(resolutions, Map.of("cim", CIM16)))
                .containsExactly(entry("cim16", CIM16));
        assertThat(applyToFile(resolutions, Map.of("cim", CIM18)))
                .containsExactly(entry("cim", CIM18));
    }

    @Test
    void applyTo_datasetBindingRenamedWithoutASuccessor_leavesThePrefixToNobody() {
        var resolutions =
                ResolvedPrefixes.of(
                        List.of(comparison),
                        List.of(new PrefixResolutionDTO("cim:", CIM16, Action.RENAME, "cim16:")));

        assertThat(applyToWorkspace(resolutions, Map.of("cim", CIM16)))
                .containsExactly(entry("cim16", CIM16));
        assertThat(applyToFile(resolutions, Map.of("cim", CIM18))).isEmpty();
    }

    @Test
    void applyTo_twoBindingsTradingPrefixes_keepsBoth() {
        var resolutions =
                ResolvedPrefixes.of(
                        List.of(comparisonOf("a:", CIM16), comparisonOf("b:", CIM18)),
                        List.of(
                                new PrefixResolutionDTO("a:", CIM16, Action.RENAME, "b:"),
                                new PrefixResolutionDTO("b:", CIM18, Action.RENAME, "a:")));

        assertThat(applyToFile(resolutions, Map.of("a", CIM16, "b", CIM18)))
                .containsOnly(entry("b", CIM16), entry("a", CIM18));
    }

    @Test
    void of_twoNamespacesSentToTheSamePrefix_isRejected() {
        assertThatThrownBy(
                        () ->
                                ResolvedPrefixes.of(
                                        List.of(comparison),
                                        List.of(
                                                new PrefixResolutionDTO(
                                                        "cim:", CIM16, Action.KEEP, null),
                                                new PrefixResolutionDTO(
                                                        "cim:", CIM18, Action.KEEP, null))))
                .isInstanceOf(InvalidPrefixException.class);
    }

    @Test
    void of_renameIntoAPrefixAnotherNamespaceKeeps_isRejected() {
        assertThatThrownBy(
                        () ->
                                ResolvedPrefixes.of(
                                        List.of(comparison),
                                        List.of(
                                                new PrefixResolutionDTO(
                                                        "rdfs:", RDFS, Action.RENAME, "cim:"),
                                                new PrefixResolutionDTO(
                                                        "cim:", CIM16, Action.KEEP, null))))
                .isInstanceOf(InvalidPrefixException.class);
    }

    @Test
    void of_renameIntoAPrefixNobodyAnsweredFor_isRejected() {
        assertThatThrownBy(
                        () ->
                                ResolvedPrefixes.of(
                                        List.of(comparison, comparisonOf("rdfs:", RDFS)),
                                        List.of(
                                                new PrefixResolutionDTO(
                                                        "cim:", CIM18, Action.RENAME, "rdfs:"))))
                .isInstanceOf(InvalidPrefixException.class);
    }

    @Test
    void of_renameIntoAPrefixItsHolderGivesUp_isAllowed() {
        ResolvedPrefixes.of(
                List.of(comparison, comparisonOf("rdfs:", RDFS)),
                List.of(
                        new PrefixResolutionDTO("rdfs:", RDFS, Action.RENAME, "schema:"),
                        new PrefixResolutionDTO("cim:", CIM18, Action.RENAME, "rdfs:")));
    }

    @Test
    void of_renameIntoAPrefixNobodyHolds_isAllowed() {
        ResolvedPrefixes.of(
                List.of(comparison),
                List.of(new PrefixResolutionDTO("cim:", CIM18, Action.RENAME, "cim18:")));
    }

    @Test
    void of_oneNamespaceUnderTwoPrefixes_endsOnOneOfThem() {
        ResolvedPrefixes.of(
                List.of(comparison),
                List.of(
                        new PrefixResolutionDTO("cim:", CIM18, Action.RENAME, "cim18:"),
                        new PrefixResolutionDTO("c:", CIM18, Action.RENAME, "cim18:")));
    }

    @Test
    void rewriteWorkspacePrefixes_withoutAChange_staysEmpty() {
        var resolutions = ResolvedPrefixes.of(List.of(comparison), List.of());
        var current = prefixMapping(Map.of("cim", CIM16));

        assertThat(resolutions.rewriteWorkspacePrefixes(current)).isEmpty();
    }

    @Test
    void applyTo_bindingsNobodyDecidedOn_areLeftAlone() {
        var resolutions = ResolvedPrefixes.of(List.of(comparison), List.of());

        assertThat(
                        applyToFile(
                                resolutions,
                                Map.of("rdfs", "http://www.w3.org/2000/01/rdf-schema#")))
                .containsExactly(entry("rdfs", "http://www.w3.org/2000/01/rdf-schema#"));
    }

    @Test
    void of_answerToAPrefixThatWasNotCompared_changesNothing() {
        var resolutions =
                ResolvedPrefixes.of(
                        List.of(comparison),
                        List.of(
                                new PrefixResolutionDTO(
                                        "rdfs:",
                                        "http://www.w3.org/2000/01/rdf-schema#",
                                        Action.RENAME,
                                        "schema:")));

        assertThat(
                        applyToFile(
                                resolutions,
                                Map.of("rdfs", "http://www.w3.org/2000/01/rdf-schema#")))
                .containsExactly(entry("rdfs", "http://www.w3.org/2000/01/rdf-schema#"));
    }

    @Test
    void of_withoutAComparison_leavesEveryPrefixAlone() {
        var resolutions = ResolvedPrefixes.of(List.of(), List.of());

        assertThat(applyToFile(resolutions, Map.of("cim", CIM18)))
                .containsExactly(entry("cim", CIM18));
    }

    @Test
    void of_renameToSomethingRdfCannotUse_isRejected() {
        assertThatThrownBy(
                        () ->
                                ResolvedPrefixes.of(
                                        List.of(comparison),
                                        List.of(
                                                new PrefixResolutionDTO(
                                                        "cim:", CIM18, Action.RENAME, "2cim:"))))
                .isInstanceOf(InvalidPrefixException.class);
    }

    @Test
    void of_renameToNothing_isRejected() {
        assertThatThrownBy(
                        () ->
                                ResolvedPrefixes.of(
                                        List.of(comparison),
                                        List.of(
                                                new PrefixResolutionDTO(
                                                        "cim:", CIM18, Action.RENAME, ":"))))
                .isInstanceOf(InvalidPrefixException.class);
    }

    @Test
    void of_otherActions_needNoPrefix() {
        ResolvedPrefixes.of(
                List.of(comparison),
                List.of(
                        new PrefixResolutionDTO("cim:", CIM18, Action.DROP, null),
                        new PrefixResolutionDTO("cim:", CIM18, Action.KEEP, null)));
    }

    /** A prefix one file of the import brings and the dataset does not know. */
    private PrefixComparison comparisonOf(String prefix, String iri) {
        return new PrefixComparison(
                prefix, null, List.of(new PrefixBinding(iri, List.of("dl30.ttl"))), false);
    }

    private Map<String, String> applyToFile(
            ResolvedPrefixes resolutions, Map<String, String> prefixes) {
        var prefixMapping = prefixMapping(prefixes);
        resolutions.applyTo(prefixMapping);
        return prefixMapping.getNsPrefixMap();
    }

    private Map<String, String> applyToWorkspace(
            ResolvedPrefixes resolutions, Map<String, String> prefixes) {
        return resolutions
                .rewriteWorkspacePrefixes(prefixMapping(prefixes))
                .map(PrefixMapping::getNsPrefixMap)
                .orElse(prefixes);
    }

    private PrefixMapping prefixMapping(Map<String, String> prefixes) {
        return new PrefixMappingImpl().setNsPrefixes(prefixes);
    }
}

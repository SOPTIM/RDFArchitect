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

package org.rdfarchitect.xmi.map;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.xmi.map.XmiDiagramAvailability.Availability;
import org.rdfarchitect.xmi.map.XmiDiagramAvailability.DiagramDependence;
import org.rdfarchitect.xmi.map.XmiDiagramAvailability.Selection;
import org.rdfarchitect.xmi.read.XmiIndex;
import org.rdfarchitect.xmi.read.XmiPackageScope;
import org.rdfarchitect.xmi.read.XmiProfileSlice;
import org.rdfarchitect.xmi.read.XmiReader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * What a selection has to cover for a diagram to be importable.
 *
 * <p>Worked out at read time so that the caller can answer it per tick, and it has to agree with
 * what {@link XmiDiagrams} does afterwards: a diagram offered and then dropped, or dropped and then
 * written, would make the choice a lie.
 */
class XmiDiagramAvailabilityTest {

    private static final Path EXPORT =
            Path.of("src/test/java/org/rdfarchitect/xmi/read/testdata/profiles-export.xml");

    private static final String TOPOLOGY = "EAPK_TOPOLOGY";
    private static final String TOPOLOGY_CORE = "EAPK_TOPOLOGY_INNER";
    private static final String DOMAIN = "EAPK_DOMAIN";
    private static final String CORE_EQUIPMENT = "EAPK_COREEQUIPMENT";
    private static final String CONFIGURATION = "EAPK_CONFIG";

    private static final String TOPOLOGY_DIAGRAM = "Topology overview";
    private static final String CONFIGURATION_DIAGRAM = "Configuration";
    private static final String NOTES_DIAGRAM = "Notes";
    private static final String INNER_DIAGRAM = "TopologyCore";

    private static XmiIndex index;
    private static Availability availability;

    @BeforeAll
    static void readTheExport() throws IOException {
        try (var file = Files.newInputStream(EXPORT)) {
            index = XmiReader.read(file);
        }
        availability = XmiDiagramAvailability.of(index);
    }

    @Test
    void of_reportsTheDiagramsThatDrawClasses() {
        assertThat(availability.diagrams())
                .extracting(DiagramDependence::name)
                .containsExactly(
                        NOTES_DIAGRAM, INNER_DIAGRAM, CONFIGURATION_DIAGRAM, TOPOLOGY_DIAGRAM);
    }

    @Test
    void of_offersADiagramUnderThePackageDeclaringIt() {
        assertThat(diagram(CONFIGURATION_DIAGRAM).packageId()).isEqualTo("EAPK_CONFIG");
    }

    @Test
    void of_offersADiagramOfAClasslessPackageUnderTheNearestOfferedOne() {
        // Notes holds no class, so it is no schema and has no row to be shown under.
        assertThat(diagram(NOTES_DIAGRAM).packageId()).isEqualTo("EAPK_ROOT");
    }

    @Test
    void of_offersADiagramInsideAProfileUnderTheProfile() {
        // A profile is taken whole, so its sub-packages are not offered separately.
        assertThat(diagram(INNER_DIAGRAM).packageId()).isEqualTo(TOPOLOGY);
    }

    @Test
    void of_leavesOutADiagramThatDrawsNoClass() {
        // The package diagram draws a package, which no selection can supply.
        assertThat(availability.diagrams())
                .extracting(DiagramDependence::name)
                .doesNotContain("Package dependencies");
    }

    @Test
    void of_countsOnlyTheShapesThatStandForAClass() {
        // The diagram also draws a boundary, which the reader dropped from the index.
        assertThat(diagram(TOPOLOGY_DIAGRAM).classes()).isEqualTo(2);
    }

    @Test
    void of_doesNotCountAProfilesOwnMetadataClassAmongTheShapes() {
        // The Configuration diagram also draws CoreEquipment-EU_Ontology, which becomes that
        // profile's ontology header and never a class, so no selection has to supply it.
        assertThat(diagram(CONFIGURATION_DIAGRAM).classes()).isEqualTo(1);
    }

    @Test
    void isAvailable_whenTheSelectionSuppliesEveryClassDrawn() {
        assertThat(available(TOPOLOGY_DIAGRAM, withSubPackages(TOPOLOGY))).isTrue();
    }

    @Test
    void isAvailable_notWhileAClassItDrawsHasNoSupplier() {
        assertThat(available(TOPOLOGY_DIAGRAM, withSubPackages(DOMAIN))).isFalse();
    }

    @Test
    void isAvailable_countsAPackageThatSuppliesTheClassAsADependency() {
        // TopologyCore declares the classes the diagram draws, so picking it supplies them too.
        assertThat(available(TOPOLOGY_DIAGRAM, withSubPackages(TOPOLOGY_CORE))).isTrue();
    }

    @Test
    void isAvailable_notWhenThePackageIsTakenAloneAndASubPackageDeclaresTheClasses() {
        assertThat(available(TOPOLOGY_DIAGRAM, packageOnly(TOPOLOGY))).isFalse();
    }

    @Test
    void isAvailable_whenThePackageTakenAloneSuppliesThemItself() {
        assertThat(available(TOPOLOGY_DIAGRAM, packageOnly(TOPOLOGY_CORE))).isTrue();
    }

    @Test
    void isAvailable_notThroughAClassOnlyTheHeaderReaches() {
        // The CoreEquipment header points at ConfigurationDataset, and the header is not written,
        // so neither is the class: offering the diagram that draws it would promise nothing.
        assertThat(available(CONFIGURATION_DIAGRAM, withSubPackages(CORE_EQUIPMENT))).isFalse();
    }

    @Test
    void isAvailable_agreesWithWhatTheImportThenWrites() {
        // The one thing that must hold: offered means written, and not offered means not written.
        assertThat(writtenFor(TOPOLOGY)).containsExactlyElementsOf(offeredFor(TOPOLOGY));
    }

    @Test
    void isAvailable_agreesWithWhatTheImportWritesForAProfileWithAHeader() {
        assertThat(writtenFor(CORE_EQUIPMENT))
                .containsExactlyElementsOf(offeredFor(CORE_EQUIPMENT));
    }

    @Test
    void isAvailable_agreesForADiagramThatDrawsAnotherProfilesHeader() {
        // The Configuration diagram draws CoreEquipment-EU_Ontology beside its own class. Both
        // sides pass over it, so the diagram is offered and then written rather than dropped.
        assertThat(writtenFor(CONFIGURATION)).containsExactlyElementsOf(offeredFor(CONFIGURATION));
        assertThat(offeredFor(CONFIGURATION)).isNotEmpty();
    }

    /** The export ids of the diagrams an import of {@code packageId} would store. */
    private List<String> writtenFor(String packageId) {
        var locations = new HashMap<String, XmiDiagrams.ClassLocation>();
        XmiProfileSlice.classIdsOf(
                        index,
                        packageId,
                        XmiPackageScope.WITH_SUB_PACKAGES,
                        headerClassIdsOf(packageId))
                .forEach(
                        classId ->
                                locations.put(
                                        classId,
                                        new XmiDiagrams.ClassLocation(
                                                UUID.randomUUID(),
                                                "http://example.com#" + classId,
                                                "urn:g")));
        return XmiDiagrams.of(index, locations).stream()
                .map(XmiDiagrams.ImportedDiagram::exportId)
                .toList();
    }

    /** The export ids of the diagrams choosing {@code packageId} offers. */
    private List<String> offeredFor(String packageId) {
        return availability.diagrams().stream()
                .filter(
                        diagram ->
                                XmiDiagramAvailability.isAvailable(
                                        diagram,
                                        availability.supplierGroups(),
                                        withSubPackages(packageId)))
                .map(DiagramDependence::id)
                .toList();
    }

    private Set<String> headerClassIdsOf(String packageId) {
        return index.getProfiles().stream()
                .filter(profile -> profile.id().equals(packageId))
                .findFirst()
                .map(index::headerClassIdsOf)
                .orElse(Set.of());
    }

    private boolean available(String diagramName, Selection selection) {
        return XmiDiagramAvailability.isAvailable(
                diagram(diagramName), availability.supplierGroups(), selection);
    }

    private DiagramDependence diagram(String name) {
        return availability.diagrams().stream()
                .filter(diagram -> diagram.name().equals(name))
                .findFirst()
                .orElseThrow();
    }

    private static Selection withSubPackages(String packageId) {
        return Selection.withSubPackages(Set.of(packageId));
    }

    private static Selection packageOnly(String packageId) {
        return new Selection(Set.of(), Set.of(packageId));
    }
}

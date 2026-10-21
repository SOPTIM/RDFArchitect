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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.xmi.read.XmiPackageTree.XmiPackageNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * What the choice of what to import is made over.
 *
 * <p>Every package can become a schema, so what matters here is that a package is reported with the
 * numbers it would actually produce — a package is written as its classes plus the closure over
 * their types and superclasses, and the two differ by enough to mislead.
 */
class XmiPackageTreeTest {

    private static final Path EXPORT =
            Path.of("src/test/java/org/rdfarchitect/xmi/read/testdata/profiles-export.xml");

    private static XmiIndex index;

    @BeforeAll
    static void readTheExport() throws IOException {
        try (var file = Files.newInputStream(EXPORT)) {
            index = XmiReader.read(file);
        }
    }

    @Test
    void of_reportsAPackageThatIsNoProfileToo() {
        // TopologyCore declares no metadata class and is a schema like any other.
        assertThat(nodeNamed("TopologyCore").profile()).isFalse();
        assertThat(nodeNamed("TopologyCore").declaredClasses()).isPositive();
    }

    @Test
    void of_marksThePackagesThatDeclareAProfile() {
        assertThat(XmiPackageTree.of(index))
                .filteredOn(XmiPackageNode::profile)
                .extracting(XmiPackageNode::name)
                .containsExactlyInAnyOrder("Topology", "Meßwerte", "CoreEquipment-EU");
    }

    @Test
    void of_countsTheClassesOfTheSubPackagesAsTheOutersOwn() {
        // Topology itself declares two metadata classes; TopologyCore below it holds the rest.
        assertThat(nodeNamed("Topology").declaredClasses())
                .isGreaterThan(nodeNamed("TopologyCore").declaredClasses());
    }

    @Test
    void of_reportsWhatTheClosureAddsApartFromWhatThePackageDeclares() {
        // The datatypes of Topology live in another profile's package, so they are dependencies
        // rather than declarations — and they end up in the schema all the same.
        var topology = nodeNamed("Topology");

        assertThat(topology.dependencyClasses()).isPositive();
        assertThat(topology.declaredClasses() + topology.dependencyClasses())
                .isEqualTo(XmiProfileSlice.classIdsOf(index, topology.id()).size());
    }

    @Test
    void of_countsThePackagesOwnClassesApartFromItsSubPackages() {
        // What a container's own classes become when its sub-packages are chosen separately. Of
        // the CGMES 3.0 model, 162 classes sit like that and would be unreachable otherwise.
        var topology = nodeNamed("Topology");

        assertThat(topology.ownClasses()).isLessThan(topology.declaredClasses());
        assertThat(topology.ownClasses() + topology.ownDependencyClasses())
                .isEqualTo(
                        XmiProfileSlice.classIdsOf(
                                        index, topology.id(), XmiPackageScope.PACKAGE_ONLY)
                                .size());
    }

    @Test
    void of_reportsNoOwnClassesForAPackageThatOnlyHoldsOthers() {
        assertThat(nodeNamed("ExampleStandard").ownClasses()).isZero();
    }

    @Test
    void of_namesThePackageContainingEachOne() {
        assertThat(nodeNamed("TopologyCore").parentId()).isEqualTo(nodeNamed("Topology").id());
        assertThat(nodeNamed("ExampleStandard").parentId()).isNull();
    }

    @Test
    void of_leavesOutAPackageThatWouldWriteNothing() {
        // No class below it and no header of its own; an export carries plenty of those.
        assertThat(XmiPackageTree.of(index))
                .allSatisfy(
                        node ->
                                assertThat(
                                                node.declaredClasses() > 0
                                                        || index.headerClassIdsOf(
                                                                                index.schemaOf(
                                                                                        node.id()))
                                                                        .size()
                                                                > 0)
                                        .isTrue());
    }

    @Test
    void of_keepsAPackageWhoseOnlyContentIsItsHeader() {
        // CoreEquipment-EU declares nothing but the class describing it, and a schema of just
        // that header is still a schema.
        assertThat(nodeNamed("CoreEquipment-EU").declaredClasses()).isZero();
    }

    @Test
    void of_doesNotCountTheHeaderClassOrWhatItRefersTo() {
        // The header is written as an owl:Ontology and is no starting point of the closure, so
        // the datatypes of its twenty fields are not part of the schema either.
        assertThat(nodeNamed("CoreEquipment-EU").dependencyClasses()).isZero();
    }

    // -------------------------------------------------------------------------

    private static XmiPackageNode nodeNamed(String name) {
        return XmiPackageTree.of(index).stream()
                .filter(node -> name.equals(node.name()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No package named " + name));
    }
}

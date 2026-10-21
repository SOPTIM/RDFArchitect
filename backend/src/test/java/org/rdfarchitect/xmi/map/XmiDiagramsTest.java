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
import static org.assertj.core.api.Assertions.tuple;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.xmi.map.XmiDiagrams.ClassLocation;
import org.rdfarchitect.xmi.map.XmiDiagrams.ImportedDiagram;
import org.rdfarchitect.xmi.map.XmiDiagrams.PlacedClass;
import org.rdfarchitect.xmi.read.XmiIndex;
import org.rdfarchitect.xmi.read.XmiReader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Which diagrams of an export survive the import, and what they hold.
 *
 * <p>The fixture's topology diagram draws two classes and one boundary, and its package diagram
 * draws a package — between them that is every case there is: a shape standing for a class, one
 * standing for something the editor has no concept of, and a diagram with no class on it at all.
 */
class XmiDiagramsTest {

    private static final Path EXPORT =
            Path.of("src/test/java/org/rdfarchitect/xmi/read/testdata/profiles-export.xml");

    private static final String GRAPH = "urn:graph:topology";
    private static final String TOPOLOGY_DIAGRAM = "Topology overview";

    private static XmiIndex index;

    @BeforeAll
    static void readTheExport() throws IOException {
        try (var file = Files.newInputStream(EXPORT)) {
            index = XmiReader.read(file);
        }
    }

    @Test
    void of_takesTheClassesADiagramDrawsWhereItDrawsThem() {
        // Pulled apart by half as it goes in: a class of ours needs more room than the editor
        // gave it, and the arrangement is kept by moving every class by the same factor.
        var diagram = topologyDiagram(everythingWritten());

        assertThat(diagram.classes())
                .extracting(PlacedClass::name, PlacedClass::x, PlacedClass::y)
                .containsExactlyInAnyOrder(
                        tuple("TopologicalNode", 150f, 300f), tuple("Terminal", 450f, 600f));
    }

    @Test
    void of_keepsTheArrangementWhilePullingItApart() {
        // Whatever the factor, what was twice as far away stays twice as far away.
        var placed = topologyDiagram(everythingWritten()).classes();
        var one = placed.getFirst();
        var other = placed.getLast();

        assertThat((other.x() - one.x()) / (other.y() - one.y()))
                .isEqualTo((300f - 100f) / (400f - 200f));
    }

    @Test
    void of_ordersTheClassesAsTheExportStacksThem() {
        // The drawing lists TopologicalNode first and the extension gives Terminal the lower
        // seqno. Stored positions carry no z, so the order they go in is the only thing left to
        // reproduce it with.
        var diagram = topologyDiagram(everythingWritten());

        assertThat(diagram.classes())
                .extracting(PlacedClass::name)
                .containsExactly("Terminal", "TopologicalNode");
    }

    @Test
    void of_passesOverAShapeThatStandsForNoClass() {
        // The diagram also draws a boundary, which the reader has already dropped from the index.
        var diagram = topologyDiagram(everythingWritten());

        assertThat(diagram.classes()).hasSize(2);
    }

    @Test
    void of_namesADiagramAsTheExtensionDoes() {
        assertThat(topologyDiagram(everythingWritten()).name()).isEqualTo("Topology overview");
    }

    @Test
    void of_leavesOutADiagramDrawingAClassThatWasNotWritten() {
        // Half a diagram is worse than none: it looks complete and is not.
        var withoutTerminal = everythingWritten();
        withoutTerminal.remove("EAID_TERMINAL");

        assertThat(XmiDiagrams.of(index, withoutTerminal))
                .extracting(ImportedDiagram::name)
                .doesNotContain(TOPOLOGY_DIAGRAM);
    }

    @Test
    void of_leavesOutADiagramThatDrawsNoClassAtAll() {
        // The fixture's other diagram draws a package, which is no class and so places nothing.
        assertThat(XmiDiagrams.of(index, everythingWritten()))
                .extracting(ImportedDiagram::name)
                .doesNotContain("Package dependencies");
    }

    @Test
    void of_givesEveryDiagramAnIdentityOfItsOwn() {
        var first = topologyDiagram(everythingWritten());
        var second = topologyDiagram(everythingWritten());

        assertThat(first.id()).isNotEqualTo(second.id());
    }

    // -------------------------------------------------------------------------

    private static ImportedDiagram topologyDiagram(Map<String, ClassLocation> locations) {
        return XmiDiagrams.of(index, locations).stream()
                .filter(diagram -> TOPOLOGY_DIAGRAM.equals(diagram.name()))
                .findFirst()
                .orElseThrow();
    }

    /** Every class of the export written into one schema, which is the uneventful case. */
    private static Map<String, ClassLocation> everythingWritten() {
        var locations = new HashMap<String, ClassLocation>();
        index.getClasses()
                .forEach(
                        (classId, cimClass) ->
                                locations.put(
                                        classId,
                                        new ClassLocation(
                                                UUID.randomUUID(),
                                                "http://example.com/ns/CIM#" + cimClass.getName(),
                                                GRAPH)));
        return locations;
    }
}

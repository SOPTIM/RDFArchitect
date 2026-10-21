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
 * <p>The fixture's class diagram draws two classes and one boundary, and its package diagram draws
 * a package — between them that is every case there is: a shape standing for a class, one standing
 * for something the editor has no concept of, and a diagram with no class on it at all.
 */
class XmiDiagramsTest {

    private static final Path EXPORT =
            Path.of("src/test/java/org/rdfarchitect/xmi/read/testdata/profiles-export.xml");

    private static final String GRAPH = "urn:graph:topology";

    private static XmiIndex index;

    @BeforeAll
    static void readTheExport() throws IOException {
        try (var file = Files.newInputStream(EXPORT)) {
            index = XmiReader.read(file);
        }
    }

    @Test
    void of_takesTheClassesADiagramDrawsWhereItDrawsThem() {
        var diagram = onlyDiagram(everythingWritten());

        assertThat(diagram.classes())
                .extracting(PlacedClass::name, PlacedClass::x, PlacedClass::y)
                .containsExactlyInAnyOrder(
                        tuple("TopologicalNode", 100f, 200f), tuple("Terminal", 300f, 400f));
    }

    @Test
    void of_ordersTheClassesAsTheExportStacksThem() {
        // The drawing lists TopologicalNode first and the extension gives Terminal the lower
        // seqno. Stored positions carry no z, so the order they go in is the only thing left to
        // reproduce it with.
        var diagram = onlyDiagram(everythingWritten());

        assertThat(diagram.classes())
                .extracting(PlacedClass::name)
                .containsExactly("Terminal", "TopologicalNode");
    }

    @Test
    void of_passesOverAShapeThatStandsForNoClass() {
        // The diagram also draws a boundary, which the reader has already dropped from the index.
        var diagram = onlyDiagram(everythingWritten());

        assertThat(diagram.classes()).hasSize(2);
    }

    @Test
    void of_namesADiagramAsTheExtensionDoes() {
        assertThat(onlyDiagram(everythingWritten()).name()).isEqualTo("Topology overview");
    }

    @Test
    void of_leavesOutADiagramDrawingAClassThatWasNotWritten() {
        // Half a diagram is worse than none: it looks complete and is not.
        var withoutTerminal = everythingWritten();
        withoutTerminal.remove("EAID_TERMINAL");

        assertThat(XmiDiagrams.of(index, withoutTerminal)).isEmpty();
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
        var first = onlyDiagram(everythingWritten());
        var second = onlyDiagram(everythingWritten());

        assertThat(first.id()).isNotEqualTo(second.id());
    }

    // -------------------------------------------------------------------------

    private static ImportedDiagram onlyDiagram(Map<String, ClassLocation> locations) {
        var diagrams = XmiDiagrams.of(index, locations);
        assertThat(diagrams).hasSize(1);
        return diagrams.getFirst();
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

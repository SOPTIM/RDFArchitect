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

import org.apache.jena.query.ReadWrite;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.config.SchemaConfig;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseAdapter;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseImpl;
import org.rdfarchitect.database.inmemory.diagrams.CustomDiagram;
import org.rdfarchitect.dl.queries.select.DLObjectFetcher;
import org.rdfarchitect.services.diagrams.CrossProfileUtils;
import org.rdfarchitect.xmi.map.XmiDiagrams.ClassLocation;
import org.rdfarchitect.xmi.map.XmiDiagrams.ImportedDiagram;
import org.rdfarchitect.xmi.map.XmiDiagrams.PlacedClass;

import java.util.List;
import java.util.UUID;

/**
 * Where a diagram of an export ends up, which is decided by what it draws and not by where it was
 * declared.
 */
class XmiDiagramStoreTest {

    private static final String DATASET = "ds";
    private static final String FIRST = "http://example.com/graphs/first";
    private static final String SECOND = "http://example.com/graphs/second";

    /** A caller that never cancels, which is every case but the one that tests cancelling. */
    private static final java.util.function.BooleanSupplier NEVER = () -> false;

    private DatabasePort databasePort;
    private XmiDiagramStore store;

    @BeforeEach
    void setUp() {
        databasePort = new InMemoryDatabaseAdapter(new InMemoryDatabaseImpl(new SchemaConfig()));
        databasePort.createWorkspaceIfAbsent(DATASET);
        databasePort.createEmptyGraph(new GraphIdentifier(DATASET, FIRST));
        databasePort.createEmptyGraph(new GraphIdentifier(DATASET, SECOND));
        store = new XmiDiagramStore(databasePort);
    }

    @Test
    void store_putsADiagramDrawingOneSchemaWithThatSchema() {
        store.store(DATASET, List.of(diagram(placed("A", FIRST), placed("B", FIRST))), NEVER);

        assertThat(diagramsOfGraph(FIRST)).extracting(CustomDiagram::getName).containsExactly("D");
        assertThat(databasePort.getDatasetDiagrams(DATASET)).isEmpty();
    }

    @Test
    void store_putsADiagramDrawingSeveralSchemasWithTheWorkspace() {
        // No single schema can hold it, and the same Enterprise Architect diagram is one or the
        // other depending only on which profiles were chosen alongside it.
        store.store(DATASET, List.of(diagram(placed("A", FIRST), placed("B", SECOND))), NEVER);

        assertThat(databasePort.getDatasetDiagrams(DATASET).values())
                .extracting(CustomDiagram::getName)
                .containsExactly("D");
        assertThat(diagramsOfGraph(FIRST)).isEmpty();
        assertThat(diagramsOfGraph(SECOND)).isEmpty();
    }

    @Test
    void store_keepsTheClassesOfAWorkspaceDiagram() {
        store.store(DATASET, List.of(diagram(placed("A", FIRST), placed("B", SECOND))), NEVER);

        var diagram = databasePort.getDatasetDiagrams(DATASET).values().iterator().next();
        assertThat(diagram.getClasses()).hasSize(2);
    }

    @Test
    void store_addressesAClassOfAWorkspaceDiagramByItsMergedUuid() {
        // That is how the workspace-wide layout identifies the same class across schemas; the
        // uuid of the class itself would place nothing.
        var drawn = placed("A", FIRST);
        var imported = diagram(drawn, placed("B", SECOND));

        store.store(DATASET, List.of(imported), NEVER);

        var layout = databasePort.getDatasetDiagramLayout(DATASET).getDiagramLayoutModel();
        assertThat(DLObjectFetcher.fetchDiagramDOPPerClass(layout, imported.id()))
                .containsKey(CrossProfileUtils.mergedUuid(drawn.location().classUri()));
    }

    @Test
    void store_reportsHowManyItStored() {
        var stored =
                store.store(
                        DATASET,
                        List.of(
                                diagram(placed("A", FIRST)),
                                diagram(placed("B", FIRST), placed("C", SECOND))),
                        NEVER);

        assertThat(stored).isEqualTo(2);
    }

    @Test
    void store_keepsGoingWhenOneDiagramNamesASchemaThatIsNotThere() {
        // The positions of that one are lost; the schemas it draws are not.
        var stored =
                store.store(
                        DATASET,
                        List.of(
                                diagram(placed("A", "http://example.com/graphs/gone")),
                                diagram(placed("B", FIRST))),
                        NEVER);

        assertThat(stored).isEqualTo(1);
        assertThat(diagramsOfGraph(FIRST)).hasSize(1);
    }

    @Test
    void store_stopsAtTheNextDiagramOnceTheImportIsCancelled() {
        // The longest part of an import by far, so a cancellation noticed only after it would not
        // look like one at all.
        var stored =
                store.store(
                        DATASET,
                        List.of(diagram(placed("A", FIRST)), diagram(placed("B", FIRST))),
                        () -> true);

        assertThat(stored).isZero();
        assertThat(diagramsOfGraph(FIRST)).isEmpty();
    }

    // -------------------------------------------------------------------------

    private static ImportedDiagram diagram(PlacedClass... classes) {
        return new ImportedDiagram(UUID.randomUUID(), "D", List.of(classes));
    }

    private static PlacedClass placed(String name, String graphUri) {
        return new PlacedClass(
                new ClassLocation(UUID.randomUUID(), "http://example.com#" + name, graphUri),
                name,
                1,
                2);
    }

    private List<CustomDiagram> diagramsOfGraph(String graphUri) {
        var graphIdentifier = new GraphIdentifier(DATASET, graphUri);
        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.READ)) {
            return List.copyOf(ctx.getCustomDiagrams().values());
        }
    }
}

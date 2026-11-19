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
import org.apache.jena.sparql.graph.GraphFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.config.SchemaConfig;
import org.rdfarchitect.database.DatabasePort;
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
import java.util.function.BooleanSupplier;

/**
 * Where a diagram of an export ends up, which is decided by what it draws and not by where it was
 * declared.
 */
class XmiDiagramStoreTest {

    private static final String DATASET = "ds";
    private static final String FIRST = "http://example.com/graphs/first";
    private static final String SECOND = "http://example.com/graphs/second";

    /** A caller that never cancels, which is every case but the one that tests cancelling. */
    private static final BooleanSupplier NEVER = () -> false;

    private DatabasePort databasePort;
    private XmiDiagramStore store;

    @BeforeEach
    void setUp() {
        databasePort = new InMemoryDatabaseAdapter(new InMemoryDatabaseImpl(new SchemaConfig()));
        databasePort.createWorkspaceIfAbsent(DATASET);
        try (var transaction = databasePort.beginTransaction(DATASET, ReadWrite.WRITE)) {
            transaction.createGraph(FIRST, GraphFactory.createDefaultGraph());
            transaction.createGraph(SECOND, GraphFactory.createDefaultGraph());
            transaction.commit("schemas to draw on");
        }
        store = new XmiDiagramStore();
    }

    @Test
    void store_putsADiagramDrawingOneSchemaWithThatSchema() {
        store(List.of(diagram(placed("A", FIRST), placed("B", FIRST))), NEVER);

        assertThat(diagramsOfGraph(FIRST)).extracting(CustomDiagram::getName).containsExactly("D");
        assertThat(workspaceDiagrams()).isEmpty();
    }

    @Test
    void store_putsADiagramDrawingSeveralSchemasWithTheWorkspace() {
        // No single schema can hold it, and the same Enterprise Architect diagram is one or the
        // other depending only on which profiles were chosen alongside it.
        store(List.of(diagram(placed("A", FIRST), placed("B", SECOND))), NEVER);

        assertThat(workspaceDiagrams()).extracting(CustomDiagram::getName).containsExactly("D");
        assertThat(diagramsOfGraph(FIRST)).isEmpty();
        assertThat(diagramsOfGraph(SECOND)).isEmpty();
    }

    @Test
    void store_keepsTheClassesOfAWorkspaceDiagram() {
        store(List.of(diagram(placed("A", FIRST), placed("B", SECOND))), NEVER);

        var diagram = workspaceDiagrams().getFirst();
        assertThat(diagram.getClasses()).hasSize(2);
    }

    @Test
    void store_addressesAClassOfAWorkspaceDiagramByItsMergedUuid() {
        // That is how the workspace-wide layout identifies the same class across schemas; the
        // uuid of the class itself would place nothing.
        var drawn = placed("A", FIRST);
        var imported = diagram(drawn, placed("B", SECOND));

        store(List.of(imported), NEVER);

        try (var transaction = databasePort.beginTransaction(DATASET, ReadWrite.READ)) {
            var layout = transaction.layout().getDiagramLayoutModel();
            assertThat(DLObjectFetcher.fetchDiagramDOPPerClass(layout, imported.id()))
                    .containsKey(CrossProfileUtils.mergedUuid(drawn.location().classUri()));
        }
    }

    @Test
    void store_reportsHowManyItStored() {
        var stored =
                store(
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
                store(
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
                store(
                        List.of(diagram(placed("A", FIRST)), diagram(placed("B", FIRST))),
                        () -> true);

        assertThat(stored).isZero();
        assertThat(diagramsOfGraph(FIRST)).isEmpty();
    }

    // -------------------------------------------------------------------------

    private static ImportedDiagram diagram(PlacedClass... classes) {
        return new ImportedDiagram(UUID.randomUUID(), "EAID_D", "D", List.of(classes));
    }

    private static PlacedClass placed(String name, String graphUri) {
        return new PlacedClass(
                new ClassLocation(UUID.randomUUID(), "http://example.com#" + name, graphUri),
                name,
                1,
                2);
    }

    /** Stores the diagrams the way an import does, in a transaction of its own. */
    private int store(List<ImportedDiagram> diagrams, BooleanSupplier cancelled) {
        try (var transaction = databasePort.beginTransaction(DATASET, ReadWrite.WRITE)) {
            var stored = store.store(transaction, diagrams, cancelled);
            transaction.commit("diagrams");
            return stored;
        }
    }

    private List<CustomDiagram> diagramsOfGraph(String graphUri) {
        try (var transaction = databasePort.beginTransaction(DATASET, ReadWrite.READ)) {
            return List.copyOf(transaction.graph(graphUri).getCustomDiagrams().values());
        }
    }

    private List<CustomDiagram> workspaceDiagrams() {
        try (var transaction = databasePort.beginTransaction(DATASET, ReadWrite.READ)) {
            return List.copyOf(transaction.diagrams().values());
        }
    }
}

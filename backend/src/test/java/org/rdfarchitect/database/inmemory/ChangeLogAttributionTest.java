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

package org.rdfarchitect.database.inmemory;

import static org.assertj.core.api.Assertions.*;
import static org.rdfarchitect.database.inmemory.WorkspaceFixtures.GRAPH_A;
import static org.rdfarchitect.database.inmemory.WorkspaceFixtures.GRAPH_B;
import static org.rdfarchitect.database.inmemory.WorkspaceFixtures.WORKSPACE;
import static org.rdfarchitect.rdf.TestRDFUtils.triple;

import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.database.inmemory.diagrams.CustomDiagram;
import org.rdfarchitect.models.changelog.ContextDelta;
import org.rdfarchitect.models.changelog.WorkspaceChangeLogEntry;

import java.util.List;
import java.util.UUID;

/**
 * Covers what a recorded change says about itself: which graphs it reached, what kind of data it
 * touched, and which resources it changed. That is what a changelog scoped to one graph, a filter
 * by kind, and a message offering to go where the change landed are all derived from.
 */
class ChangeLogAttributionTest {

    private static final String CLASS_URI = "http://example.org/a#Breaker";

    private Workspace workspace;

    @BeforeEach
    void setUp() {
        workspace = new Workspace(WORKSPACE);
    }

    // -------------------------------------------------------------------------
    // Which graphs a change reached
    // -------------------------------------------------------------------------

    @Nested
    class AffectedGraphs {

        @Test
        void aSchemaChange_isAttributedToTheGraphItLandedIn() {
            createGraph(GRAPH_A);
            createGraph(GRAPH_B);

            commitClass(GRAPH_A);

            assertThat(newestChange().affectedGraphUris()).containsExactly(GRAPH_A);
        }

        @Test
        void creatingAGraph_isAttributedToThatGraph() {
            createGraph(GRAPH_A);

            assertThat(newestChange().affectedGraphUris()).containsExactly(GRAPH_A);
        }

        @Test
        void deletingAGraph_isAttributedToThatGraph() {
            createGraph(GRAPH_A);

            try (var transaction = workspace.begin(ReadWrite.WRITE)) {
                transaction.deleteGraph(GRAPH_A);
                transaction.commit("deleted graph a");
            }

            assertThat(newestChange().affectedGraphUris()).containsExactly(GRAPH_A);
        }

        @Test
        void renamingAGraph_isAttributedToTheUriItLeftAndTheOneItMovedTo() {
            createGraph(GRAPH_A);

            try (var transaction = workspace.begin(ReadWrite.WRITE)) {
                transaction.renameGraph(GRAPH_A, GRAPH_B);
                transaction.commit("renamed graph a");
            }

            assertThat(newestChange().affectedGraphUris())
                    .containsExactlyInAnyOrder(GRAPH_A, GRAPH_B);
        }

        @Test
        void aChangeToWorkspaceDataAlone_isAttributedToNoGraph() {
            var diagramId = UUID.randomUUID();

            try (var transaction = workspace.begin(ReadWrite.WRITE)) {
                transaction
                        .diagrams()
                        .put(diagramId, new CustomDiagram(diagramId, "overview", List.of()));
                transaction.commit("created a workspace diagram");
            }

            assertThat(newestChange().affectedGraphUris()).isEmpty();
        }
    }

    // -------------------------------------------------------------------------
    // Which graph a change belongs to once the graph has been renamed
    // -------------------------------------------------------------------------

    @Nested
    class RenamedGraphs {

        @Test
        void anEarlierSchemaChange_isAttributedToTheNameTheGraphHasNow() {
            createGraph(GRAPH_A);
            commitClass(GRAPH_A);
            renameGraph(GRAPH_A, GRAPH_B);

            assertThat(schemaChange().affectedGraphUris()).containsExactly(GRAPH_B);
        }

        @Test
        void theDeltaOfAnEarlierSchemaChange_namesTheNameTheGraphHasNow() {
            createGraph(GRAPH_A);
            commitClass(GRAPH_A);
            renameGraph(GRAPH_A, GRAPH_B);

            assertThat(schemaChange().deltas())
                    .extracting(ContextDelta::graphUri)
                    .containsExactly(GRAPH_B);
        }

        @Test
        void aPendingUndo_isAttributedToTheNameTheGraphHasNow() {
            createGraph(GRAPH_A);
            renameGraph(GRAPH_A, GRAPH_B);
            commitClass(GRAPH_B);

            assertThat(workspace.pendingUndo().affectedGraphUris()).containsExactly(GRAPH_B);
        }

        @Test
        void anUndoneChange_isAttributedToTheNameTheGraphHasAfterTheUndo() {
            createGraph(GRAPH_A);
            commitClass(GRAPH_A);
            renameGraph(GRAPH_A, GRAPH_B);

            // Undoing the rename puts the graph back under its old name, which is where the user
            // has to look for the change the next undo would take back.
            var step = workspace.undo();

            assertThat(step.change().affectedGraphUris())
                    .containsExactlyInAnyOrder(GRAPH_A, GRAPH_B);
            assertThat(schemaChange().affectedGraphUris()).containsExactly(GRAPH_A);
        }

        @Test
        void aChangeToAGraphDeletedSince_keepsTheNameItWasRecordedUnder() {
            createGraph(GRAPH_A);
            commitClass(GRAPH_A);

            try (var transaction = workspace.begin(ReadWrite.WRITE)) {
                transaction.deleteGraph(GRAPH_A);
                transaction.commit("deleted graph a");
            }

            assertThat(schemaChange().affectedGraphUris()).containsExactly(GRAPH_A);
        }
    }

    // -------------------------------------------------------------------------
    // What kind of data a change touched
    // -------------------------------------------------------------------------

    @Nested
    class AffectedKinds {

        @Test
        void aSchemaChange_isNamedTheWayItsDeltaIsNamed() {
            createGraph(GRAPH_A);

            commitClass(GRAPH_A);

            var change = newestChange();
            assertThat(change.affectedKinds()).containsExactly("rdf");
            assertThat(change.deltas())
                    .extracting(ContextDelta::contextName)
                    .containsExactly("rdf");
        }

        @Test
        void aChangeWithoutADelta_stillNamesWhatItTouched() {
            // Deleting a graph changes the set of graphs, which keeps snapshots rather than
            // triples. A filter that went by the deltas alone would lose the change entirely.
            createGraph(GRAPH_A);

            try (var transaction = workspace.begin(ReadWrite.WRITE)) {
                transaction.deleteGraph(GRAPH_A);
                transaction.commit("deleted graph a");
            }

            var change = newestChange();
            assertThat(change.deltas()).isEmpty();
            assertThat(change.affectedKinds()).containsExactly("graphs");
        }

        @Test
        void aChangeToTheShapes_isToldApartFromOneToTheSchema() {
            createGraph(GRAPH_A);

            try (var transaction = workspace.begin(ReadWrite.WRITE)) {
                transaction.graph(GRAPH_A).getCustomSHACL().add(triple("s p o"));
                transaction.commit("changed the shapes");
            }

            assertThat(newestChange().affectedKinds()).containsExactly("shacl");
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private WorkspaceChangeLogEntry newestChange() {
        return WorkspaceFixtures.newestChange(workspace);
    }

    /** The entry that changed the schema, whatever has been recorded on top of it since. */
    private WorkspaceChangeLogEntry schemaChange() {
        return workspace.getChangeHistory().stream()
                .filter(entry -> entry.affectedKinds().contains("rdf"))
                .findFirst()
                .orElseThrow();
    }

    private void createGraph(String graphUri) {
        WorkspaceFixtures.createGraph(workspace, graphUri);
    }

    private void renameGraph(String oldGraphUri, String newGraphUri) {
        WorkspaceFixtures.renameGraph(workspace, oldGraphUri, newGraphUri);
    }

    private void commitClass(String graphUri) {
        try (var transaction = workspace.begin(ReadWrite.WRITE)) {
            transaction
                    .graph(graphUri)
                    .getRdfGraph()
                    .add(
                            Triple.create(
                                    NodeFactory.createURI(CLASS_URI),
                                    RDF.type.asNode(),
                                    RDFS.Class.asNode()));
            transaction.commit("created a class");
        }
    }
}

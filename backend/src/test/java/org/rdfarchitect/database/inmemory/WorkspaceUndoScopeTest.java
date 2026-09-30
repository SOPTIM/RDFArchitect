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

import org.apache.jena.graph.Triple;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.shared.impl.PrefixMappingImpl;
import org.apache.jena.sparql.graph.GraphFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.config.GraphCompressionConfig;
import org.rdfarchitect.database.inmemory.diagrams.ClassInDiagram;
import org.rdfarchitect.database.inmemory.diagrams.CustomDiagram;
import org.rdfarchitect.models.changelog.ParticipantId;
import org.rdfarchitect.models.cim.data.dto.relations.uri.URI;
import org.rdfarchitect.rdf.TestRDFUtils;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Covers what the workspace changelog reaches: everything a user can change inside a workspace, not
 * just the RDF schema.
 */
class WorkspaceUndoScopeTest {

    private static final String WORKSPACE = "workspace";
    private static final String GRAPH_A = "http://example.org/a";
    private static final String GRAPH_B = "http://example.org/b";

    private Workspace workspace;
    private Triple triple;

    @BeforeEach
    void setUp() {
        workspace = new Workspace(WORKSPACE);
        triple = TestRDFUtils.triple("s p o");
    }

    // -------------------------------------------------------------------------
    // Graph lifecycle
    // -------------------------------------------------------------------------

    @Nested
    class GraphLifecycle {

        @Test
        void undo_ofAGraphCreation_removesTheGraphAgain() {
            createGraph(GRAPH_A);

            workspace.undo();

            assertThat(workspace.listGraphUris()).isEmpty();
        }

        @Test
        void redo_ofAGraphCreation_bringsTheGraphBack() {
            createGraph(GRAPH_A);
            workspace.undo();

            workspace.redo();

            assertThat(workspace.listGraphUris()).containsExactly(GRAPH_A);
        }

        @Test
        void undo_ofAGraphDeletion_bringsBackTheGraphWithItsContents() {
            createGraph(GRAPH_A);
            commitTriple(GRAPH_A, triple);
            deleteGraph(GRAPH_A);

            workspace.undo();

            assertThat(workspace.listGraphUris()).containsExactly(GRAPH_A);
            assertThat(triplesIn(GRAPH_A)).containsExactly(triple);
        }

        @Test
        void undo_ofAGraphDeletion_keepsTheHistoryOfThatGraphUsable() {
            createGraph(GRAPH_A);
            commitTriple(GRAPH_A, triple);
            deleteGraph(GRAPH_A);
            workspace.undo();

            workspace.undo();

            assertThat(triplesIn(GRAPH_A)).isEmpty();
        }

        @Test
        void undo_ofAGraphRename_restoresTheOldUri() {
            createGraph(GRAPH_A);
            try (var transaction = workspace.begin(ReadWrite.WRITE)) {
                transaction.renameGraph(GRAPH_A, GRAPH_B);
                transaction.commit("renamed the graph");
            }

            workspace.undo();

            assertThat(workspace.listGraphUris()).containsExactly(GRAPH_A);
        }

        @Test
        void commit_ofTwoGraphDeletions_isOneEntryThatUndoBringsBackTogether() {
            createGraph(GRAPH_A);
            createGraph(GRAPH_B);
            try (var transaction = workspace.begin(ReadWrite.WRITE)) {
                transaction.deleteGraph(GRAPH_A);
                transaction.deleteGraph(GRAPH_B);
                transaction.commit("deleted both graphs");
            }

            workspace.undo();

            assertThat(workspace.listGraphUris()).containsExactlyInAnyOrder(GRAPH_A, GRAPH_B);
        }
    }

    // -------------------------------------------------------------------------
    // Diagrams, colours and prefixes
    // -------------------------------------------------------------------------

    @Nested
    class WorkspaceState {

        @Test
        void undo_ofACreatedWorkspaceDiagram_removesItAgain() {
            var diagramId = UUID.randomUUID();
            try (var transaction = workspace.begin(ReadWrite.WRITE)) {
                transaction.diagrams().put(diagramId, new CustomDiagram(diagramId, "d", List.of()));
                transaction.commit("created a diagram");
            }

            workspace.undo();

            assertThat(diagrams()).isEmpty();
        }

        @Test
        void undo_ofADeletedWorkspaceDiagram_bringsItBackWithItsClasses() {
            var diagramId = UUID.randomUUID();
            var classes = List.of(new ClassInDiagram(UUID.randomUUID(), new URI(GRAPH_A)));
            try (var transaction = workspace.begin(ReadWrite.WRITE)) {
                transaction.diagrams().put(diagramId, new CustomDiagram(diagramId, "d", classes));
                transaction.commit("created a diagram");
            }
            try (var transaction = workspace.begin(ReadWrite.WRITE)) {
                transaction.diagrams().remove(diagramId);
                transaction.commit("deleted the diagram");
            }

            workspace.undo();

            assertThat(diagrams().get(diagramId).getClasses()).isEqualTo(classes);
        }

        @Test
        void undo_ofARenamedGraphDiagram_restoresTheOldName() {
            createGraph(GRAPH_A);
            var diagramId = UUID.randomUUID();
            try (var transaction = workspace.begin(ReadWrite.WRITE)) {
                transaction
                        .graph(GRAPH_A)
                        .getCustomDiagrams()
                        .put(diagramId, new CustomDiagram(diagramId, "before", List.of()));
                transaction.commit("created a diagram");
            }
            try (var transaction = workspace.begin(ReadWrite.WRITE)) {
                transaction.graph(GRAPH_A).getCustomDiagrams().get(diagramId).setName("after");
                transaction.commit("renamed the diagram");
            }

            workspace.undo();

            try (var transaction = workspace.begin(ReadWrite.READ)) {
                assertThat(transaction.graph(GRAPH_A).getCustomDiagrams().get(diagramId).getName())
                        .isEqualTo("before");
            }
        }

        @Test
        void undo_ofAColourChange_restoresThePreviousColour() {
            createGraph(GRAPH_A);
            String generated;
            try (var transaction = workspace.begin(ReadWrite.READ)) {
                generated = transaction.crossProfileInfo().getColor(GRAPH_A);
            }
            try (var transaction = workspace.begin(ReadWrite.WRITE)) {
                transaction.crossProfileInfo().setColor(GRAPH_A, "#123456");
                transaction.commit("changed the colour");
            }

            workspace.undo();

            try (var transaction = workspace.begin(ReadWrite.READ)) {
                assertThat(transaction.crossProfileInfo().getColor(GRAPH_A)).isEqualTo(generated);
            }
        }

        @Test
        void undo_ofAPrefixChange_restoresThePreviousPrefixes() {
            setPrefixes(prefixMapping("first", "http://example.org/first#"));
            setPrefixes(prefixMapping("second", "http://example.org/second#"));

            workspace.undo();

            assertThat(workspace.getPrefixMapping().getNsPrefixMap())
                    .containsExactly(entry("first", "http://example.org/first#"));
        }

        @Test
        void readingDiagramsInAWriteTransaction_isNotRecordedAsAChange() {
            createGraph(GRAPH_A);
            var diagramId = UUID.randomUUID();
            try (var transaction = workspace.begin(ReadWrite.WRITE)) {
                transaction.diagrams().put(diagramId, new CustomDiagram(diagramId, "d", List.of()));
                transaction.commit("created a diagram");
            }

            try (var transaction = workspace.begin(ReadWrite.WRITE)) {
                transaction.diagrams().get(diagramId).getName();
                transaction.graph(GRAPH_A).getRdfGraph().add(triple);
                transaction.commit("changed only the schema");
            }

            assertThat(workspace.getChangeHistory().getFirst().participants())
                    .extracting(version -> version.id().kind())
                    .containsExactly(ParticipantId.Kind.RDF);
        }
    }

    // -------------------------------------------------------------------------
    // The history horizon
    // -------------------------------------------------------------------------

    @Nested
    class Retention {

        /**
         * The log keeps five entries: the graph creation and the first sixteen triples have fallen
         * past the horizon and are folded into the base, the last four can still be undone. Before
         * the log owned the stack, the two bounds were counted separately and the log went on
         * offering undos the graph could no longer perform.
         */
        @Test
        void undo_pastTheRetentionBound_stopsWhereTheHistoryActuallyEnds() {
            var config = new GraphCompressionConfig();
            var original = GraphCompressionConfig.getMaxVersions();
            try {
                config.setMaxVersions(5);
                var bounded = new Workspace("bounded");
                createGraph(bounded, GRAPH_A);
                for (int i = 0; i < 20; i++) {
                    commitTriple(bounded, GRAPH_A, TestRDFUtils.triple("s p o" + i));
                }

                var undone = 0;
                while (bounded.canUndo()) {
                    bounded.undo();
                    undone++;
                }

                assertThat(undone).isEqualTo(4);
                assertThat(triplesIn(bounded, GRAPH_A)).hasSize(16);
            } finally {
                config.setMaxVersions(original);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void createGraph(String graphUri) {
        createGraph(workspace, graphUri);
    }

    private static void createGraph(Workspace target, String graphUri) {
        try (var transaction = target.begin(ReadWrite.WRITE)) {
            transaction.createGraph(graphUri, GraphFactory.createDefaultGraph());
            transaction.commit("created graph %s".formatted(graphUri));
        }
    }

    private void deleteGraph(String graphUri) {
        try (var transaction = workspace.begin(ReadWrite.WRITE)) {
            transaction.deleteGraph(graphUri);
            transaction.commit("deleted graph %s".formatted(graphUri));
        }
    }

    private void commitTriple(String graphUri, Triple value) {
        commitTriple(workspace, graphUri, value);
    }

    private static void commitTriple(Workspace target, String graphUri, Triple value) {
        try (var transaction = target.begin(ReadWrite.WRITE)) {
            transaction.graph(graphUri).getRdfGraph().add(value);
            transaction.commit("added a triple");
        }
    }

    private List<Triple> triplesIn(String graphUri) {
        return triplesIn(workspace, graphUri);
    }

    private static List<Triple> triplesIn(Workspace target, String graphUri) {
        try (var transaction = target.begin(ReadWrite.READ)) {
            return transaction.graph(graphUri).getRdfGraph().find().toList();
        }
    }

    private Map<UUID, CustomDiagram> diagrams() {
        try (var transaction = workspace.begin(ReadWrite.READ)) {
            return transaction.diagrams();
        }
    }

    private void setPrefixes(PrefixMapping prefixMapping) {
        try (var transaction = workspace.begin(ReadWrite.WRITE)) {
            transaction.setPrefixes(prefixMapping);
            transaction.commit("changed the namespace prefixes");
        }
    }

    private static PrefixMapping prefixMapping(String prefix, String namespace) {
        var mapping = new PrefixMappingImpl();
        mapping.setNsPrefix(prefix, namespace);
        return mapping;
    }
}

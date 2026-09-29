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

import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.sparql.graph.GraphFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WorkspacePrefixTest {

    private static final String WORKSPACE = "workspace";
    private static final String GRAPH_URI = "http://example.org/graph";
    private static final String FOO_URI = "http://example.org/foo#";

    private Workspace workspace;

    @BeforeEach
    void setUp() {
        workspace = new Workspace(WORKSPACE);
        workspace.create(GRAPH_URI, GraphFactory.createDefaultGraph());
    }

    @Test
    void commit_customShaclPrefix_isVisibleInSubsequentReadTransaction() {
        setPrefix();

        try (var transaction = workspace.begin(ReadWrite.READ)) {
            var model =
                    ModelFactory.createModelForGraph(transaction.graph(GRAPH_URI).getCustomSHACL());
            assertThat(model.getNsPrefixURI("foo")).isEqualTo(FOO_URI);
        }
    }

    @Test
    void commit_onlyPrefixChange_isRecordedAsUndoableStep() {
        setPrefix();

        assertThat(workspace.canUndo()).isTrue();
    }

    @Test
    void undo_revertsCustomShaclPrefix() {
        setPrefix();

        workspace.undo();

        try (var transaction = workspace.begin(ReadWrite.READ)) {
            var model =
                    ModelFactory.createModelForGraph(transaction.graph(GRAPH_URI).getCustomSHACL());
            assertThat(model.getNsPrefixURI("foo")).isNull();
        }
    }

    @Test
    void redo_reappliesCustomShaclPrefix() {
        setPrefix();
        workspace.undo();

        workspace.redo();

        try (var transaction = workspace.begin(ReadWrite.READ)) {
            var model =
                    ModelFactory.createModelForGraph(transaction.graph(GRAPH_URI).getCustomSHACL());
            assertThat(model.getNsPrefixURI("foo")).isEqualTo(FOO_URI);
        }
    }

    private void setPrefix() {
        try (var transaction = workspace.begin(ReadWrite.WRITE)) {
            ModelFactory.createModelForGraph(transaction.graph(GRAPH_URI).getCustomSHACL())
                    .setNsPrefix("foo", FOO_URI);
            transaction.commit("set prefix");
        }
    }
}

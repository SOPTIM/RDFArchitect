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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GraphWithContextTransactionalPrefixTest {

    private static final String FOO_URI = "http://example.org/foo#";

    private GraphWithContextTransactional ctx;

    @BeforeEach
    void setUp() {
        ctx = new GraphWithContextTransactional(GraphFactory.createDefaultGraph());
    }

    @AfterEach
    void tearDown() {
        if (ctx.isInTransaction()) {
            ctx.end();
        }
    }

    @Test
    void commit_customShaclPrefix_isVisibleInSubsequentReadTransaction() {
        try (var _ = ctx.begin(ReadWrite.WRITE)) {
            var model = ModelFactory.createModelForGraph(ctx.getCustomSHACL());
            model.setNsPrefix("foo", FOO_URI);
            ctx.commit("set prefix");
        }

        try (var _ = ctx.begin(ReadWrite.READ)) {
            var model = ModelFactory.createModelForGraph(ctx.getCustomSHACL());
            assertThat(model.getNsPrefixURI("foo")).isEqualTo(FOO_URI);
        }
    }

    @Test
    void commit_onlyPrefixChange_isRecordedAsUndoableStep() {
        try (var _ = ctx.begin(ReadWrite.WRITE)) {
            ModelFactory.createModelForGraph(ctx.getCustomSHACL()).setNsPrefix("foo", FOO_URI);
            ctx.commit("set prefix");
        }

        try (var _ = ctx.begin(ReadWrite.READ)) {
            assertThat(ctx.canUndo()).isTrue();
        }
    }

    @Test
    void undo_revertsCustomShaclPrefix() {
        try (var _ = ctx.begin(ReadWrite.WRITE)) {
            ModelFactory.createModelForGraph(ctx.getCustomSHACL()).setNsPrefix("foo", FOO_URI);
            ctx.commit("set prefix");
        }

        ctx.undo();

        try (var _ = ctx.begin(ReadWrite.READ)) {
            var model = ModelFactory.createModelForGraph(ctx.getCustomSHACL());
            assertThat(model.getNsPrefixURI("foo")).isNull();
        }
    }

    @Test
    void redo_reappliesCustomShaclPrefix() {
        try (var _ = ctx.begin(ReadWrite.WRITE)) {
            ModelFactory.createModelForGraph(ctx.getCustomSHACL()).setNsPrefix("foo", FOO_URI);
            ctx.commit("set prefix");
        }
        ctx.undo();

        ctx.redo();

        try (var _ = ctx.begin(ReadWrite.READ)) {
            var model = ModelFactory.createModelForGraph(ctx.getCustomSHACL());
            assertThat(model.getNsPrefixURI("foo")).isEqualTo(FOO_URI);
        }
    }
}

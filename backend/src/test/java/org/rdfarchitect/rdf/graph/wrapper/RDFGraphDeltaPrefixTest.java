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

package org.rdfarchitect.rdf.graph.wrapper;

import static org.assertj.core.api.Assertions.*;

import org.apache.jena.query.ReadWrite;
import org.apache.jena.sparql.graph.GraphFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RDFGraphDeltaPrefixTest {

    private static final String FOO_URI = "http://example.org/foo#";
    private static final String BAR_URI = "http://example.org/bar#";

    private TransactionContext txnContext;
    private RDFGraphDelta graph;

    @BeforeEach
    void setUp() {
        txnContext = new TransactionContext();
        graph = new RDFGraphDelta(GraphFactory.createDefaultGraph(), 15, 5, txnContext);
    }

    @AfterEach
    void tearDown() {
        if (txnContext.isInTransaction()) {
            txnContext.end();
        }
    }

    @Test
    void hasChanges_afterSettingPrefix_returnsTrue() {
        txnContext.begin(ReadWrite.WRITE);

        graph.getPrefixMapping().setNsPrefix("foo", FOO_URI);

        assertThat(graph.hasChanges()).isTrue();
    }

    @Test
    void hasChanges_withoutAnyChange_returnsFalse() {
        txnContext.begin(ReadWrite.WRITE);

        assertThat(graph.hasChanges()).isFalse();
    }

    @Test
    void commit_prefixIsVisibleInSubsequentTransaction() {
        txnContext.begin(ReadWrite.WRITE);
        graph.getPrefixMapping().setNsPrefix("foo", FOO_URI);
        graph.commit();
        txnContext.end();

        txnContext.begin(ReadWrite.READ);
        assertThat(graph.getPrefixMapping().getNsPrefixURI("foo")).isEqualTo(FOO_URI);
    }

    @Test
    void commit_multiplePrefixesAcrossCommits_areAllVisible() {
        txnContext.begin(ReadWrite.WRITE);
        graph.getPrefixMapping().setNsPrefix("foo", FOO_URI);
        graph.commit();
        txnContext.end();

        txnContext.begin(ReadWrite.WRITE);
        graph.getPrefixMapping().setNsPrefix("bar", BAR_URI);
        graph.commit();
        txnContext.end();

        txnContext.begin(ReadWrite.READ);
        assertThat(graph.getPrefixMapping().getNsPrefixMap())
                .containsEntry("foo", FOO_URI)
                .containsEntry("bar", BAR_URI);
    }

    @Test
    void undo_revertsPrefixAddition() {
        txnContext.begin(ReadWrite.WRITE);
        graph.getPrefixMapping().setNsPrefix("foo", FOO_URI);
        graph.commit();
        txnContext.end();

        txnContext.begin(ReadWrite.WRITE);
        graph.undo();
        txnContext.end();

        txnContext.begin(ReadWrite.READ);
        assertThat(graph.getPrefixMapping().getNsPrefixURI("foo")).isNull();
    }

    @Test
    void redo_reappliesPrefixAddition() {
        txnContext.begin(ReadWrite.WRITE);
        graph.getPrefixMapping().setNsPrefix("foo", FOO_URI);
        graph.commit();
        txnContext.end();

        txnContext.begin(ReadWrite.WRITE);
        graph.undo();
        txnContext.end();

        txnContext.begin(ReadWrite.WRITE);
        graph.redo();
        txnContext.end();

        txnContext.begin(ReadWrite.READ);
        assertThat(graph.getPrefixMapping().getNsPrefixURI("foo")).isEqualTo(FOO_URI);
    }

    @Test
    void undo_revertsPrefixRemoval() {
        // commit 1: add foo
        txnContext.begin(ReadWrite.WRITE);
        graph.getPrefixMapping().setNsPrefix("foo", FOO_URI);
        graph.commit();
        txnContext.end();

        // commit 2: remove foo
        txnContext.begin(ReadWrite.WRITE);
        graph.getPrefixMapping().removeNsPrefix("foo");
        graph.commit();
        txnContext.end();

        // undo the removal -> foo should be back
        txnContext.begin(ReadWrite.WRITE);
        graph.undo();
        txnContext.end();

        txnContext.begin(ReadWrite.READ);
        assertThat(graph.getPrefixMapping().getNsPrefixURI("foo")).isEqualTo(FOO_URI);
    }

    @Test
    void abort_discardsUncommittedPrefixChange() {
        txnContext.begin(ReadWrite.WRITE);
        graph.getPrefixMapping().setNsPrefix("foo", FOO_URI);
        graph.abort();
        txnContext.end();

        txnContext.begin(ReadWrite.READ);
        assertThat(graph.getPrefixMapping().getNsPrefixURI("foo")).isNull();
    }
}

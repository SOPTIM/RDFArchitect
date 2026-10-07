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
import static org.rdfarchitect.rdf.TestRDFUtils.triple;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.sparql.graph.GraphFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.exception.graph.GraphVersionControlException;

import java.util.List;

/**
 * Covers reading an older version of a graph without going there: a delta's base is the delta
 * before it, so every version in the chain already composes to the state it left behind.
 */
class RDFGraphDeltaCaptureTest {

    private WorkspaceTransactionContext txnContext;
    private RDFGraphDelta graph;

    @BeforeEach
    void setUp() {
        txnContext = new WorkspaceTransactionContext("workspace");
        graph = new RDFGraphDelta(GraphFactory.createDefaultGraph(), txnContext);
    }

    @AfterEach
    void tearDown() {
        while (txnContext.isInTransaction()) {
            txnContext.end();
        }
    }

    @Test
    void capture_ofTheCurrentVersion_writesBackNothing() {
        commit(triple("s p o1"));

        txnContext.begin(ReadWrite.WRITE);
        graph.capture(0).reinstate();

        assertThat(graph.hasChanges()).isFalse();
    }

    @Test
    void capture_ofAnOlderVersion_putsBackWhatWasAddedSince() {
        commit(triple("s p o1"));
        commit(triple("s p o2"));

        txnContext.begin(ReadWrite.WRITE);
        graph.capture(1).reinstate();

        assertThat(triples()).containsExactly(triple("s p o1"));
    }

    @Test
    void capture_ofAnOlderVersion_putsBackWhatWasDeletedSince() {
        commit(triple("s p o1"));
        txnContext.begin(ReadWrite.WRITE);
        graph.delete(triple("s p o1"));
        graph.commit();
        txnContext.end();

        txnContext.begin(ReadWrite.WRITE);
        graph.capture(1).reinstate();

        assertThat(triples()).containsExactly(triple("s p o1"));
    }

    @Test
    void capture_leavesTheGraphWhereItStandsUntilTheStateIsWrittenBack() {
        commit(triple("s p o1"));
        commit(triple("s p o2"));

        txnContext.begin(ReadWrite.WRITE);
        var captured = graph.capture(1);

        assertThat(triples()).containsExactlyInAnyOrder(triple("s p o1"), triple("s p o2"));
        assertThat(graph.hasChanges()).isFalse();
        assertThat(graph.currentVersionId()).isEqualTo(graph.versionIdAt(0));

        captured.reinstate();

        assertThat(triples()).containsExactly(triple("s p o1"));
    }

    @Test
    void capture_writesBackTheSmallestChangeThatReachesTheState() {
        // The delta of the commit recording the restore is what the changelog shows, so it has to
        // name what really differs rather than the whole graph twice over.
        commit(triple("s p o1"));
        commit(triple("s p o2"));

        txnContext.begin(ReadWrite.WRITE);
        graph.capture(1).reinstate();
        graph.commit();

        var delta = graph.getLastDelta();
        assertThat(delta.getDeletions().stream().toList()).containsExactly(triple("s p o2"));
        assertThat(delta.getAdditions().isEmpty()).isTrue();
    }

    @Test
    void capture_pastTheRetainedHistory_throwsRatherThanAnsweringWithTheOldestVersion() {
        commit(triple("s p o1"));

        txnContext.begin(ReadWrite.READ);

        assertThatThrownBy(() -> graph.capture(5))
                .isInstanceOf(GraphVersionControlException.class)
                .hasMessageContaining("versions back");
    }

    @Test
    void versionIdAt_namesTheVersionTheGraphGainedThatManyCommitsAgo() {
        commit(triple("s p o1"));
        txnContext.begin(ReadWrite.READ);
        var first = graph.currentVersionId();
        txnContext.end();
        commit(triple("s p o2"));

        txnContext.begin(ReadWrite.READ);

        assertThat(graph.versionIdAt(1)).isEqualTo(first);
        assertThat(graph.versionIdAt(0)).isNotEqualTo(first);
    }

    private void commit(Triple triple) {
        txnContext.begin(ReadWrite.WRITE);
        graph.add(triple);
        graph.commit();
        txnContext.end();
    }

    private List<Triple> triples() {
        return graph.find(Node.ANY, Node.ANY, Node.ANY).toList();
    }
}

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.rdfarchitect.rdf.TestRDFUtils.triple;

import org.apache.jena.graph.Graph;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.sparql.graph.GraphFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.rdf.graph.DeltaCompressible;

/**
 * Commits that change nothing in a graph.
 *
 * <p>A context commits all of its participants on every commit, so most commits leave most graphs
 * unchanged. Those versions still have to count — undo moves every participant in step — but they
 * must not make the graph slower to read.
 */
class RDFGraphDeltaUnchangedVersionsTest {

    private static final int MAX_VERSIONS = 50;
    private static final int COMPRESS_COUNT = 5;

    private TransactionContext txnContext;
    private RDFGraphDelta delta;

    @BeforeEach
    void setUp() {
        txnContext = new TransactionContext();
        delta = newDelta(MAX_VERSIONS, COMPRESS_COUNT);
        txnContext.begin(ReadWrite.WRITE);
    }

    @AfterEach
    void tearDown() {
        if (txnContext.isInTransaction()) {
            txnContext.end();
        }
    }

    private RDFGraphDelta newDelta(int maxVersions, int compressCount) {
        return new RDFGraphDelta(
                GraphFactory.createDefaultGraph(), maxVersions, compressCount, txnContext);
    }

    /** How many deltas a read of the committed content passes through. */
    static int depth(RDFGraphDelta graph) {
        int depth = 0;
        Graph layer = graph.getLastDelta();
        while (layer instanceof DeltaCompressible compressible) {
            depth++;
            layer = compressible.getBase();
        }
        return depth;
    }

    private void commitUnchanged(int times) {
        for (int i = 0; i < times; i++) {
            delta.commit();
        }
    }

    @Test
    void unchangedCommitsDoNotDeepenTheGraph() {
        delta.add(triple("s p o"));
        delta.commit();
        var depthAfterEdit = depth(delta);

        commitUnchanged(40);

        assertThat(depth(delta)).isLessThanOrEqualTo(depthAfterEdit + 1);
    }

    @Test
    void anEditAfterUnchangedCommitsIsLayeredOverTheLastEdit() {
        delta.add(triple("s p o"));
        delta.commit();
        var lastEdit = delta.getLastDelta();

        commitUnchanged(40);
        delta.add(triple("s p o2"));
        delta.commit();

        assertThat(delta.getLastDelta().getBase()).isSameAs(lastEdit);
    }

    @Test
    void unchangedCommitsStillCountAsVersions() {
        delta.add(triple("s p o"));
        delta.commit();
        var version = delta.currentVersion();

        commitUnchanged(3);

        assertThat(delta.currentVersion()).isEqualTo(version + 3);
        for (int i = 0; i < 3; i++) {
            delta.undo();
            assertThat(delta.contains(triple("s p o"))).isTrue();
        }
        delta.undo();
        assertThat(delta.contains(triple("s p o"))).isFalse();

        for (int i = 0; i < 4; i++) {
            delta.redo();
        }
        assertThat(delta.contains(triple("s p o"))).isTrue();
    }

    @Test
    void contentVersionIdOnlyChangesWithTheContent() {
        delta.add(triple("s p o"));
        delta.commit();
        var edited = delta.contentVersionId();

        commitUnchanged(3);
        assertThat(delta.contentVersionId()).isEqualTo(edited);

        delta.add(triple("s p o2"));
        delta.commit();
        assertThat(delta.contentVersionId()).isNotEqualTo(edited);

        delta.undo();
        assertThat(delta.contentVersionId()).isEqualTo(edited);
    }

    @Test
    void compressingPastUnchangedVersionsKeepsTheContentAndTheDepthBounded() {
        delta = newDelta(8, 3);
        for (int round = 0; round < 10; round++) {
            delta.add(triple("s p o" + round));
            delta.commit();
            commitUnchanged(5);
        }

        assertThat(depth(delta)).isLessThanOrEqualTo(10);
        for (int round = 0; round < 10; round++) {
            assertThat(delta.contains(triple("s p o" + round))).isTrue();
        }
        while (delta.canUndo()) {
            delta.undo();
        }
        assertThat(delta.contains(triple("s p o9"))).isFalse();
        assertThat(delta.contains(triple("s p o0"))).isTrue();
    }
}

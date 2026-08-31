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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.rdfarchitect.rdf.TestRDFUtils.triple;

import org.apache.jena.query.ReadWrite;
import org.apache.jena.sparql.graph.GraphFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Aligning a graph's history depth with the rest of its context.
 *
 * <p>A context undoes every participant the same number of times and {@link RDFGraphDelta#undo()}
 * throws once a participant is out of history, so a graph joining a context that already has a
 * history has to be padded first.
 */
class RDFGraphDeltaPadHistoryTest {

    private static final int MAX_VERSIONS = 50;
    private static final int COMPRESS_COUNT = 5;

    private TransactionContext txnContext;
    private RDFGraphDelta delta;

    @BeforeEach
    void setUp() {
        txnContext = new TransactionContext();
        delta =
                new RDFGraphDelta(
                        GraphFactory.createDefaultGraph(),
                        MAX_VERSIONS,
                        COMPRESS_COUNT,
                        txnContext);
        txnContext.begin(ReadWrite.WRITE);
    }

    @AfterEach
    void tearDown() {
        if (txnContext.isInTransaction()) {
            txnContext.end();
        }
    }

    @Test
    void freshGraphStartsAtVersionZero() {
        assertThat(delta.currentVersion()).isZero();
        assertThat(delta.canUndo()).isFalse();
    }

    @Test
    void padHistory_raisesTheVersionToTheTarget() {
        delta.padHistory(4);

        assertThat(delta.currentVersion()).isEqualTo(4);
    }

    @Test
    void padHistory_makesTheGraphUndoableThatManyTimes() {
        delta.padHistory(3);

        assertThatCode(
                        () -> {
                            for (int i = 0; i < 3; i++) {
                                delta.undo();
                            }
                        })
                .doesNotThrowAnyException();
        assertThat(delta.canUndo()).isFalse();
    }

    @Test
    void padHistory_leavesTheContentEmpty() {
        delta.padHistory(3);

        assertThat(delta.isEmpty()).isTrue();
    }

    @Test
    void padHistory_keepsContentAlreadyStaged() {
        delta.add(triple("s p o"));

        delta.padHistory(2);

        assertThat(delta.currentVersion()).isEqualTo(2);
        assertThat(delta.contains(triple("s p o"))).isTrue();
    }

    @Test
    void padHistory_doesNothingWhenAlreadyDeepEnough() {
        delta.padHistory(3);

        delta.padHistory(1);

        assertThat(delta.currentVersion()).isEqualTo(3);
    }

    @Test
    void padHistory_toZeroOrNegativeIsANoOp() {
        delta.padHistory(0);
        delta.padHistory(-1);

        assertThat(delta.currentVersion()).isZero();
    }

    @Test
    void paddedGraphStillCommitsAndUndoesNormally() {
        delta.padHistory(2);
        delta.commit();

        delta.add(triple("s p o"));
        delta.commit();
        assertThat(delta.contains(triple("s p o"))).isTrue();

        delta.undo();

        assertThat(delta.contains(triple("s p o"))).isFalse();
    }
}

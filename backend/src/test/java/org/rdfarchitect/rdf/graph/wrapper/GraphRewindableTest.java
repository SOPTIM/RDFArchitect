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
import static org.junit.jupiter.api.Assertions.*;
import static org.rdfarchitect.rdf.TestRDFUtils.*;

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.GraphEventManager;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.TransactionHandler;
import org.apache.jena.graph.Triple;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.query.TxnType;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.sparql.core.Transactional;
import org.apache.jena.sparql.graph.GraphFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.rdfarchitect.exception.graph.GraphNotInATransactionException;
import org.rdfarchitect.exception.graph.GraphNotInAWriteTransactionException;
import org.rdfarchitect.exception.graph.GraphTransactionException;
import org.rdfarchitect.exception.graph.GraphVersionControlException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

class GraphRewindableTest {

    @ParameterizedTest
    @CsvSource(
            value = {"0:0", "1:2", "-1:4", "1:0", "1:-1", "5:6", "232345:234452345"},
            delimiter = ':')
    void constructor_throwsException(int i1, int i2) {
        Graph emptyGraph = GraphFactory.createDefaultGraph();
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new GraphRewindable(emptyGraph, i1, i2));
    }

    @ParameterizedTest
    @CsvSource(
            value = {"1:1", "2:2", "2:1", "5:1", "15:5", "6:5", "234452345:232345"},
            delimiter = ':')
    void constructor_successful(int i1, int i2) {
        assertThatNoException()
                .isThrownBy(() -> new GraphRewindable(GraphFactory.createDefaultGraph(), i1, i2));
    }

    @Nested
    class NotInATransactionExceptions {

        GraphRewindable graphRewindable;

        @BeforeEach
        void setUp() {
            graphRewindable = new GraphRewindable(GraphFactory.createDefaultGraph(), 1, 1);
        }

        @AfterEach
        void tearDown() {
            graphRewindable.begin(TxnType.WRITE);
            graphRewindable.close();
            graphRewindable.end();
            graphRewindable = null;
        }

        @Test
        void getTransactionHandler_noTransactionStarted_throwsGraphNotInATransactionException() {
            assertThatExceptionOfType(GraphNotInATransactionException.class)
                    .isThrownBy(() -> graphRewindable.getTransactionHandler());
        }

        @Test
        void getEventManager_noTransactionStarted_throwsGraphNotInATransactionException() {
            assertThatExceptionOfType(GraphNotInATransactionException.class)
                    .isThrownBy(() -> graphRewindable.getEventManager());
        }

        @Test
        void getPrefixMapping_noTransactionStarted_throwsGraphNotInATransactionException() {
            assertThatExceptionOfType(GraphNotInATransactionException.class)
                    .isThrownBy(() -> graphRewindable.getPrefixMapping());
        }

        @Test
        void add_noTransactionStarted_throwsGraphNotInATransactionException() {
            Triple any = Triple.create(Node.ANY, Node.ANY, Node.ANY);
            assertThatExceptionOfType(GraphNotInATransactionException.class)
                    .isThrownBy(() -> graphRewindable.add(any));
            assertThatExceptionOfType(GraphNotInATransactionException.class)
                    .isThrownBy(() -> graphRewindable.add(Node.ANY, Node.ANY, Node.ANY));
        }

        @Test
        void delete_noTransactionStarted_throwsGraphNotInATransactionException() {
            Triple any = Triple.create(Node.ANY, Node.ANY, Node.ANY);
            assertThatExceptionOfType(GraphNotInATransactionException.class)
                    .isThrownBy(() -> graphRewindable.delete(any));

            assertThatExceptionOfType(GraphNotInATransactionException.class)
                    .isThrownBy(() -> graphRewindable.delete(Node.ANY, Node.ANY, Node.ANY));
        }

        @Test
        void find_noTransactionStarted_throwsGraphNotInATransactionException() {
            Triple any = Triple.create(Node.ANY, Node.ANY, Node.ANY);
            assertThatExceptionOfType(GraphNotInATransactionException.class)
                    .isThrownBy(() -> graphRewindable.find());
            assertThatExceptionOfType(GraphNotInATransactionException.class)
                    .isThrownBy(() -> graphRewindable.find(any));
            assertThatExceptionOfType(GraphNotInATransactionException.class)
                    .isThrownBy(() -> graphRewindable.find(Node.ANY, Node.ANY, Node.ANY));
        }

        @Test
        void isIsomorphicWith_noTransactionStarted_throwsGraphNotInATransactionException() {
            Graph emptyGraph = GraphFactory.createDefaultGraph();
            assertThatExceptionOfType(GraphNotInATransactionException.class)
                    .isThrownBy(() -> graphRewindable.isIsomorphicWith(emptyGraph));
        }

        @Test
        void contains_noTransactionStarted_throwsGraphNotInATransactionException() {
            Triple any = Triple.create(Node.ANY, Node.ANY, Node.ANY);
            assertThatExceptionOfType(GraphNotInATransactionException.class)
                    .isThrownBy(() -> graphRewindable.contains(any));
            assertThatExceptionOfType(GraphNotInATransactionException.class)
                    .isThrownBy(() -> graphRewindable.contains(Node.ANY, Node.ANY, Node.ANY));
        }

        @Test
        void clear_noTransactionStarted_throwsGraphNotInATransactionException() {
            assertThatExceptionOfType(GraphNotInATransactionException.class)
                    .isThrownBy(() -> graphRewindable.clear());
        }

        @Test
        void remove_noTransactionStarted_throwsGraphNotInATransactionException() {
            assertThatExceptionOfType(GraphNotInATransactionException.class)
                    .isThrownBy(() -> graphRewindable.remove(Node.ANY, Node.ANY, Node.ANY));
        }

        @Test
        void close_noTransactionStarted_throwsGraphNotInATransactionException() {
            assertThatExceptionOfType(GraphNotInATransactionException.class)
                    .isThrownBy(() -> graphRewindable.close());
        }

        @Test
        void isEmpty_noTransactionStarted_throwsGraphNotInATransactionException() {
            assertThatExceptionOfType(GraphNotInATransactionException.class)
                    .isThrownBy(() -> graphRewindable.isEmpty());
        }

        @Test
        void size_noTransactionStarted_throwsGraphNotInATransactionException() {
            assertThatExceptionOfType(GraphNotInATransactionException.class)
                    .isThrownBy(() -> graphRewindable.size());
        }

        @Test
        void isClosed_noTransactionStarted_throwsGraphNotInATransactionException() {
            assertThatExceptionOfType(GraphNotInATransactionException.class)
                    .isThrownBy(() -> graphRewindable.isClosed());
        }

        @Test
        void promote_noTransactionStarted_throwsGraphNotInATransactionException() {
            assertThatExceptionOfType(GraphNotInATransactionException.class)
                    .isThrownBy(() -> graphRewindable.promote());
            assertThatExceptionOfType(GraphNotInATransactionException.class)
                    .isThrownBy(
                            () -> graphRewindable.promote(Transactional.Promote.READ_COMMITTED));
        }

        @Test
        void commit_noTransactionStarted_throwsGraphNotInATransactionException() {
            assertThatExceptionOfType(GraphNotInATransactionException.class)
                    .isThrownBy(() -> graphRewindable.commit());
        }

        @Test
        void abort_noTransactionStarted_throwsGraphNotInATransactionException() {
            assertThatExceptionOfType(GraphNotInATransactionException.class)
                    .isThrownBy(() -> graphRewindable.abort());
        }

        @Test
        void end_noTransactionStarted_throwsGraphNotInATransactionException() {
            assertThatExceptionOfType(GraphNotInATransactionException.class)
                    .isThrownBy(() -> graphRewindable.end());
        }

        @Test
        void transactionMode_noTransactionStarted_throwsGraphNotInATransactionException() {
            assertThatExceptionOfType(GraphNotInATransactionException.class)
                    .isThrownBy(() -> graphRewindable.transactionMode());
        }

        @Test
        void transactionType_noTransactionStarted_throwsGraphNotInATransactionException() {
            assertThatExceptionOfType(GraphNotInATransactionException.class)
                    .isThrownBy(() -> graphRewindable.transactionType());
        }
    }

    @Nested
    class EmptyGraphRewindable {

        GraphRewindable graphRewindable;

        @BeforeEach
        void setUp() {
            graphRewindable = new GraphRewindable(GraphFactory.createDefaultGraph(), 15, 5);
        }

        @AfterEach
        void tearDown() {
            graphRewindable.begin(TxnType.WRITE);
            graphRewindable.close();
            graphRewindable.end();
            graphRewindable = null;
        }

        @Test
        void undo_emptyGraphWithNoChanges_throwsGraphVersionControlException() {
            assertThatExceptionOfType(GraphVersionControlException.class)
                    .isThrownBy(() -> graphRewindable.undo());
        }

        @Test
        void redo_emptyGraphWithNoChanges_throwsGraphVersionControlException() {
            assertThatExceptionOfType(GraphVersionControlException.class)
                    .isThrownBy(() -> graphRewindable.redo());
        }

        @Test
        void getTransactionHandler() {
            graphRewindable.begin(TxnType.READ);
            assertThat(graphRewindable.getTransactionHandler())
                    .isInstanceOf(TransactionHandler.class);
            graphRewindable.end();
        }

        @Test
        void getEventManager() {
            graphRewindable.begin(TxnType.READ);
            assertThat(graphRewindable.getEventManager()).isInstanceOf(GraphEventManager.class);
            graphRewindable.end();
        }

        @Test
        void getPrefixMapping() {
            graphRewindable.begin(TxnType.READ);
            PrefixMapping prefixMapping = graphRewindable.getPrefixMapping();
            assertThat(prefixMapping).isInstanceOf(PrefixMapping.class);
            assertThat(prefixMapping.getNsPrefixMap()).isEmpty();
            graphRewindable.end();
        }

        @Test
        void find() {
            graphRewindable.begin(TxnType.READ);
            assertThat(graphRewindable.find().toList()).isEmpty();
            graphRewindable.end();
        }

        @Test
        void isIsomorphicWith() {
            graphRewindable.begin(TxnType.READ);
            assertThat(graphRewindable.isIsomorphicWith(graphRewindable)).isTrue();
            assertThat(graphRewindable.isIsomorphicWith(GraphFactory.createDefaultGraph()))
                    .isTrue();
            graphRewindable.end();
        }

        @Test
        void clear() {
            graphRewindable.begin(TxnType.WRITE);
            graphRewindable.clear();
            assertThat(graphRewindable.isEmpty()).isTrue();
            graphRewindable.end();
        }

        @Test
        void isEmpty() {
            graphRewindable.begin(TxnType.READ);
            assertThat(graphRewindable.isEmpty()).isTrue();
            graphRewindable.end();
        }

        @Test
        void size() {
            graphRewindable.begin(TxnType.READ);
            assertThat(graphRewindable.size()).isZero();
            graphRewindable.end();
        }

        @Test
        void transaction_multiple_begins() {
            graphRewindable.begin(TxnType.READ);
            assertThatExceptionOfType(GraphTransactionException.class)
                    .isThrownBy(() -> graphRewindable.begin());
            graphRewindable.end();
        }

        @Test
        void promote_READ() {
            graphRewindable.begin(TxnType.READ);
            assertThat(graphRewindable.promote()).isFalse();
            assertThatExceptionOfType(GraphTransactionException.class)
                    .isThrownBy(
                            () -> graphRewindable.promote(Transactional.Promote.READ_COMMITTED));
            assertThatExceptionOfType(GraphTransactionException.class)
                    .isThrownBy(() -> graphRewindable.promote(Transactional.Promote.ISOLATED));
            graphRewindable.end();
        }

        @Test
        void promote_WRITE() {
            graphRewindable.begin(TxnType.WRITE);
            assertThat(graphRewindable.promote()).isTrue();
            assertThat(graphRewindable.promote(Transactional.Promote.ISOLATED)).isTrue();
            assertThat(graphRewindable.promote(Transactional.Promote.READ_COMMITTED)).isTrue();
            graphRewindable.end();
        }

        @Test
        void promote_READ_PROMOTE() {
            graphRewindable.begin(TxnType.READ_PROMOTE);
            assertThat(graphRewindable.promote()).isTrue();
            assertThat(graphRewindable.promote(Transactional.Promote.ISOLATED)).isTrue();
            assertThat(graphRewindable.promote(Transactional.Promote.READ_COMMITTED)).isTrue();
            graphRewindable.end();
        }

        @Test
        void promote_READ_COMMITED_PROMOTE() {
            graphRewindable.begin(TxnType.READ_COMMITTED_PROMOTE);
            assertThat(graphRewindable.promote()).isTrue();
            assertThat(graphRewindable.promote(Transactional.Promote.ISOLATED)).isTrue();
            assertThat(graphRewindable.promote(Transactional.Promote.READ_COMMITTED)).isTrue();
            graphRewindable.end();
        }

        @Test
        void commit() {
            graphRewindable.begin(TxnType.WRITE);
            graphRewindable.commit();
            assertThat(graphRewindable.isIsomorphicWith(GraphFactory.createDefaultGraph()))
                    .isTrue();
            graphRewindable.end();
        }

        @Test
        void abort() {
            graphRewindable.begin(TxnType.WRITE);
            graphRewindable.abort();
            assertThat(graphRewindable.isIsomorphicWith(GraphFactory.createDefaultGraph()))
                    .isTrue();
            graphRewindable.end();
        }

        @Test
        void end() {
            graphRewindable.begin();
            assertThatNoException().isThrownBy(() -> graphRewindable.end());
            assertThat(graphRewindable.isInTransaction()).isFalse();
        }

        @Test
        void transactionMode_read() {
            graphRewindable.begin(TxnType.READ);
            assertThat(graphRewindable.transactionMode())
                    .isInstanceOf(ReadWrite.class)
                    .isEqualTo(ReadWrite.READ);
            graphRewindable.end();
        }

        @Test
        void transactionMode_write() {
            graphRewindable.begin(TxnType.WRITE);
            assertThat(graphRewindable.transactionMode())
                    .isInstanceOf(ReadWrite.class)
                    .isEqualTo(ReadWrite.WRITE);
            graphRewindable.end();
        }

        @Test
        void transactionType_read() {
            graphRewindable.begin(TxnType.READ);
            assertThat(graphRewindable.transactionType())
                    .isInstanceOf(TxnType.class)
                    .isEqualTo(TxnType.READ);
            graphRewindable.end();
        }

        @Test
        void transactionType_write() {
            graphRewindable.begin(TxnType.WRITE);
            assertThat(graphRewindable.transactionType())
                    .isInstanceOf(TxnType.class)
                    .isEqualTo(TxnType.WRITE);
            graphRewindable.end();
        }

        @Test
        void isInTransaction() {
            assertThat(graphRewindable.isInTransaction()).isFalse();
            graphRewindable.begin(TxnType.READ);
            assertThat(graphRewindable.isInTransaction()).isTrue();
            graphRewindable.end();
            assertThat(graphRewindable.isInTransaction()).isFalse();
        }
    }

    @SuppressWarnings("java:S5976")
    @Nested
    class Transactions {

        List<Triple> triples;

        @BeforeEach
        void setUp() {
            triples = new ArrayList<>();
            triples.add(triple("a a a"));
            triples.add(triple("a a b"));
            triples.add(triple("a a c"));
            triples.add(triple("a b a"));
            triples.add(triple("a b b"));
            triples.add(triple("a b c"));
            triples.add(triple("a c a"));
            triples.add(triple("a c b"));
            triples.add(triple("a c c"));
            triples.add(triple("b a a"));
            triples.add(triple("b a b"));
            triples.add(triple("b a c"));
            triples.add(triple("b b a"));
            triples.add(triple("b b b"));
            triples.add(triple("b b c"));
            triples.add(triple("b c a"));
            triples.add(triple("b c b"));
            triples.add(triple("b c c"));
            triples.add(triple("c a a"));
            triples.add(triple("c a b"));
            triples.add(triple("c a c"));
            triples.add(triple("c b a"));
            triples.add(triple("c b b"));
            triples.add(triple("c b c"));
            triples.add(triple("c c a"));
            triples.add(triple("c c b"));
            triples.add(triple("c c c"));
        }

        @AfterEach
        void tearDown() {
            triples = null;
        }

        @Test
        void addTriples() {
            GraphRewindable graphRewindable =
                    new GraphRewindable(GraphFactory.createDefaultGraph(), 28, 5);
            for (Triple t : triples) {
                graphRewindable.begin(TxnType.WRITE);
                graphRewindable.add(t);
                graphRewindable.commit();
                graphRewindable.end();
            }
            graphRewindable.begin(TxnType.READ);
            assertThat(graphRewindable.size()).isEqualTo(27);
            assertThat(graphRewindable.find().toList()).hasSameElementsAs(triples);
            graphRewindable.end();
        }

        @Test
        void addNodes() {
            GraphRewindable graphRewindable =
                    new GraphRewindable(GraphFactory.createDefaultGraph(), 28, 5);
            for (Triple t : triples) {
                graphRewindable.begin(TxnType.WRITE);
                graphRewindable.add(t.getSubject(), t.getPredicate(), t.getObject());
                graphRewindable.commit();
                graphRewindable.end();
            }
            graphRewindable.begin(TxnType.READ);
            assertThat(graphRewindable.size()).isEqualTo(27);
            assertThat(graphRewindable.find().toList()).hasSameElementsAs(triples);
            graphRewindable.end();
        }

        @Test
        void addThenDeleteSome() {
            GraphRewindable graphRewindable =
                    new GraphRewindable(GraphFactory.createDefaultGraph(), 28, 5);
            graphRewindable.begin(TxnType.WRITE);
            for (Triple t : triples) {
                graphRewindable.add(t);
            }
            assertThat(graphRewindable.size()).isEqualTo(27);
            assertThat(graphRewindable.find().toList()).hasSameElementsAs(triples);

            graphRewindable.delete(triple("a a a"));
            graphRewindable.delete(triple("a a b"));
            graphRewindable.delete(triple("a a c"));

            triples.remove(triple("a a a"));
            triples.remove(triple("a a b"));
            triples.remove(triple("a a c"));
            assertThat(graphRewindable.size()).isEqualTo(24);
            assertThat(graphRewindable.find().toList()).hasSameElementsAs(triples);
            graphRewindable.commit();
            graphRewindable.end();
        }

        @Test
        void maxingTransactionCount() {
            GraphRewindable graphRewindable =
                    new GraphRewindable(GraphFactory.createDefaultGraph(), 5, 5);
            for (Triple t : triples) {
                graphRewindable.begin(TxnType.WRITE);
                graphRewindable.add(t);
                graphRewindable.commit();
                graphRewindable.end();
            }
            graphRewindable.begin(TxnType.READ);
            assertThat(graphRewindable.size()).isEqualTo(27);
            assertThat(graphRewindable.find().toList()).hasSameElementsAs(triples);
            graphRewindable.end();
        }

        @Test
        void maxingTransactionCount_minSize() {
            GraphRewindable graphRewindable =
                    new GraphRewindable(GraphFactory.createDefaultGraph(), 1, 1);
            for (Triple t : triples) {
                graphRewindable.begin(TxnType.WRITE);
                graphRewindable.add(t);
                graphRewindable.commit();
                graphRewindable.end();
            }
            graphRewindable.begin(TxnType.READ);
            assertThat(graphRewindable.size()).isEqualTo(27);
            assertThat(graphRewindable.find().toList()).hasSameElementsAs(triples);
            graphRewindable.end();
        }

        @Test
        void abort_successful() {
            GraphRewindable graphRewindable =
                    new GraphRewindable(GraphFactory.createDefaultGraph(), 1, 1);
            graphRewindable.begin(TxnType.WRITE);
            for (Triple t : triples) {
                graphRewindable.add(t);
            }
            assertThat(graphRewindable.size()).isEqualTo(27);
            graphRewindable.abort();
            assertThat(graphRewindable.size()).isZero();
            graphRewindable.end();
        }

        @Test
        void abort_read_throwsException() {
            GraphRewindable graphRewindable =
                    new GraphRewindable(GraphFactory.createDefaultGraph(), 1, 1);
            graphRewindable.begin(TxnType.READ);
            assertThatExceptionOfType(GraphTransactionException.class)
                    .isThrownBy(graphRewindable::abort);
            graphRewindable.end();
        }

        @Test
        void end_withUncommittedChanges_abort() {
            GraphRewindable graphRewindable =
                    new GraphRewindable(GraphFactory.createDefaultGraph(), 1, 1);
            graphRewindable.begin(TxnType.WRITE);
            for (Triple t : triples) {
                graphRewindable.add(t);
            }
            assertThat(graphRewindable.size()).isEqualTo(27);
            assertThatNoException().isThrownBy(graphRewindable::end);
            graphRewindable.begin(TxnType.READ);
            assertThat(graphRewindable.size()).isZero();
            graphRewindable.end();
        }

        @Test
        void commit_readTransaction_throwException() {
            GraphRewindable graphRewindable =
                    new GraphRewindable(GraphFactory.createDefaultGraph(), 1, 1);
            graphRewindable.begin(TxnType.READ);
            assertThatExceptionOfType(GraphTransactionException.class)
                    .isThrownBy(graphRewindable::commit);
            graphRewindable.end();
        }

        @Test
        void promote_readTransaction_fails() {
            GraphRewindable graphRewindable =
                    new GraphRewindable(GraphFactory.createDefaultGraph(), 1, 1);
            graphRewindable.begin(TxnType.READ);
            assertThat(graphRewindable.promote()).isFalse();
            assertThatExceptionOfType(GraphTransactionException.class)
                    .isThrownBy(
                            () -> graphRewindable.promote(Transactional.Promote.READ_COMMITTED));
            assertThatExceptionOfType(GraphTransactionException.class)
                    .isThrownBy(() -> graphRewindable.promote(Transactional.Promote.ISOLATED));
            graphRewindable.end();
        }

        @Test
        void promote_readPromote_successful() {
            GraphRewindable graphRewindable =
                    new GraphRewindable(GraphFactory.createDefaultGraph(), 1, 1);
            graphRewindable.begin(TxnType.READ_PROMOTE);
            assertThat(graphRewindable.promote(Transactional.Promote.READ_COMMITTED)).isTrue();
            assertThat(graphRewindable.promote(Transactional.Promote.ISOLATED)).isTrue();
            graphRewindable.end();
        }

        @Test
        void promote_readCommittedPromote_successful() {
            GraphRewindable graphRewindable =
                    new GraphRewindable(GraphFactory.createDefaultGraph(), 1, 1);
            graphRewindable.begin(TxnType.READ_COMMITTED_PROMOTE);
            assertThat(graphRewindable.promote(Transactional.Promote.READ_COMMITTED)).isTrue();
            assertThat(graphRewindable.promote(Transactional.Promote.ISOLATED)).isTrue();
            graphRewindable.end();
        }
    }

    @Nested
    class VersionControl {

        List<Triple> triples;

        @BeforeEach
        void setUp() {
            triples = new ArrayList<>();
            triples.add(triple("a a a"));
            triples.add(triple("a a b"));
            triples.add(triple("a a c"));
            triples.add(triple("a b a"));
            triples.add(triple("a b b"));
            triples.add(triple("a b c"));
            triples.add(triple("a c a"));
            triples.add(triple("a c b"));
            triples.add(triple("a c c"));
            triples.add(triple("b a a"));
            triples.add(triple("b a b"));
            triples.add(triple("b a c"));
            triples.add(triple("b b a"));
            triples.add(triple("b b b"));
            triples.add(triple("b b c"));
            triples.add(triple("b c a"));
            triples.add(triple("b c b"));
            triples.add(triple("b c c"));
            triples.add(triple("c a a"));
            triples.add(triple("c a b"));
            triples.add(triple("c a c"));
            triples.add(triple("c b a"));
            triples.add(triple("c b b"));
            triples.add(triple("c b c"));
            triples.add(triple("c c a"));
            triples.add(triple("c c b"));
            triples.add(triple("c c c"));
        }

        @AfterEach
        void tearDown() {
            triples = null;
        }

        @ParameterizedTest
        @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 12, 15, 20, 23, 25, 28, 30, 50, 1234})
        void undoUntilOldest_thenRedoUntilNewest_compressCount1(int maxVersions) {
            GraphRewindable graphRewindable =
                    new GraphRewindable(GraphFactory.createDefaultGraph(), maxVersions, 1);
            for (Triple t : triples) {
                graphRewindable.begin(TxnType.WRITE);
                graphRewindable.add(t);
                graphRewindable.commit();
                graphRewindable.end();
            }
            maxVersions = Math.min(maxVersions, triples.size() + 1);

            for (int i = 0; i < maxVersions - 1; i++) {
                graphRewindable.undo();
            }
            graphRewindable.begin(TxnType.READ);
            assertThat(graphRewindable.size()).isEqualTo(triples.size() - (maxVersions - 1));
            graphRewindable.end();
            assertThatExceptionOfType(GraphVersionControlException.class)
                    .isThrownBy(graphRewindable::undo);

            // redo
            for (int i = 0; i < maxVersions - 1; i++) {
                graphRewindable.redo();
            }
            graphRewindable.begin(TxnType.READ);
            assertThat(graphRewindable.size()).isEqualTo(triples.size());
            graphRewindable.end();
            assertThatExceptionOfType(GraphVersionControlException.class)
                    .isThrownBy(graphRewindable::redo);
        }

        @Test
        void undoUntilOldest_thenRedoUntilNewest_compressCount5() {
            GraphRewindable graphRewindable =
                    new GraphRewindable(GraphFactory.createDefaultGraph(), 10, 5);
            for (Triple t : triples) {
                graphRewindable.begin(TxnType.WRITE);
                graphRewindable.add(t);
                graphRewindable.commit();
                graphRewindable.end();
            }

            for (int i = 0; i < 7; i++) {
                graphRewindable.undo();
            }
            graphRewindable.begin(TxnType.READ);
            assertThat(graphRewindable.size()).isEqualTo(20);
            graphRewindable.end();
            assertThatExceptionOfType(GraphVersionControlException.class)
                    .isThrownBy(graphRewindable::undo);

            // redo
            for (int i = 0; i < 7; i++) {
                graphRewindable.redo();
            }
            graphRewindable.begin(TxnType.WRITE);
            assertThat(graphRewindable.size()).isEqualTo(triples.size());
            graphRewindable.end();
            assertThatExceptionOfType(GraphVersionControlException.class)
                    .isThrownBy(graphRewindable::redo);
        }

        @Test
        void undoUncommitedChanges() {
            GraphRewindable graphRewindable =
                    new GraphRewindable(GraphFactory.createDefaultGraph(), 30, 5);
            for (Triple t : triples) {
                graphRewindable.begin(TxnType.WRITE);
                graphRewindable.add(t);
                graphRewindable.commit();
                graphRewindable.end();
            }
            graphRewindable.begin(TxnType.WRITE);
            graphRewindable.add(triple("m m m"));
            assertThat(graphRewindable.size()).isEqualTo(triples.size() + 1);
            graphRewindable.commit();
            graphRewindable.end();
            graphRewindable.undo();
            graphRewindable.begin(TxnType.READ);
            assertThat(graphRewindable.size()).isEqualTo(triples.size());
            graphRewindable.end();
        }

        @Test
        void restore_deltasExist_restoresVersion() {
            // Arrange
            GraphRewindable graphRewindable =
                    new GraphRewindable(GraphFactory.createDefaultGraph(), 20, 1);
            graphRewindable.begin(TxnType.WRITE);
            graphRewindable.add(triple("a a a"));
            var version = UUID.fromString(graphRewindable.currentDelta.getVersionId().toString());
            graphRewindable.commit();
            graphRewindable.add(triple("b b b"));
            graphRewindable.commit();
            graphRewindable.end();

            // Act
            graphRewindable.restore(version);

            // Assert
            graphRewindable.begin(TxnType.READ);
            assertAll(
                    () -> assertThat(graphRewindable.contains(triple("a a a"))).isTrue(),
                    () -> assertThat(graphRewindable.contains(triple("b b b"))).isFalse());
            graphRewindable.end();
        }

        @Test
        void restore_versionDoesNotExist_throwsException() {
            // Arrange
            GraphRewindable graphRewindable =
                    new GraphRewindable(GraphFactory.createDefaultGraph(), 20, 1);

            // Act + Assert
            assertThrows(GraphVersionControlException.class, () -> graphRewindable.restore(null));
        }
    }

    @Nested
    class GraphOperations {

        @Test
        void close_read_throwsException() {
            GraphRewindable graphRewindable =
                    new GraphRewindable(GraphFactory.createDefaultGraph(), 1, 1);
            graphRewindable.begin(TxnType.READ);
            assertThatExceptionOfType(GraphNotInAWriteTransactionException.class)
                    .isThrownBy(graphRewindable::close);
            graphRewindable.end();
        }

        @Test
        void close_write() {
            GraphRewindable graphRewindable =
                    new GraphRewindable(GraphFactory.createDefaultGraph(), 1, 1);
            graphRewindable.begin(TxnType.WRITE);
            assertThatNoException().isThrownBy(graphRewindable::close);
            assertThat(graphRewindable.isClosed()).isTrue();
        }

        @Test
        void add_read_throwsException() {
            GraphRewindable graphRewindable =
                    new GraphRewindable(GraphFactory.createDefaultGraph(), 1, 1);
            Node s = NodeFactory.createURI("a");
            Node p = NodeFactory.createURI("a");
            Node o = NodeFactory.createURI("a");
            graphRewindable.begin(TxnType.READ);
            assertThatExceptionOfType(GraphNotInAWriteTransactionException.class)
                    .isThrownBy(() -> graphRewindable.add(s, p, o));
            graphRewindable.end();
            graphRewindable.begin(TxnType.READ);
            assertThat(graphRewindable.size()).isZero();
            graphRewindable.end();
        }

        @Test
        void delete_read_throwsException() {
            GraphRewindable graphRewindable =
                    new GraphRewindable(GraphFactory.createDefaultGraph(), 1, 1);
            graphRewindable.begin(TxnType.WRITE);
            graphRewindable.add(
                    NodeFactory.createURI("a"),
                    NodeFactory.createURI("a"),
                    NodeFactory.createURI("a"));
            graphRewindable.commit();
            graphRewindable.end();
            graphRewindable.begin(TxnType.READ);
            assertThatExceptionOfType(GraphNotInAWriteTransactionException.class)
                    .isThrownBy(() -> graphRewindable.delete(Node.ANY, Node.ANY, Node.ANY));
            assertThat(graphRewindable.size()).isEqualTo(1);
            graphRewindable.end();
        }

        @Test
        void remove_read_throwsException() {
            GraphRewindable graphRewindable =
                    new GraphRewindable(GraphFactory.createDefaultGraph(), 1, 1);
            graphRewindable.begin(TxnType.WRITE);
            graphRewindable.add(
                    NodeFactory.createURI("a"),
                    NodeFactory.createURI("a"),
                    NodeFactory.createURI("a"));
            graphRewindable.commit();
            graphRewindable.end();
            graphRewindable.begin(TxnType.READ);
            assertThatExceptionOfType(GraphNotInAWriteTransactionException.class)
                    .isThrownBy(() -> graphRewindable.remove(Node.ANY, Node.ANY, Node.ANY));
            assertThat(graphRewindable.size()).isEqualTo(1);
            graphRewindable.end();
        }

        @Test
        void remove_write() {
            GraphRewindable graphRewindable =
                    new GraphRewindable(GraphFactory.createDefaultGraph(), 1, 1);
            graphRewindable.begin(TxnType.WRITE);
            graphRewindable.add(
                    NodeFactory.createURI("a"),
                    NodeFactory.createURI("a"),
                    NodeFactory.createURI("a"));
            graphRewindable.commit();
            assertThat(graphRewindable.size()).isEqualTo(1);
            graphRewindable.remove(Node.ANY, Node.ANY, Node.ANY);
            graphRewindable.commit();
            assertThat(graphRewindable.size()).isZero();
            graphRewindable.end();
        }

        @Test
        void clear_read_throwsException() {
            GraphRewindable graphRewindable =
                    new GraphRewindable(GraphFactory.createDefaultGraph(), 1, 1);
            graphRewindable.begin(TxnType.READ);
            assertThatExceptionOfType(GraphNotInAWriteTransactionException.class)
                    .isThrownBy(graphRewindable::clear);
            assertThat(graphRewindable.size()).isZero();
            graphRewindable.end();
        }

        @Test
        void contains() {
            GraphRewindable graphRewindable =
                    new GraphRewindable(GraphFactory.createDefaultGraph(), 1, 1);
            graphRewindable.begin(TxnType.WRITE);
            graphRewindable.add(
                    NodeFactory.createURI("a"),
                    NodeFactory.createURI("a"),
                    NodeFactory.createURI("a"));
            graphRewindable.commit();
            assertThat(
                            graphRewindable.contains(
                                    NodeFactory.createURI("a"),
                                    NodeFactory.createURI("a"),
                                    NodeFactory.createURI("a")))
                    .isTrue();
            graphRewindable.end();
        }

        @Test
        void find() {
            GraphRewindable graphRewindable =
                    new GraphRewindable(GraphFactory.createDefaultGraph(), 1, 1);
            graphRewindable.begin(TxnType.WRITE);
            graphRewindable.add(
                    NodeFactory.createURI("a"),
                    NodeFactory.createURI("a"),
                    NodeFactory.createURI("a"));
            graphRewindable.commit();
            assertThat(graphRewindable.find(Node.ANY, Node.ANY, Node.ANY).toList()).hasSize(1);
            graphRewindable.end();
        }
    }

    @Nested
    class MultiThreadTests {

        GraphRewindable graphRewindable;

        ExecutorService executor;

        @BeforeEach
        void setUp() {
            graphRewindable = new GraphRewindable(GraphFactory.createDefaultGraph(), 1, 1);
            executor = Executors.newSingleThreadExecutor();
        }

        @AfterEach
        void tearDown() {
            executor.shutdown();
            graphRewindable.begin(TxnType.WRITE);
            graphRewindable.close();
            graphRewindable.end();
            graphRewindable = null;
        }

        @Test
        void begin_multipleReadTransactions() {
            executor.submit(() -> graphRewindable.begin(TxnType.READ));
            assertThatNoException().isThrownBy(() -> graphRewindable.begin(TxnType.READ));
            graphRewindable.end();
            executor.submit(graphRewindable::end);
        }

        @Test
        void begin_anyTransactionWhenAnotherIsWriting() {
            graphRewindable.begin(TxnType.WRITE);
            executor.submit(() -> graphRewindable.begin(TxnType.WRITE));
            executor.submit(() -> assertThat(graphRewindable.isInTransaction()).isFalse());
            assertThat(graphRewindable.isInTransaction()).isTrue();
            graphRewindable.end();
            executor.submit(() -> assertThat(graphRewindable.isInTransaction()).isTrue());
            executor.submit(graphRewindable::end);
            executor.submit(() -> assertThat(graphRewindable.isInTransaction()).isFalse());
        }

        @Test
        void changesShouldNotAffectOtherTransactions() {
            graphRewindable.begin(TxnType.WRITE);
            Future<?> beginFuture = executor.submit(() -> graphRewindable.begin(TxnType.WRITE));
            Future<?> addFuture = executor.submit(() -> graphRewindable.add(triple("a a a")));
            Future<?> commitFuture = executor.submit(graphRewindable::commit);
            assertThat(graphRewindable.contains(triple("a a a"))).isFalse();
            graphRewindable.end();
            waitFor(beginFuture);
            waitFor(addFuture);
            waitFor(commitFuture);
            Future<?> endFuture = executor.submit(graphRewindable::end);
            waitFor(endFuture);
            graphRewindable.begin(TxnType.READ);
            assertThat(graphRewindable.contains(triple("a a a"))).isTrue();
            graphRewindable.end();
        }

        private void waitFor(Future<?> future) {
            assertThatNoException().isThrownBy(future::get);
        }
    }
}

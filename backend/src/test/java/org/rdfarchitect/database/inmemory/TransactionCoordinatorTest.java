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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.exception.graph.GraphTransactionException;
import org.rdfarchitect.models.changelog.ChangeLogParticipant;
import org.rdfarchitect.models.changelog.ParticipantId;
import org.rdfarchitect.models.changelog.WorkspaceChangeLog;
import org.rdfarchitect.rdf.graph.wrapper.TransactionParticipant;
import org.rdfarchitect.rdf.graph.wrapper.WorkspaceTransactionContext;

/**
 * Covers what a workspace transaction cannot produce on its own: a participant that fails in the
 * middle of a commit.
 */
class TransactionCoordinatorTest {

    private WorkspaceTransactionContext txnContext;
    private WorkspaceChangeLog changeLog;
    private TransactionCoordinator coordinator;

    @BeforeEach
    void setUp() {
        txnContext = new WorkspaceTransactionContext("workspace");
        changeLog = new WorkspaceChangeLog("loaded workspace", 20);
        coordinator =
                new TransactionCoordinator(
                        txnContext,
                        changeLog,
                        _ -> ParticipantId.ofWorkspace(ParticipantId.Kind.DIAGRAMS));
        txnContext.begin(ReadWrite.WRITE);
    }

    @AfterEach
    void tearDown() {
        while (txnContext.isInTransaction()) {
            txnContext.end();
        }
    }

    @Test
    void commit_whenAParticipantFails_takesBackTheVersionsAlreadyCut() {
        var first = new StubParticipant();
        var failing = new StubParticipant().failingOnCommit();
        txnContext.enroll(first);
        txnContext.enroll(failing);

        assertThatThrownBy(() -> coordinator.commit("a change touching both"))
                .isInstanceOf(IllegalStateException.class);

        assertThat(first.commits).isEqualTo(1);
        assertThat(first.undos).isEqualTo(1);
        assertThat(first.redoHistoryDiscards).isEqualTo(1);
    }

    @Test
    void commit_whenAParticipantFails_rollsBackTheOnesThatHadNotCommittedYet() {
        var failing = new StubParticipant().failingOnCommit();
        var untouched = new StubParticipant();
        txnContext.enroll(failing);
        txnContext.enroll(untouched);

        assertThatThrownBy(() -> coordinator.commit("a change touching both"))
                .isInstanceOf(IllegalStateException.class);

        assertThat(untouched.commits).isZero();
        assertThat(untouched.aborts).isEqualTo(1);
    }

    @Test
    void commit_whenAParticipantFails_writesNoChangelogEntry() {
        txnContext.enroll(new StubParticipant());
        txnContext.enroll(new StubParticipant().failingOnCommit());

        assertThatThrownBy(() -> coordinator.commit("a change touching both"))
                .isInstanceOf(IllegalStateException.class);

        assertThat(changeLog.undoHistory()).hasSize(1);
        assertThat(changeLog.canUndo()).isFalse();
    }

    @Test
    void commit_whenAParticipantFails_killsTheTransaction() {
        txnContext.enroll(new StubParticipant().failingOnCommit());

        assertThatThrownBy(() -> coordinator.commit("a change"))
                .isInstanceOf(IllegalStateException.class);

        assertThat(txnContext.isAborted()).isTrue();
        assertThatThrownBy(() -> coordinator.commit("a second attempt"))
                .isInstanceOf(GraphTransactionException.class);
    }

    @Test
    void commit_whenTakingTheCommitBackAlsoFails_stillReportsTheOriginalFailure() {
        txnContext.enroll(new StubParticipant().failingOnUndo());
        txnContext.enroll(new StubParticipant().failingOnCommit());

        assertThatThrownBy(() -> coordinator.commit("a change touching both"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("commit failed");
    }

    @Test
    void commit_whenNothingFails_leavesNoVersionTakenBack() {
        var participant = new StubParticipant();
        txnContext.enroll(participant);

        coordinator.commit("a change");

        assertThat(participant.commits).isEqualTo(1);
        assertThat(participant.undos).isZero();
        assertThat(changeLog.canUndo()).isTrue();
    }

    private static final class StubParticipant
            implements TransactionParticipant, ChangeLogParticipant {

        private boolean failOnCommit;
        private boolean failOnUndo;

        private int commits;
        private int aborts;
        private int undos;
        private int redoHistoryDiscards;
        private int folds;

        StubParticipant failingOnCommit() {
            failOnCommit = true;
            return this;
        }

        StubParticipant failingOnUndo() {
            failOnUndo = true;
            return this;
        }

        @Override
        public void commit() {
            if (failOnCommit) {
                throw new IllegalStateException("commit failed");
            }
            commits++;
        }

        @Override
        public void abort() {
            aborts++;
        }

        @Override
        public boolean hasChanges() {
            return true;
        }

        @Override
        public void undo() {
            if (failOnUndo) {
                throw new IllegalStateException("undo failed");
            }
            undos++;
        }

        @Override
        public void redo() {
            // not reached by these tests
        }

        @Override
        public void discardOldestVersion() {
            // not reached by these tests
        }

        @Override
        public void discardRedoHistory() {
            redoHistoryDiscards++;
        }

        @Override
        public void foldLastVersionIntoPrevious() {
            folds++;
        }
    }
}

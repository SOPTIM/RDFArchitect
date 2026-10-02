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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.exception.graph.GraphNotInATransactionException;
import org.rdfarchitect.exception.graph.GraphNotInAWriteTransactionException;
import org.rdfarchitect.exception.graph.GraphTransactionException;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

class WorkspaceTransactionContextTest {

    private WorkspaceTransactionContext workspaceA;
    private WorkspaceTransactionContext workspaceB;
    private TransactionParticipant participant;
    private TransactionParticipant otherParticipant;

    @BeforeEach
    void setUp() {
        workspaceA = new WorkspaceTransactionContext("A");
        workspaceB = new WorkspaceTransactionContext("B");
        participant = new StubParticipant();
        otherParticipant = new StubParticipant();
    }

    @AfterEach
    void tearDown() {
        while (workspaceA.isInTransaction()) {
            workspaceA.end();
        }
        while (workspaceB.isInTransaction()) {
            workspaceB.end();
        }
    }

    // -------------------------------------------------------------------------
    // begin / end
    // -------------------------------------------------------------------------

    @Test
    void begin_outermost_reportsThatTheLockMustBeAcquired() {
        assertThat(workspaceA.begin(ReadWrite.WRITE)).isTrue();
    }

    @Test
    void begin_nested_joinsWithoutReportingOutermost() {
        workspaceA.begin(ReadWrite.WRITE);

        assertThat(workspaceA.begin(ReadWrite.WRITE)).isFalse();
    }

    @Test
    void end_innerLevel_doesNotReleaseTheTransaction() {
        workspaceA.begin(ReadWrite.WRITE);
        workspaceA.begin(ReadWrite.WRITE);

        assertThat(workspaceA.end()).isFalse();
        assertThat(workspaceA.isInTransaction()).isTrue();
    }

    @Test
    void end_outermostLevel_releasesTheTransaction() {
        workspaceA.begin(ReadWrite.WRITE);
        workspaceA.begin(ReadWrite.WRITE);
        workspaceA.end();

        assertThat(workspaceA.end()).isTrue();
        assertThat(workspaceA.isInTransaction()).isFalse();
    }

    @Test
    void end_whenNotInTransaction_throwsException() {
        assertThatThrownBy(() -> workspaceA.end())
                .isInstanceOf(GraphNotInATransactionException.class);
    }

    @Test
    void isOutermost_reflectsTheNestingDepth() {
        workspaceA.begin(ReadWrite.WRITE);
        assertThat(workspaceA.isOutermost()).isTrue();

        workspaceA.begin(ReadWrite.WRITE);
        assertThat(workspaceA.isOutermost()).isFalse();

        workspaceA.end();
        assertThat(workspaceA.isOutermost()).isTrue();
    }

    // -------------------------------------------------------------------------
    // Rule: one workspace per thread
    // -------------------------------------------------------------------------

    @Test
    void begin_onSecondWorkspace_throwsException() {
        workspaceA.begin(ReadWrite.READ);

        assertThatThrownBy(() -> workspaceB.begin(ReadWrite.READ))
                .isInstanceOf(GraphTransactionException.class)
                .hasMessageContaining("already inside workspace 'A'");
    }

    @Test
    void isInTransaction_whileOtherWorkspaceIsActive_returnsFalse() {
        workspaceA.begin(ReadWrite.WRITE);

        assertThat(workspaceB.isInTransaction()).isFalse();
    }

    // -------------------------------------------------------------------------
    // Rule: no read-to-write promotion
    // -------------------------------------------------------------------------

    @Test
    void begin_writeInsideRead_throwsException() {
        workspaceA.begin(ReadWrite.READ);

        assertThatThrownBy(() -> workspaceA.begin(ReadWrite.WRITE))
                .isInstanceOf(GraphTransactionException.class)
                .hasMessageContaining("Cannot promote");
    }

    @Test
    void begin_readInsideWrite_joinsAsWrite() {
        workspaceA.begin(ReadWrite.WRITE);

        assertThat(workspaceA.begin(ReadWrite.READ)).isFalse();
        assertThat(workspaceA.transactionMode()).isEqualTo(ReadWrite.WRITE);
    }

    @Test
    void transactionMode_whenNotInTransaction_throwsException() {
        assertThatThrownBy(() -> workspaceA.transactionMode())
                .isInstanceOf(GraphNotInATransactionException.class);
    }

    // -------------------------------------------------------------------------
    // Rule: participants enrol themselves
    // -------------------------------------------------------------------------

    @Test
    void enroll_recordsParticipantsInWriteOrder() {
        workspaceA.begin(ReadWrite.WRITE);

        workspaceA.enroll(otherParticipant);
        workspaceA.enroll(participant);

        assertThat(workspaceA.enrolledParticipants())
                .containsExactly(otherParticipant, participant);
    }

    @Test
    void enroll_repeatedForSameParticipant_isIdempotent() {
        workspaceA.begin(ReadWrite.WRITE);

        workspaceA.enroll(participant);
        workspaceA.enroll(participant);

        assertThat(workspaceA.enrolledParticipants()).containsExactly(participant);
    }

    @Test
    void enroll_fromAnInnerLevel_endsUpInTheSameTransaction() {
        workspaceA.begin(ReadWrite.WRITE);
        workspaceA.begin(ReadWrite.WRITE);

        workspaceA.enroll(participant);
        workspaceA.end();

        assertThat(workspaceA.enrolledParticipants()).containsExactly(participant);
    }

    @Test
    void enroll_duringReadTransaction_throwsException() {
        workspaceA.begin(ReadWrite.READ);

        assertThatThrownBy(() -> workspaceA.enroll(participant))
                .isInstanceOf(GraphNotInAWriteTransactionException.class);
    }

    @Test
    void enrolledParticipants_isUnmodifiable() {
        workspaceA.begin(ReadWrite.WRITE);
        var enrolled = workspaceA.enrolledParticipants();

        assertThatThrownBy(() -> enrolled.add(participant))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void clearEnrolled_startsTheNextCommitWithoutCarryOver() {
        workspaceA.begin(ReadWrite.WRITE);
        workspaceA.enroll(participant);

        workspaceA.clearEnrolled();

        assertThat(workspaceA.enrolledParticipants()).isEmpty();
    }

    @Test
    void enrolledParticipants_whenNotInTransaction_throwsException() {
        assertThatThrownBy(() -> workspaceA.enrolledParticipants())
                .isInstanceOf(GraphNotInATransactionException.class);
    }

    // -------------------------------------------------------------------------
    // Rule: only the outermost commit writes an entry
    // -------------------------------------------------------------------------

    @Test
    void addMessage_fromAnInnerCommit_isDroppedInFavourOfTheEnclosingOne() {
        // An inner commit describes a step of a larger action; the enclosing commit names what the
        // user actually did.
        workspaceA.begin(ReadWrite.WRITE);
        workspaceA.begin(ReadWrite.WRITE);
        workspaceA.addMessage("renamed the class in the diagrams");
        workspaceA.end();
        workspaceA.addMessage("renamed a class");

        assertThat(workspaceA.message()).isEqualTo("renamed a class");
    }

    @Test
    void addMessage_ignoresBlankMessages() {
        workspaceA.begin(ReadWrite.WRITE);

        workspaceA.addMessage(null);
        workspaceA.addMessage("   ");

        assertThat(workspaceA.message()).isNull();
    }

    @Test
    void clearMessage_forgetsTheRecordedMessage() {
        workspaceA.begin(ReadWrite.WRITE);
        workspaceA.addMessage("something");

        workspaceA.clearMessage();

        assertThat(workspaceA.message()).isNull();
    }

    // -------------------------------------------------------------------------
    // Rule: an abort kills the whole transaction, not just its level
    // -------------------------------------------------------------------------

    @Test
    void markAborted_fromAnInnerLevel_abortsTheEnclosingTransaction() {
        workspaceA.begin(ReadWrite.WRITE);
        workspaceA.begin(ReadWrite.WRITE);

        workspaceA.markAborted();
        workspaceA.end();

        assertThat(workspaceA.isAborted()).isTrue();
    }

    @Test
    void enroll_afterAbort_throwsExceptionSoTheCallerCannotKeepWriting() {
        workspaceA.begin(ReadWrite.WRITE);
        workspaceA.markAborted();

        assertThatThrownBy(() -> workspaceA.enroll(participant))
                .isInstanceOf(GraphTransactionException.class)
                .hasMessageContaining("has been aborted");
    }

    @Test
    void addMessage_afterAbort_throwsException() {
        workspaceA.begin(ReadWrite.WRITE);
        workspaceA.markAborted();

        assertThatThrownBy(() -> workspaceA.addMessage("too late"))
                .isInstanceOf(GraphTransactionException.class);
    }

    @Test
    void clearEnrolled_afterAbort_stillWorksSoTheAbortCanCleanUp() {
        workspaceA.begin(ReadWrite.WRITE);
        workspaceA.enroll(participant);
        workspaceA.markAborted();

        workspaceA.clearEnrolled();

        assertThat(workspaceA.enrolledParticipants()).isEmpty();
    }

    @Test
    void abort_doesNotLeakIntoTheNextTransaction() {
        workspaceA.begin(ReadWrite.WRITE);
        workspaceA.markAborted();
        workspaceA.end();

        workspaceA.begin(ReadWrite.WRITE);

        assertThat(workspaceA.isAborted()).isFalse();
    }

    // -------------------------------------------------------------------------
    // Thread isolation
    // -------------------------------------------------------------------------

    @Test
    void transactionState_isNotSharedBetweenThreads() throws InterruptedException {
        workspaceA.begin(ReadWrite.READ);
        var seenInOtherThread = new AtomicReference<Boolean>();
        var started = new CountDownLatch(1);

        var other =
                new Thread(
                        () -> {
                            seenInOtherThread.set(workspaceA.isInTransaction());
                            started.countDown();
                        });
        other.start();

        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(seenInOtherThread.get()).isFalse();
        assertThat(workspaceA.isInTransaction()).isTrue();
    }

    private static final class StubParticipant implements TransactionParticipant {

        @Override
        public void commit() {
            // no state to commit
        }

        @Override
        public void abort() {
            // no state to discard
        }

        @Override
        public boolean hasChanges() {
            return false;
        }
    }
}

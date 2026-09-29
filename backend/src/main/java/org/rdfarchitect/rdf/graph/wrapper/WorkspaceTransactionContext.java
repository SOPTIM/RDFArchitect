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

import org.apache.jena.query.ReadWrite;
import org.rdfarchitect.exception.graph.GraphNotInATransactionException;
import org.rdfarchitect.exception.graph.GraphNotInAWriteTransactionException;
import org.rdfarchitect.exception.graph.GraphTransactionException;
import org.rdfarchitect.models.changelog.ContextDelta;
import org.rdfarchitect.models.changelog.ParticipantVersion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Per-workspace transaction state for the current thread. Replaces the per-graph {@link
 * TransactionContext} once transactions are owned by the workspace.
 *
 * <p>Transactions nest by joining: a {@link #begin(ReadWrite)} on a workspace that this thread is
 * already inside increments a depth counter and reuses the running transaction instead of failing.
 * Only the outermost {@code begin}/{@link #end()} pair is reported back to the caller, so that the
 * workspace acquires and releases its lock exactly once. The following rules are enforced:
 *
 * <ul>
 *   <li>A thread may be inside at most one workspace at a time. Beginning a transaction on a second
 *       workspace throws, because it would require a lock ordering across workspaces.
 *   <li>A read transaction cannot be promoted to a write transaction. {@link
 *       java.util.concurrent.locks.ReentrantReadWriteLock} cannot upgrade a read lock, so this
 *       would deadlock.
 *   <li>Participants enrol themselves on their first write. The enrolled set is the authoritative
 *       answer to "who changed in this transaction" and therefore determines which participants a
 *       commit records — no participant receives a version it did not earn.
 *   <li>Only the outermost commit produces a changelog entry. Inner commits contribute their
 *       message via {@link #addMessage(String)} and are otherwise invisible.
 * </ul>
 *
 * <p>All state is thread-local, so multiple readers may be inside the same workspace concurrently
 * without observing each other.
 */
public class WorkspaceTransactionContext {

    private static final ThreadLocal<Frame> currentFrame = new ThreadLocal<>();

    private final String workspaceName;

    public WorkspaceTransactionContext(String workspaceName) {
        this.workspaceName = workspaceName;
    }

    /** State of the transaction the current thread is inside, if any. */
    private static final class Frame {

        private final WorkspaceTransactionContext owner;
        private final ReadWrite mode;
        private final Set<TransactionParticipant> enrolled = new LinkedHashSet<>();
        private final List<String> messages = new ArrayList<>();
        private final List<ParticipantVersion> pendingVersions = new ArrayList<>();
        private final List<ContextDelta> pendingDeltas = new ArrayList<>();
        private int depth = 1;
        private boolean aborted;

        private Frame(WorkspaceTransactionContext owner, ReadWrite mode) {
            this.owner = owner;
            this.mode = mode;
        }
    }

    // -------------------------------------------------------------------------
    // Transaction lifecycle
    // -------------------------------------------------------------------------

    /**
     * Begins a transaction, or joins the one this thread already holds on this workspace.
     *
     * @param mode the requested transaction mode
     * @return {@code true} if this call opened the outermost transaction and the caller must
     *     acquire the workspace lock, {@code false} if it joined a running one
     * @throws GraphTransactionException if this thread is inside a different workspace, or if a
     *     write transaction is requested while a read transaction is running
     */
    public boolean begin(ReadWrite mode) {
        var frame = currentFrame.get();
        if (frame == null) {
            currentFrame.set(new Frame(this, mode));
            return true;
        }
        if (frame.owner != this) {
            throw new GraphTransactionException(
                    "Cannot begin a transaction on workspace '%s': this thread is already inside workspace '%s'."
                            .formatted(workspaceName, frame.owner.workspaceName));
        }
        if (mode == ReadWrite.WRITE && frame.mode == ReadWrite.READ) {
            throw new GraphTransactionException(
                    "Cannot promote the running read transaction on workspace '%s' to a write transaction."
                            .formatted(workspaceName));
        }
        frame.depth++;
        return false;
    }

    /**
     * Leaves the current transaction level.
     *
     * @return {@code true} if the outermost level was left and the caller must release the
     *     workspace lock, {@code false} if an enclosing level is still running
     * @throws GraphNotInATransactionException if this thread is not inside this workspace
     */
    public boolean end() {
        var frame = requireFrame();
        frame.depth--;
        if (frame.depth == 0) {
            currentFrame.remove();
            return true;
        }
        return false;
    }

    /**
     * Returns whether this thread is currently inside a transaction on <em>this</em> workspace. A
     * running transaction on a different workspace does not count.
     */
    public boolean isInTransaction() {
        var frame = currentFrame.get();
        return frame != null && frame.owner == this;
    }

    /**
     * Returns whether the current transaction level is the outermost one, i.e. whether the next
     * commit is the one that produces a changelog entry.
     *
     * @throws GraphNotInATransactionException if this thread is not inside this workspace
     */
    public boolean isOutermost() {
        return requireFrame().depth == 1;
    }

    /**
     * Returns the mode of the running transaction.
     *
     * @throws GraphNotInATransactionException if this thread is not inside this workspace
     */
    public ReadWrite transactionMode() {
        return requireFrame().mode;
    }

    // -------------------------------------------------------------------------
    // Participants
    // -------------------------------------------------------------------------

    /**
     * Registers a participant as changed by the running transaction. Participants call this on
     * their first write; repeated calls are idempotent.
     *
     * @param participant the participant that is about to be modified
     * @throws GraphNotInATransactionException if this thread is not inside this workspace
     * @throws GraphNotInAWriteTransactionException if the running transaction is read-only
     */
    public void enroll(TransactionParticipant participant) {
        var frame = requireUnabortedFrame();
        if (frame.mode == ReadWrite.READ) {
            throw new GraphNotInAWriteTransactionException();
        }
        frame.enrolled.add(participant);
    }

    /**
     * Returns the participants that enrolled in the running transaction, in the order they first
     * wrote.
     *
     * @throws GraphNotInATransactionException if this thread is not inside this workspace
     */
    public Set<TransactionParticipant> enrolledParticipants() {
        return Collections.unmodifiableSet(requireFrame().enrolled);
    }

    /**
     * Forgets all enrolled participants, so that the transaction can continue after a commit or
     * abort without carrying their registration over into the next commit.
     *
     * @throws GraphNotInATransactionException if this thread is not inside this workspace
     */
    public void clearEnrolled() {
        requireFrame().enrolled.clear();
    }

    // -------------------------------------------------------------------------
    // Commit messages
    // -------------------------------------------------------------------------

    /**
     * Records the message of a commit. Inner commits use this to contribute their description to
     * the single entry the outermost commit will write.
     *
     * @param message the commit message; blank messages are ignored
     * @throws GraphNotInATransactionException if this thread is not inside this workspace
     */
    public void addMessage(String message) {
        var frame = requireUnabortedFrame();
        if (message != null && !message.isBlank()) {
            frame.messages.add(message);
        }
    }

    /**
     * Returns the commit messages recorded during the running transaction, oldest first.
     *
     * @throws GraphNotInATransactionException if this thread is not inside this workspace
     */
    public List<String> messages() {
        return List.copyOf(requireFrame().messages);
    }

    /**
     * Forgets all recorded commit messages.
     *
     * @throws GraphNotInATransactionException if this thread is not inside this workspace
     */
    public void clearMessages() {
        requireFrame().messages.clear();
    }

    // -------------------------------------------------------------------------
    // Versions gained but not yet written to the changelog
    // -------------------------------------------------------------------------

    /**
     * Remembers the versions a commit produced. A commit that does not name itself contributes its
     * versions here, so that the entry written when the transaction ends covers them too and no
     * participant ends up a version ahead of the changelog.
     *
     * @param versions the versions the participants gained
     * @param deltas what changed, for display in the changelog
     * @throws GraphNotInATransactionException if this thread is not inside this workspace
     */
    public void addPendingVersions(List<ParticipantVersion> versions, List<ContextDelta> deltas) {
        var frame = requireUnabortedFrame();
        frame.pendingVersions.addAll(versions);
        frame.pendingDeltas.addAll(deltas);
    }

    /**
     * Returns the versions gained so far that no changelog entry covers yet.
     *
     * @throws GraphNotInATransactionException if this thread is not inside this workspace
     */
    public List<ParticipantVersion> pendingVersions() {
        return List.copyOf(requireFrame().pendingVersions);
    }

    /**
     * Returns the changes gained so far that no changelog entry covers yet.
     *
     * @throws GraphNotInATransactionException if this thread is not inside this workspace
     */
    public List<ContextDelta> pendingDeltas() {
        return List.copyOf(requireFrame().pendingDeltas);
    }

    /**
     * Forgets the pending versions, after an entry covering them has been written.
     *
     * @throws GraphNotInATransactionException if this thread is not inside this workspace
     */
    public void clearPendingVersions() {
        var frame = requireFrame();
        frame.pendingVersions.clear();
        frame.pendingDeltas.clear();
    }

    // -------------------------------------------------------------------------
    // Abort
    // -------------------------------------------------------------------------

    /**
     * Marks the whole transaction as aborted, no matter which nesting level aborted it. An abort is
     * never local to the level that triggered it: the levels share one set of participants, so
     * rolling back part of them would leave the rest half-written. After this, every further write
     * fails and only {@link #end()} still works, so that an enclosing caller cannot swallow the
     * failure and commit the remains.
     *
     * @throws GraphNotInATransactionException if this thread is not inside this workspace
     */
    public void markAborted() {
        requireFrame().aborted = true;
    }

    /**
     * Returns whether the running transaction has been aborted.
     *
     * @throws GraphNotInATransactionException if this thread is not inside this workspace
     */
    public boolean isAborted() {
        return requireFrame().aborted;
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private Frame requireFrame() {
        var frame = currentFrame.get();
        if (frame == null || frame.owner != this) {
            throw new GraphNotInATransactionException();
        }
        return frame;
    }

    private Frame requireUnabortedFrame() {
        var frame = requireFrame();
        if (frame.aborted) {
            throw new GraphTransactionException(
                    "The transaction on workspace '%s' has been aborted and cannot be used further."
                            .formatted(workspaceName));
        }
        return frame;
    }
}

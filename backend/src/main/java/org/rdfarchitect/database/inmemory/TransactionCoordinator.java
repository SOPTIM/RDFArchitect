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

import org.apache.jena.graph.Graph;
import org.apache.jena.query.ReadWrite;
import org.rdfarchitect.exception.graph.GraphNotInATransactionException;
import org.rdfarchitect.exception.graph.GraphTransactionException;
import org.rdfarchitect.models.changelog.ChangeLogParticipant;
import org.rdfarchitect.models.changelog.ContextDelta;
import org.rdfarchitect.models.changelog.ParticipantId;
import org.rdfarchitect.models.changelog.ParticipantVersion;
import org.rdfarchitect.models.changelog.WorkspaceChangeLog;
import org.rdfarchitect.models.changelog.WorkspaceChangeLogEntry;
import org.rdfarchitect.rdf.graph.GraphUtils;
import org.rdfarchitect.rdf.graph.wrapper.DeltaSource;
import org.rdfarchitect.rdf.graph.wrapper.SnapshotParticipant;
import org.rdfarchitect.rdf.graph.wrapper.TransactionParticipant;
import org.rdfarchitect.rdf.graph.wrapper.WorkspaceTransactionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * Turns the participants that a transaction changed into a changelog entry.
 *
 * <p>Committing a workspace transaction is more than forwarding the call: only participants that
 * actually changed gain a version, only the outermost commit writes an entry, and a commit that
 * does not name itself still has to be covered by one before the transaction ends — otherwise a
 * participant would be a version ahead of the log and the log would promise an undo it cannot
 * perform.
 *
 * <p>Separated from {@link Workspace} because this is the part of a transaction that carries rules;
 * the workspace itself only owns the lock and the data.
 */
class TransactionCoordinator {

    private static final Logger logger = LoggerFactory.getLogger(TransactionCoordinator.class);

    private static final String UNNAMED_CHANGE_MESSAGE = "changed workspace";

    private final WorkspaceTransactionContext txnContext;
    private final WorkspaceChangeLog changeLog;

    /**
     * Resolves what a participant represents. Asked on demand rather than remembered, so that a
     * renamed graph does not leave a stale identifier behind.
     */
    private final Function<TransactionParticipant, ParticipantId> identify;

    TransactionCoordinator(
            WorkspaceTransactionContext txnContext,
            WorkspaceChangeLog changeLog,
            Function<TransactionParticipant, ParticipantId> identify) {
        this.txnContext = txnContext;
        this.changeLog = changeLog;
        this.identify = identify;
    }

    /**
     * Commits everything the transaction changed so far.
     *
     * @param message what the change did
     */
    void commit(String message) {
        requireWriteTransaction();
        if (txnContext.isAborted()) {
            throw new GraphTransactionException("Cannot commit an aborted transaction.");
        }
        txnContext.addMessage(message);

        var enrolled = List.copyOf(txnContext.enrolledParticipants());
        enrolled.forEach(this::enhanceWithUUIDs);

        var versions = new ArrayList<ParticipantVersion>();
        var deltas = new ArrayList<ContextDelta>();
        var versioned = new ArrayList<TransactionParticipant>();
        var complete = false;
        try {
            for (var participant : enrolled) {
                if (!participant.hasChanges()) {
                    continue;
                }
                participant.commit();
                versioned.add(participant);
                recordVersion(participant, versions, deltas);
            }
            complete = true;
        } finally {
            if (!complete) {
                failCommit(versioned);
            }
        }
        txnContext.clearEnrolled();
        txnContext.addPendingVersions(versions, deltas);

        // Inner commits are invisible: they contribute their message to the entry the outermost
        // commit writes, so that one user action does not turn into several changelog entries.
        if (txnContext.isOutermost()) {
            writeChangeLogEntry();
        }
        logger.debug("Workspace committed: {}", message);
    }

    /**
     * Commits without adding a step to the history.
     *
     * <p>For a change the user did not make: the layout a diagram is given the first time it is
     * opened. It has to be stored — otherwise every visit lays it out anew — but offering to undo
     * it would mean offering to undo opening a diagram. The change is folded into the current
     * version instead, so the participants stay in step with the log.
     */
    void commitWithoutHistory() {
        requireWriteTransaction();
        if (txnContext.isAborted()) {
            throw new GraphTransactionException("Cannot commit an aborted transaction.");
        }
        if (!txnContext.isOutermost()) {
            throw new GraphTransactionException(
                    "Cannot skip the history from inside a transaction that records it.");
        }
        var enrolled = List.copyOf(txnContext.enrolledParticipants());
        var versioned = new ArrayList<TransactionParticipant>();
        var complete = false;
        try {
            for (var participant : enrolled) {
                if (!participant.hasChanges()) {
                    continue;
                }
                participant.commit();
                versioned.add(participant);
                if (participant instanceof ChangeLogParticipant rewindable) {
                    rewindable.foldLastVersionIntoPrevious();
                }
            }
            complete = true;
        } finally {
            if (!complete) {
                failCommit(versioned);
            }
        }
        txnContext.clearEnrolled();
        logger.debug("Workspace committed without recording history.");
    }

    /**
     * Leaves nothing of a commit that died halfway through.
     *
     * <p>A participant that already committed has cut a version and no longer considers itself
     * changed, so {@code abort()} alone would not reach it — its change would survive with no
     * changelog entry naming it, which is the one state the log must never be in. Its version is
     * therefore taken back explicitly before the usual abort handles the rest.
     *
     * <p>Runs from a {@code finally} block, so the failure that got us here is still on its way up:
     * a second failure while cleaning up is logged rather than thrown, so it cannot displace the
     * first.
     *
     * @param versioned the participants that committed during this attempt, in commit order
     */
    private void failCommit(List<TransactionParticipant> versioned) {
        logger.error("A participant failed while committing; taking the commit back.");
        for (int i = versioned.size() - 1; i >= 0; i--) {
            if (versioned.get(i) instanceof ChangeLogParticipant participant) {
                try {
                    participant.undo();
                    participant.discardRedoHistory();
                } catch (RuntimeException e) {
                    logger.error("Could not take back the version of a participant.", e);
                }
            }
        }
        abort();
    }

    /** Rolls back everything the transaction changed and marks it unusable. */
    void abort() {
        requireWriteTransaction();
        txnContext.enrolledParticipants().forEach(TransactionParticipant::abort);
        txnContext.clearEnrolled();
        txnContext.markAborted();
        logger.debug("Workspace transaction aborted.");
    }

    /**
     * Leaves the outermost write transaction in a state the changelog can describe: uncommitted
     * changes are rolled back, and versions that no entry covers yet still get one.
     *
     * <p>The second case means an inner commit succeeded and the enclosing one never followed,
     * which is a mistake in the calling code: the entry it writes can only carry a placeholder
     * name. Writing it anyway is still better than leaving a participant a version ahead of the
     * log, where the log would offer an undo nobody can perform.
     */
    void finishOutermostWrite() {
        if (!txnContext.enrolledParticipants().isEmpty()) {
            logger.warn("Ending write transaction with uncommitted changes — aborting.");
            txnContext.enrolledParticipants().forEach(TransactionParticipant::abort);
            txnContext.clearEnrolled();
        }
        if (!txnContext.pendingVersions().isEmpty()) {
            logger.warn(
                    "Ending write transaction with committed changes that no commit named; "
                            + "recording them as \"{}\".",
                    UNNAMED_CHANGE_MESSAGE);
            writeChangeLogEntry();
        }
    }

    /**
     * Gives every resource of a changed schema graph a UUID before its version is cut, so that a
     * resource can be addressed by an identifier that survives renaming.
     */
    private void enhanceWithUUIDs(TransactionParticipant participant) {
        if (participant instanceof Graph graph
                && identify.apply(participant).kind() == ParticipantId.Kind.RDF) {
            GraphUtils.enhanceWithUUIDs(graph);
        }
    }

    private void recordVersion(
            TransactionParticipant participant,
            List<ParticipantVersion> versions,
            List<ContextDelta> deltas) {
        if (!(participant instanceof ChangeLogParticipant rewindable)) {
            return;
        }
        var id = identify.apply(participant);
        var lastDelta = participant instanceof DeltaSource r ? r.getLastDelta() : null;
        var additions =
                participant instanceof SnapshotParticipant<?> snapshot
                        ? snapshot.additionsOfLastVersion()
                        : List.<String>of();
        versions.add(
                new ParticipantVersion(
                        id,
                        rewindable,
                        lastDelta != null ? lastDelta.getVersionId() : null,
                        additions));
        if (lastDelta != null) {
            deltas.add(
                    new ContextDelta(
                            id.kind().name().toLowerCase(Locale.ROOT),
                            new WeakReference<>(lastDelta.getAdditions()),
                            new WeakReference<>(lastDelta.getDeletions())));
        }
    }

    private void writeChangeLogEntry() {
        var versions = txnContext.pendingVersions();
        if (versions.isEmpty()) {
            txnContext.clearMessages();
            return;
        }
        var messages = txnContext.messages();
        var message = messages.isEmpty() ? UNNAMED_CHANGE_MESSAGE : String.join("; ", messages);
        changeLog.push(WorkspaceChangeLogEntry.of(message, versions, txnContext.pendingDeltas()));
        txnContext.clearPendingVersions();
        txnContext.clearMessages();
    }

    private void requireWriteTransaction() {
        if (!txnContext.isInTransaction()) {
            throw new GraphNotInATransactionException();
        }
        if (txnContext.transactionMode() == ReadWrite.READ) {
            throw new GraphTransactionException("Cannot write in a read transaction.");
        }
    }
}

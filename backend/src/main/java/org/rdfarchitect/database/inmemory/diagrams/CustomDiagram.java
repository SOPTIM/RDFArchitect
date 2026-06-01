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

package org.rdfarchitect.database.inmemory.diagrams;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import lombok.Setter;

import org.rdfarchitect.rdf.graph.DeltaCompressible;
import org.rdfarchitect.rdf.graph.wrapper.Rewindable;
import org.rdfarchitect.rdf.graph.wrapper.TransactionParticipant;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

/**
 * A custom diagram that participates in the transactional undo/redo framework.
 *
 * <p>State is managed via snapshots: on each {@link #commit()}, the current state is deep-copied
 * and pushed onto the undo stack. {@link #undo()} and {@link #redo()} restore previous snapshots.
 *
 * <p>Before a write transaction begins, the coordinator must call {@link #beginTransaction()} so
 * that {@link #abort()} can restore the pre-transaction state and {@link #hasChanges()} can detect
 * modifications. The method is package-private so that only the coordinator ({@link
 * org.rdfarchitect.database.inmemory.GraphWithContextTransactional}) in the same package can invoke
 * it.
 *
 * <p>Like all other transaction participants, this class has no lock of its own. Synchronisation is
 * the responsibility of the owning coordinator.
 */
public class CustomDiagram implements TransactionParticipant, Rewindable {

    @Getter private final UUID diagramId;
    @Getter @Setter private String name;
    private List<ClassInDiagram> classes;

    private final Deque<DiagramSnapshot> undoStack = new ArrayDeque<>();
    private final Deque<DiagramSnapshot> redoStack = new ArrayDeque<>();

    /**
     * Snapshot of the state at the start of the current transaction. {@code null} outside
     * transactions.
     */
    private DiagramSnapshot preTransactionSnapshot;

    private record DiagramSnapshot(UUID versionId, String name, List<ClassInDiagram> classes) {

        static DiagramSnapshot of(CustomDiagram diagram) {
            return new DiagramSnapshot(
                    UUID.randomUUID(), diagram.name, deepCopyClasses(diagram.classes));
        }

        void applyTo(CustomDiagram diagram) {
            diagram.name = name;
            diagram.classes = deepCopyClasses(classes);
        }

        private static List<ClassInDiagram> deepCopyClasses(List<ClassInDiagram> source) {
            if (source == null) {
                return Collections.emptyList();
            }
            var copy = new ArrayList<ClassInDiagram>(source.size());
            for (var c : source) {
                copy.add(new ClassInDiagram(c.getUuid(), c.getGraphUri()));
            }
            return copy;
        }
    }

    /**
     * Jackson deserialization constructor. The {@code diagramId} field is {@code final} and must be
     * supplied via {@code @JsonCreator} since a no-args constructor cannot initialise it.
     */
    @JsonCreator
    public CustomDiagram(
            @JsonProperty("diagramId") UUID diagramId,
            @JsonProperty("name") String name,
            @JsonProperty("classes") List<ClassInDiagram> classes) {
        this.diagramId = diagramId;
        this.name = name;
        setClasses(classes);
    }

    public CustomDiagram(UUID diagramId) {
        this.diagramId = diagramId;
    }

    // -------------------------------------------------------------------------
    // Accessors for classes (defensive copying)
    // -------------------------------------------------------------------------

    /**
     * Returns a deep copy of the classes list so that callers cannot mutate internal state.
     *
     * @return a deep copy of the diagram's class list, never {@code null}
     */
    public List<ClassInDiagram> getClasses() {
        return DiagramSnapshot.deepCopyClasses(classes);
    }

    /**
     * Stores a deep copy of the provided list, preventing external aliasing after assignment.
     *
     * @param classes the new list of classes; {@code null} is treated as an empty list
     */
    public void setClasses(List<ClassInDiagram> classes) {
        this.classes = DiagramSnapshot.deepCopyClasses(classes);
    }

    // -------------------------------------------------------------------------
    // TransactionParticipant
    // -------------------------------------------------------------------------

    /**
     * Captures a snapshot of the current state at the start of a write transaction. Must be called
     * by the coordinator before any modifications are made, so that {@link #abort()} can restore
     * the original state and {@link #hasChanges()} can detect modifications.
     *
     * <p><strong>Internal API:</strong> only {@link
     * org.rdfarchitect.database.inmemory.GraphWithContextTransactional} should call this method.
     */
    public void beginTransaction() {
        preTransactionSnapshot = DiagramSnapshot.of(this);
    }

    /**
     * Pushes the current state onto the undo stack and clears the redo stack. The pre-transaction
     * snapshot is reset so that the next transaction starts clean.
     */
    @Override
    public void commit() {
        undoStack.push(DiagramSnapshot.of(this));
        redoStack.clear();
        preTransactionSnapshot = null;
    }

    /**
     * Restores the diagram to its pre-transaction state, discarding any modifications made during
     * the current transaction.
     */
    @Override
    public void abort() {
        if (preTransactionSnapshot != null) {
            preTransactionSnapshot.applyTo(this);
            preTransactionSnapshot = null;
        }
    }

    /**
     * Returns {@code true} if the diagram has been modified since {@link #beginTransaction()} was
     * called. Returns {@code false} if no transaction is active.
     */
    @Override
    public boolean hasChanges() {
        if (preTransactionSnapshot == null) {
            return false;
        }
        return !java.util.Objects.equals(preTransactionSnapshot.name, name)
                || !java.util.Objects.equals(preTransactionSnapshot.classes, classes);
    }

    // -------------------------------------------------------------------------
    // Rewindable
    // -------------------------------------------------------------------------

    @Override
    public void undo() {
        if (!canUndo()) {
            return;
        }
        redoStack.push(undoStack.pop());
        var current = undoStack.peek();
        assert current != null : "canUndo() guarantees at least one entry remains";
        current.applyTo(this);
    }

    @Override
    public void redo() {
        if (!canRedo()) {
            return;
        }
        var snapshot = redoStack.pop();
        undoStack.push(snapshot);
        snapshot.applyTo(this);
    }

    @Override
    public boolean canUndo() {
        return undoStack.size() > 1;
    }

    @Override
    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    @Override
    public void restore(UUID versionId) {
        while (undoStack.size() > 1) {
            var top = undoStack.peek();
            assert top != null;
            if (top.versionId().equals(versionId)) {
                break;
            }
            redoStack.push(undoStack.pop());
        }
        var current = undoStack.peek();
        if (current != null) {
            current.applyTo(this);
        }
    }

    /**
     * Not applicable for snapshot-based rewindables. Returns {@code null}.
     *
     * @return {@code null} always
     */
    @Override
    public DeltaCompressible getLastDelta() {
        return null;
    }
}

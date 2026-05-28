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
 * <p>Like all other transaction participants, this class has no lock of its own. Synchronisation is
 * the responsibility of the owning coordinator ({@link
 * org.rdfarchitect.database.inmemory.GraphWithContextTransactional}).
 */
public class CustomDiagram implements TransactionParticipant, Rewindable {

    @Getter private final UUID diagramId;
    @Getter @Setter private String name;
    @Getter @Setter private List<ClassInDiagram> classes;

    private final Deque<DiagramSnapshot> undoStack = new ArrayDeque<>();
    private final Deque<DiagramSnapshot> redoStack = new ArrayDeque<>();

    /** Snapshot of the mutable diagram state taken before a transaction begins. */
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

    public CustomDiagram(UUID diagramId) {
        this.diagramId = diagramId;
    }

    public CustomDiagram(UUID diagramId, String name, List<ClassInDiagram> classes) {
        this.diagramId = diagramId;
        this.name = name;
        this.classes = classes;
    }

    // -------------------------------------------------------------------------
    // TransactionParticipant
    // -------------------------------------------------------------------------

    /**
     * Captures a snapshot of the current state before the transaction began, then records the
     * current (possibly modified) state as a new undo entry. Clears the redo stack since a new
     * commit invalidates any undone history.
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

    @Override
    public boolean hasChanges() {
        if (preTransactionSnapshot == null) {
            return false;
        }
        return !java.util.Objects.equals(preTransactionSnapshot.name, name)
                || !java.util.Objects.equals(preTransactionSnapshot.classes, classes);
    }

    /**
     * Called by the coordinator when a transaction begins so that {@link #abort()} can restore the
     * original state and {@link #hasChanges()} can detect modifications.
     */
    public void beginTransaction() {
        preTransactionSnapshot = DiagramSnapshot.of(this);
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

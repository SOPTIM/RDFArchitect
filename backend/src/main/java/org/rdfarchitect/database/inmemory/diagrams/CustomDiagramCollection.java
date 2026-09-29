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

import org.apache.jena.query.ReadWrite;
import org.rdfarchitect.rdf.graph.wrapper.TransactionParticipant;
import org.rdfarchitect.rdf.graph.wrapper.WorkspaceTransactionContext;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The custom diagrams of a graph or of a workspace.
 *
 * <p>The collection is the transaction participant, not the individual diagram, so that creating,
 * deleting and renaming a diagram are the same case as changing its contents: the state of the
 * collection changed.
 *
 * <p>State is kept as a snapshot rather than a delta. A diagram holds a handful of class
 * references, so copying it is cheap, and unlike the graphs there is no chain whose links are the
 * current state.
 */
public class CustomDiagramCollection implements TransactionParticipant {

    private final WorkspaceTransactionContext txnContext;

    private final ConcurrentHashMap<UUID, CustomDiagram> diagrams = new ConcurrentHashMap<>();

    /** State at the first write of the running transaction, or {@code null} outside one. */
    private Map<UUID, CustomDiagram> preTransactionState;

    public CustomDiagramCollection(WorkspaceTransactionContext txnContext) {
        this.txnContext = txnContext;
    }

    /**
     * Returns the diagrams for use inside the running transaction. In a write transaction this
     * enrols the collection, because the caller may modify what it hands back.
     *
     * @return the diagrams by id
     */
    public Map<UUID, CustomDiagram> get() {
        if (txnContext.isInTransaction() && txnContext.transactionMode() != ReadWrite.READ) {
            if (preTransactionState == null) {
                preTransactionState = snapshot();
            }
            txnContext.enroll(this);
        }
        return diagrams;
    }

    @Override
    public void commit() {
        preTransactionState = null;
    }

    @Override
    public void abort() {
        if (preTransactionState == null) {
            return;
        }
        diagrams.clear();
        diagrams.putAll(preTransactionState);
        preTransactionState = null;
    }

    @Override
    public boolean hasChanges() {
        return preTransactionState != null && !preTransactionState.equals(snapshot());
    }

    private Map<UUID, CustomDiagram> snapshot() {
        var copy = new HashMap<UUID, CustomDiagram>(diagrams.size());
        diagrams.forEach((id, diagram) -> copy.put(id, diagram.copy()));
        return copy;
    }
}

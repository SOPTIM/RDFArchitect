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

import org.rdfarchitect.rdf.graph.wrapper.SnapshotParticipant;
import org.rdfarchitect.rdf.graph.wrapper.WorkspaceTransactionContext;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The custom diagrams of a graph or of a workspace.
 *
 * <p>The collection is the transaction participant, not the individual diagram, so that creating,
 * deleting and renaming a diagram are the same case as changing its contents: the state of the
 * collection changed.
 */
public class CustomDiagramCollection extends SnapshotParticipant<Map<UUID, CustomDiagram>> {

    private final ConcurrentHashMap<UUID, CustomDiagram> diagrams = new ConcurrentHashMap<>();

    public CustomDiagramCollection(WorkspaceTransactionContext txnContext) {
        super(txnContext);
    }

    /**
     * Returns the diagrams for use inside the running transaction. In a write transaction this
     * enrols the collection, because the caller may change what it hands back.
     *
     * @return the diagrams by id
     */
    public Map<UUID, CustomDiagram> get() {
        beginChange();
        return diagrams;
    }

    @Override
    protected Map<UUID, CustomDiagram> snapshot() {
        var copy = new HashMap<UUID, CustomDiagram>(diagrams.size());
        diagrams.forEach((id, diagram) -> copy.put(id, diagram.copy()));
        return copy;
    }

    @Override
    protected void restore(Map<UUID, CustomDiagram> state) {
        diagrams.clear();
        state.forEach((id, diagram) -> diagrams.put(id, diagram.copy()));
    }

    @Override
    protected List<String> describeAdditions(
            Map<UUID, CustomDiagram> before, Map<UUID, CustomDiagram> after) {
        return after.entrySet().stream()
                .filter(entry -> !before.containsKey(entry.getKey()))
                .map(entry -> nameOf(entry.getValue(), entry.getKey()))
                .toList();
    }

    /** A diagram is created before it is named, so the id has to stand in until then. */
    private static String nameOf(CustomDiagram diagram, UUID id) {
        var name = diagram.getName();
        return name == null || name.isBlank() ? id.toString() : name;
    }
}

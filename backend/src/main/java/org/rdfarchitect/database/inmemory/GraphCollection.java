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

import org.rdfarchitect.rdf.graph.wrapper.SnapshotParticipant;
import org.rdfarchitect.rdf.graph.wrapper.WorkspaceTransactionContext;

import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which graphs a workspace contains, under which URI.
 *
 * <p>Making the set of graphs a participant is what puts creating, deleting and renaming a graph in
 * the same undo stack as its contents. A deleted graph is not torn down, only unlinked: the
 * snapshot that undo would return to still holds it, with its own history intact. It becomes
 * unreachable once the deletion falls out of the changelog.
 */
class GraphCollection extends SnapshotParticipant<Map<String, GraphWithContext>> {

    private final ConcurrentHashMap<String, GraphWithContext> graphs = new ConcurrentHashMap<>();

    GraphCollection(WorkspaceTransactionContext txnContext) {
        super(txnContext);
    }

    GraphWithContext get(String graphUri) {
        return graphs.get(graphUri);
    }

    boolean contains(String graphUri) {
        return graphs.containsKey(graphUri);
    }

    List<String> uris() {
        return graphs.keySet().stream().toList();
    }

    /** Returns the graphs by URI, for reading only. */
    Map<String, GraphWithContext> view() {
        return Collections.unmodifiableMap(graphs);
    }

    void put(String graphUri, GraphWithContext graph) {
        beginChange();
        graphs.put(graphUri, graph);
    }

    void remove(String graphUri) {
        beginChange();
        graphs.remove(graphUri);
    }

    void rename(String oldGraphUri, String newGraphUri) {
        beginChange();
        graphs.put(newGraphUri, graphs.remove(oldGraphUri));
    }

    @Override
    protected Map<String, GraphWithContext> snapshot() {
        return new HashMap<>(graphs);
    }

    @Override
    protected void restore(Map<String, GraphWithContext> state) {
        graphs.clear();
        graphs.putAll(state);
    }

    /**
     * A rename moves the same graph to another key, so a new key alone does not mean a new graph.
     * Only a graph that was not in the workspace before would disappear when the commit is undone.
     */
    @Override
    protected List<String> describeAdditions(
            Map<String, GraphWithContext> before, Map<String, GraphWithContext> after) {
        var existing = Collections.newSetFromMap(new IdentityHashMap<GraphWithContext, Boolean>());
        existing.addAll(before.values());
        return after.entrySet().stream()
                .filter(entry -> !existing.contains(entry.getValue()))
                .map(Map.Entry::getKey)
                .toList();
    }
}

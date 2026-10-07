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

import org.rdfarchitect.database.CrossProfileColors;
import org.rdfarchitect.models.changelog.ValueChange;
import org.rdfarchitect.rdf.graph.wrapper.SnapshotParticipant;
import org.rdfarchitect.rdf.graph.wrapper.WorkspaceTransactionContext;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** The colour each graph is drawn in on the cross-profile diagram of a workspace. */
public class CrossProfileDiagramInfo extends SnapshotParticipant<Map<String, String>>
        implements CrossProfileColors {

    @Getter private final UUID crossProfileDiagramUUID = UUID.randomUUID();

    private final ConcurrentMap<String, String> colors = new ConcurrentHashMap<>();

    public CrossProfileDiagramInfo(WorkspaceTransactionContext txnContext) {
        super(txnContext);
    }

    /**
     * Returns the colour of a graph.
     *
     * @param graphUri the graph URI
     * @return the colour, or {@code null} if the graph has none
     */
    @Override
    public String getColor(String graphUri) {
        return colors.getOrDefault(graphUri, null);
    }

    /**
     * Sets the colour of a graph.
     *
     * @param graphUri the graph URI
     * @param color the colour to use
     */
    @Override
    public void setColor(String graphUri, String color) {
        beginChange();
        colors.put(graphUri, color);
    }

    /**
     * Moves the colour of a graph to its new URI.
     *
     * @param oldGraphUri the URI the graph had
     * @param newGraphUri the URI the graph now has
     */
    public void renameGraph(String oldGraphUri, String newGraphUri) {
        beginChange();
        var color = colors.remove(oldGraphUri);
        if (color != null) {
            colors.put(newGraphUri, color);
        }
    }

    @Override
    protected Map<String, String> snapshot() {
        return new HashMap<>(colors);
    }

    @Override
    protected void restore(Map<String, String> state) {
        colors.clear();
        colors.putAll(state);
    }

    @Override
    protected List<ValueChange> describeValueChanges(
            Map<String, String> before, Map<String, String> after) {
        return ValueChange.between(before, after);
    }
}

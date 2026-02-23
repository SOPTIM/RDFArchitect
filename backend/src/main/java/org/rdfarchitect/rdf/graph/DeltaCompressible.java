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

package org.rdfarchitect.rdf.graph;

import lombok.Getter;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.GraphMemFactory;
import org.apache.jena.graph.GraphUtil;
import org.apache.jena.graph.Triple;
import org.apache.jena.graph.compose.CompositionBase;
import org.apache.jena.graph.impl.SimpleEventManager;
import org.apache.jena.sparql.graph.GraphFactory;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Graph operation for wrapping a base graph and leaving it unchanged while
 * recording all the attempted updates for later access.
 * <p>
 * The behavior of this class is not well-defined if triples are added to or
 * removed from the base graph, the additions graph, or the deletions graph
 * while this graph is in use.
 */

public class DeltaCompressible extends CompositionBase implements Graph {

    /**
     * -- GETTER --
     * Answer the base graph.
     */
    @Getter
    private Graph base;
    /**
     * -- GETTER --
     * Answer the graph of all triples added.
     */
    @Getter
    private Graph additions;
    /**
     * -- GETTER --
     * Answer the graph of all triples removed.
     */
    @Getter
    private Graph deletions;
    @Getter
    private final UUID versionId = UUID.randomUUID();

    public DeltaCompressible(@NotNull Graph base) {
        super();
        this.base = base;
        this.additions = GraphMemFactory.createDefaultGraph();
        this.deletions = GraphMemFactory.createDefaultGraph();
    }

    public void compress() {
        var newBase = GraphFactory.createDefaultGraph();
        GraphUtil.add(newBase, this.find());
        base = newBase;
        additions = GraphMemFactory.createDefaultGraph();
        deletions = GraphMemFactory.createDefaultGraph();
    }

    /**
     * Add the triple to the graph, ie add it to the additions, remove it from
     * the removals.
     */
    @Override
    public void performAdd(Triple t) {
        if (!base.contains(t)) {
            additions.add(t);
        }
        deletions.delete(t);
    }

    /**
     * Remove the triple, ie, remove it from the adds, add it to the removals.
     */
    @Override
    public void performDelete(Triple t) {
        additions.delete(t);
        if (base.contains(t)) {
            deletions.add(t);
        }
    }

    /**
     * Find all the base triples matching tm, exclude the ones that are deleted,
     * add the ones that have been added.
     */
    @Override
    protected ExtendedIterator<Triple> graphBaseFind(Triple t) {
        ExtendedIterator<Triple> iterator = base.find(t).filterDrop(deletions::contains).andThen(additions.find(t));
        return SimpleEventManager.notifyingRemove(this, iterator);
    }

    @Override
    public void close() {
        super.close();
        if (!base.isClosed()) {
            base.close();
        }
        additions.close();
        deletions.close();
    }

    @Override
    public int graphBaseSize() {
        return base.size() + additions.size() - deletions.size();
    }
}

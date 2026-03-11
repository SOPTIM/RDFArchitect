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

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.jena.graph.Graph;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.TxnType;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.shared.impl.PrefixMappingImpl;
import org.apache.jena.sparql.core.Transactional;
import org.apache.jena.sparql.graph.GraphFactory;
import org.apache.jena.sparql.graph.PrefixMappingReadOnly;
import org.rdfarchitect.config.GraphCompressionConfig;
import org.rdfarchitect.database.inmemory.diagrams.CustomDiagram;
import org.rdfarchitect.rdf.RDFUtils;
import org.rdfarchitect.rdf.graph.wrapper.DiagramLayout;
import org.rdfarchitect.rdf.graph.wrapper.GraphRewindableWithUUIDs;

import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * This class is a collection of {@link GraphWithContext graphs with context}
 * It also contains a universal {@link PrefixMapping}.
 * The goal is to mimic the structure of a {@link org.apache.jena.sparql.core.DatasetGraph DatasetGraph}, but only access each Graph individually.
 */
@NoArgsConstructor
public class GraphWithContextCollection {

    private static final String DEFAULT_GRAPH_NAME = "default";

    @Setter
    @Getter
    private boolean isReadOnly = true;

    private final int maxVersions = GraphCompressionConfig.getMaxVersions();
    private final int compressCount = GraphCompressionConfig.getCompressCount();

    //holds graphs
    private final ConcurrentMap<String, GraphWithContext> graphs = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, CustomDiagram> customDiagrams = new ConcurrentHashMap<>();

    //lock to prohibit dirty reads/writes
    private final ReentrantLock lock = new ReentrantLock();

    //universal prefix map for all graphs
    private final PrefixMapping prefixes = new PrefixMappingImpl();

    public GraphWithContextCollection(Dataset dataset) {
        lock.lock();
        try {
            this.prefixes.setNsPrefixes(dataset.getPrefixMapping());
            if (!dataset.getDefaultModel().isEmpty()) {
                var rdfGraph = new GraphRewindableWithUUIDs(dataset.getDefaultModel().getGraph(), maxVersions, compressCount);
                var graph = new GraphWithContext(rdfGraph);
                graph.setDiagramLayout(new DiagramLayout());
                graphs.put(DEFAULT_GRAPH_NAME, graph);
            }
            for (Iterator<Resource> it = dataset.listModelNames(); it.hasNext(); ) {
                var graphURI = it.next().getURI();
                var rdfGraph = new GraphRewindableWithUUIDs(dataset.getNamedModel(graphURI).getGraph(), maxVersions, compressCount);
                var graph = new GraphWithContext(rdfGraph);
                graph.setDiagramLayout(new DiagramLayout());
                graphs.put(graphURI, graph);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Begin a transaction on a {@link GraphRewindableWithUUIDs}.
     *
     * @param graphUri The GraphUri to identify the Graph.
     * @param txnType  The transactionType
     *
     * @return The {@link GraphRewindableWithUUIDs} the {@link Transactional Transaction} is performed on.
     *
     * @throws IllegalArgumentException if the graphUri is not a valid URI.
     */
    public GraphRewindableWithUUIDs begin(String graphUri, TxnType txnType) {
        lock.lock();
        try {
            graphUri = prefixes.expandPrefix(graphUri);
            createGraphIfNonExistent(graphUri);
            var rdfGraph = graphs.get(graphUri).getRdfGraph();
            rdfGraph.begin(txnType);
            return rdfGraph;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Gets a {@link GraphWithContext} from the collection.
     *
     * @param graphUri The GraphUri to identify the GraphWithContext.
     *
     * @return The {@link GraphWithContext}.
     */
    public GraphWithContext getGraphWithContext(String graphUri) {
        lock.lock();
        try {
            graphUri = prefixes.expandPrefix(graphUri);
            createGraphIfNonExistent(graphUri);
            return graphs.get(graphUri);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Creates a new {@link GraphRewindableWithUUIDs} in the collection. If the {@link GraphRewindableWithUUIDs} already exists nothing happens.
     *
     * @param graphUri The GraphUri to identify the Graph.
     */
    private void createGraphIfNonExistent(String graphUri) {
        graphUri = prefixes.expandPrefix(graphUri);
        assertValidGraphName(graphUri);
        if (!graphs.containsKey(graphUri)) {
            if (!graphUri.equals(DEFAULT_GRAPH_NAME)) {
                throw new IllegalArgumentException("Graph URI " + graphUri + " does not exist.");
            }
            var rdfGraph = new GraphRewindableWithUUIDs(GraphFactory.createDefaultGraph(), maxVersions, compressCount);
            var graph = new GraphWithContext(rdfGraph);
            graphs.put(graphUri, graph);
        }
    }

    /**
     * Creates a new {@link GraphRewindableWithUUIDs} and immediately starts a {@link TxnType TxnType.WRITE} transaction on it.
     *
     * @param graphUri The GraphUri to identify the Graph.
     * @param newGraph The new Graph.
     *
     * @throws IllegalArgumentException if the graphUri is not a valid URI.
     */
    public void create(String graphUri, Graph newGraph) {
        lock.lock();
        try {
            graphUri = prefixes.expandPrefix(graphUri);
            assertValidGraphName(graphUri);
            var rdfGraph = new GraphRewindableWithUUIDs(newGraph, maxVersions, compressCount);
            var graph = new GraphWithContext(rdfGraph);
            graph.setDiagramLayout(new DiagramLayout());
            graphs.put(graphUri, graph);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Closes all {@link GraphRewindableWithUUIDs GraphRewindables} and releases all resources.
     */
    public void clear() {
        lock.lock();
        try {
            graphs.values().forEach(graph -> {
                var rdfGraph = graph.getRdfGraph();
                rdfGraph.begin(TxnType.WRITE);
                rdfGraph.close();
                rdfGraph.end();
            });
            graphs.clear();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Removes a {@link GraphRewindableWithUUIDs}.
     *
     * @param graphUri The GraphUri to identify the Graph.
     *
     * @throws IllegalArgumentException if the graphUri is not a valid URI.
     */
    public void remove(String graphUri) {
        lock.lock();
        try {
            graphUri = prefixes.expandPrefix(graphUri);
            assertValidGraphName(graphUri);
            if (!containsGraph(graphUri)) {
                return;
            }
            var rdfGraph = graphs.get(graphUri).getRdfGraph();
            rdfGraph.begin(TxnType.WRITE);
            rdfGraph.close();
            rdfGraph.end();
            graphs.remove(graphUri);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Checks Whether {@link GraphWithContextCollection this} contains a certain {@link GraphRewindableWithUUIDs}
     *
     * @param graphUri The GraphUri to identify the Graph.
     *
     * @return True if {@link GraphWithContextCollection this} contains the {@link GraphRewindableWithUUIDs}, otherwise false
     *
     * @throws IllegalArgumentException if the graphUri is not a valid URI.
     */
    public boolean containsGraph(String graphUri) {
        lock.lock();
        try {
            graphUri = prefixes.expandPrefix(graphUri);
            assertValidGraphName(graphUri);
            return graphs.containsKey(graphUri);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Lists all names of the {@link GraphRewindableWithUUIDs}.
     *
     * @return List with all graph uris
     */
    public List<String> listGraphUris() {
        lock.lock();
        try {
            return graphs.keySet().stream().toList();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Lists the stored Prefixes of {@link GraphWithContextCollection this}
     *
     * @return {@link PrefixMappingReadOnly}
     */
    public PrefixMappingReadOnly getPrefixMapping() {
        lock.lock();
        try {
            return new PrefixMappingReadOnly(prefixes);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Replace the entire {@link PrefixMapping} of {@link GraphWithContextCollection this}. The internal {@link PrefixMapping} does not reference the given one to prevent
     * external modification.
     *
     * @param newPrefixMapping the new {@link PrefixMapping}.
     */
    public void setPrefixMapping(PrefixMapping newPrefixMapping) {
        lock.lock();
        try {
            this.prefixes.clearNsPrefixMap();
            this.prefixes.setNsPrefixes(newPrefixMapping);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Undoes the last commit on a {@link GraphRewindableWithUUIDs}.
     *
     * @param graphUri The GraphUri to identify the Graph.
     *
     * @throws IllegalArgumentException                                      if the graphUri is not a valid URI or if the graph doesn't exist.
     * @throws org.rdfarchitect.exception.graph.GraphVersionControlException if the graph is already at the first version.
     */
    public void undo(String graphUri) {
        lock.lock();
        try {
            graphUri = prefixes.expandPrefix(graphUri);
            assertValidGraphName(graphUri);
            assertThatGraphExists(graphUri);
            graphs.get(graphUri).getRdfGraph().undo();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Redoes the last undone commit on a {@link GraphRewindableWithUUIDs}.
     *
     * @param graphUri The GraphUri to identify the Graph.
     *
     * @throws IllegalArgumentException                                      if the graphUri is not a valid URI or if the graph doesn't exist.
     * @throws org.rdfarchitect.exception.graph.GraphVersionControlException if the graph is already at the last version.
     */
    public void redo(String graphUri) {
        lock.lock();
        try {
            graphUri = prefixes.expandPrefix(graphUri);
            assertValidGraphName(graphUri);
            assertThatGraphExists(graphUri);
            graphs.get(graphUri).getRdfGraph().redo();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Checks if an undo is possible on a {@link GraphRewindableWithUUIDs}.
     *
     * @param graphUri The GraphUri to identify the Graph.
     *
     * @return True if an undo is possible, otherwise false
     */
    public boolean canUndo(String graphUri) {
        lock.lock();
        try {
            graphUri = prefixes.expandPrefix(graphUri);
            assertValidGraphName(graphUri);
            assertThatGraphExists(graphUri);
            return graphs.get(graphUri).getRdfGraph().canUndo();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Checks if a redo is possible on a {@link GraphRewindableWithUUIDs}.
     *
     * @param graphUri The GraphUri to identify the Graph.
     *
     * @return True if a redo is possible, otherwise false
     */
    public boolean canRedo(String graphUri) {
        lock.lock();
        try {
            graphUri = prefixes.expandPrefix(graphUri);
            assertValidGraphName(graphUri);
            assertThatGraphExists(graphUri);
            return graphs.get(graphUri).getRdfGraph().canRedo();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Restores a graph to a specific version identified by its UUID.
     *
     * @param graphUri  The GraphUri to identify the Graph.
     * @param versionId The UUID of the version to restore.
     *
     * @throws IllegalArgumentException if the graphUri is not a valid URI or if the graph doesn't exist.
     */
    public void restore(String graphUri, UUID versionId) {
        lock.lock();
        try {
            graphUri = prefixes.expandPrefix(graphUri);
            assertValidGraphName(graphUri);
            assertThatGraphExists(graphUri);
            graphs.get(graphUri).getRdfGraph().restore(versionId);
        } finally {
            lock.unlock();
        }
    }

    private void assertThatGraphExists(String graphUri) {
        if (!containsGraph(graphUri)) {
            throw new IllegalArgumentException("Graph URI " + graphUri + " does not exist");
        }
    }

    private void assertValidGraphName(String graphUri) {
        if (!graphUri.equals(DEFAULT_GRAPH_NAME) && !RDFUtils.isURL(graphUri)) {
            throw new IllegalArgumentException("Graph Uri " + graphUri + " is not a valid URI");
        }
    }
}

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
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.shared.impl.PrefixMappingImpl;
import org.apache.jena.sparql.graph.GraphFactory;
import org.apache.jena.sparql.graph.PrefixMappingReadOnly;
import org.rdfarchitect.database.inmemory.diagrams.CrossProfileDiagramInfo;
import org.rdfarchitect.database.inmemory.diagrams.CustomDiagram;
import org.rdfarchitect.rdf.RDFUtils;
import org.rdfarchitect.rdf.graph.wrapper.DiagramLayout;

import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Collection of {@link GraphWithContextTransactional} objects with a shared {@link PrefixMapping}.
 * Mimics the structure of a {@link org.apache.jena.sparql.core.DatasetGraph DatasetGraph} but
 * exposes each graph individually.
 */
@NoArgsConstructor
public class GraphWithContextCollection {

    private static final String DEFAULT_GRAPH_NAME = "default";

    @Setter @Getter private volatile boolean isReadOnly = true;

    private final ConcurrentMap<String, GraphWithContextTransactional> graphs =
            new ConcurrentHashMap<>();

    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();

    @Getter
    private final ConcurrentMap<UUID, CustomDiagram> customDiagrams = new ConcurrentHashMap<>();

    @Getter private final DiagramLayout diagramLayout = new DiagramLayout();

    @Getter
    private final CrossProfileDiagramInfo crossProfileDiagramInfo = new CrossProfileDiagramInfo();

    // universal prefix map for all graphs
    private final PrefixMapping prefixes = new PrefixMappingImpl();

    @SuppressWarnings("java:S2093")
    public GraphWithContextCollection(Dataset dataset) {
        rwLock.writeLock().lock();
        try {
            this.prefixes.setNsPrefixes(dataset.getPrefixMapping());
            if (!dataset.getDefaultModel().isEmpty()) {
                graphs.put(
                        DEFAULT_GRAPH_NAME,
                        new GraphWithContextTransactional(dataset.getDefaultModel().getGraph()));
            }
            for (Iterator<Resource> it = dataset.listModelNames(); it.hasNext(); ) {
                var graphURI = it.next().getURI();
                graphs.put(
                        graphURI,
                        new GraphWithContextTransactional(
                                dataset.getNamedModel(graphURI).getGraph()));
            }
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * Returns the {@link GraphWithContextTransactional} identified by {@code graphUri}.
     *
     * @param graphUri the graph URI
     * @return the {@link GraphWithContextTransactional}
     */
    public GraphWithContextTransactional getGraphWithContext(String graphUri) {
        // Try read lock first — sufficient if the graph already exists.
        rwLock.readLock().lock();
        try {
            var expanded = prefixes.expandPrefix(graphUri);
            var existing = graphs.get(expanded);
            if (existing != null) {
                return existing;
            }
        } finally {
            rwLock.readLock().unlock();
        }
        // Graph not found — upgrade to write lock to create the default graph if applicable.
        rwLock.writeLock().lock();
        try {
            graphUri = prefixes.expandPrefix(graphUri);
            createGraphIfNonExistent(graphUri);
            return graphs.get(graphUri);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    private void createGraphIfNonExistent(String graphUri) {
        graphUri = prefixes.expandPrefix(graphUri);
        assertValidGraphName(graphUri);
        if (!graphs.containsKey(graphUri)) {
            if (!graphUri.equals(DEFAULT_GRAPH_NAME)) {
                throw new IllegalArgumentException("Graph URI " + graphUri + " does not exist.");
            }
            graphs.put(
                    DEFAULT_GRAPH_NAME,
                    new GraphWithContextTransactional(GraphFactory.createDefaultGraph()));
        }
    }

    /**
     * Creates a new {@link GraphWithContextTransactional} for {@code newGraph} and registers it
     * under {@code graphUri}.
     *
     * @param graphUri the graph URI
     * @param newGraph initial graph content
     */
    public void create(String graphUri, Graph newGraph) {
        rwLock.writeLock().lock();
        try (var ctx = new GraphWithContextTransactional(newGraph).begin(ReadWrite.WRITE)) {
            graphUri = prefixes.expandPrefix(graphUri);
            assertValidGraphName(graphUri);
            var existing = graphs.put(graphUri, ctx);
            if (existing != null) {
                try (var old = existing.begin(ReadWrite.WRITE)) {
                    old.getRdfGraph().close();
                }
            }
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /** Closes all graphs and clears the collection. */
    public void clear() {
        rwLock.writeLock().lock();
        try {
            graphs.values()
                    .forEach(
                            ctx -> {
                                try {
                                    ctx.begin(ReadWrite.WRITE);
                                    ctx.getRdfGraph().close();
                                } finally {
                                    ctx.end();
                                }
                            });
            graphs.clear();
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * Removes and closes the {@link GraphWithContextTransactional} identified by {@code graphUri}.
     *
     * @param graphUri the graph URI
     */
    @SuppressWarnings("resource")
    public void remove(String graphUri) {
        rwLock.writeLock().lock();
        try {
            graphUri = prefixes.expandPrefix(graphUri);
            assertValidGraphName(graphUri);
            if (!graphs.containsKey(graphUri)) {
                return;
            }
            try (var ctx = graphs.get(graphUri).begin(ReadWrite.WRITE)) {
                ctx.getRdfGraph().close();
                graphs.remove(graphUri);
            }
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * Returns a snapshot of the current graph URIs. The result may be stale by the time the caller
     * inspects it.
     */
    public List<String> listGraphUris() {
        rwLock.readLock().lock();
        try {
            return graphs.keySet().stream().toList();
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public PrefixMappingReadOnly getPrefixMapping() {
        rwLock.readLock().lock();
        try {
            return new PrefixMappingReadOnly(prefixes);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public void setPrefixMapping(PrefixMapping newPrefixMapping) {
        rwLock.writeLock().lock();
        try {
            this.prefixes.clearNsPrefixMap();
            this.prefixes.setNsPrefixes(newPrefixMapping);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    private void assertValidGraphName(String graphUri) {
        if (!graphUri.equals(DEFAULT_GRAPH_NAME) && !RDFUtils.isURL(graphUri)) {
            throw new IllegalArgumentException("Graph Uri " + graphUri + " is not a valid URI");
        }
    }
}

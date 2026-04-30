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

import static org.rdfarchitect.database.snapshots.SnapshotUtils.SNAPSHOT_PREFIX;
import static org.rdfarchitect.database.snapshots.SnapshotUtils.findSnapshotName;

import org.apache.jena.arq.querybuilder.SelectBuilder;
import org.apache.jena.graph.Graph;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.TxnType;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.sparql.graph.GraphFactory;
import org.apache.jena.sparql.graph.PrefixMappingReadOnly;
import org.rdfarchitect.database.DatabaseConnection;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.exception.database.DataAccessException;
import org.rdfarchitect.models.cim.queries.select.CIMBaseQueryBuilder;
import org.rdfarchitect.rdf.graph.source.builder.implementations.GraphSourceBuilderImpl;
import org.rdfarchitect.rdf.graph.wrapper.GraphRewindableWithUUIDs;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Class that provides {@link GraphRewindableWithUUIDs GraphRewindables} belonging to a session.
 * Write actions on this class are irreversible. closing the provided {@link
 * GraphRewindableWithUUIDs GraphRewindables} is mandatory.
 */
public class SessionDataStoreImpl implements SessionDataStore {

    private final ConcurrentHashMap<String, GraphWithContextCollection> graphCollections =
            new ConcurrentHashMap<>();

    // lock to prohibit dirty reads/writes
    private final ReentrantLock lock = new ReentrantLock();

    private void createDataset(String datasetName) {
        graphCollections.putIfAbsent(datasetName, new GraphWithContextCollection());
    }

    @Override
    public void deleteDataset(String datasetName) {
        lock.lock();
        try {
            if (!graphCollections.containsKey(datasetName)) {
                return;
            }
            var graphRewindableCollection = graphCollections.get(datasetName);
            for (String graphUri : graphRewindableCollection.listGraphUris()) {
                GraphRewindableWithUUIDs graph =
                        graphRewindableCollection.begin(graphUri, TxnType.WRITE);
                graph.close();
                graph.end();
            }
            graphCollections.remove(datasetName);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<String> listDatasets() {
        lock.lock();
        try {
            return new ArrayList<>(graphCollections.keySet());
        } finally {
            lock.unlock();
        }
    }

    @Override
    public GraphRewindableWithUUIDs begin(GraphIdentifier graphIdentifier, TxnType txnType) {
        final String datasetName = graphIdentifier.datasetName();
        final String graphUri = graphIdentifier.graphUri();
        lock.lock();
        try {
            createDataset(datasetName);
            return graphCollections.get(datasetName).begin(graphUri, txnType);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public GraphWithContext getGraphWithContext(GraphIdentifier graphIdentifier) {
        lock.lock();
        try {
            createDataset(graphIdentifier.datasetName());
            return graphCollections
                    .get(graphIdentifier.datasetName())
                    .getGraphWithContext(graphIdentifier.graphUri());
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void create(GraphIdentifier graphIdentifier, Graph newGraph) {
        lock.lock();
        try {
            createDataset(graphIdentifier.datasetName());
            graphCollections
                    .get(graphIdentifier.datasetName())
                    .create(graphIdentifier.graphUri(), newGraph);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void remove(GraphIdentifier graphIdentifier) {
        final String datasetName = graphIdentifier.datasetName();
        final String graphUri = graphIdentifier.graphUri();
        lock.lock();
        try {
            if (!graphCollections.containsKey(datasetName)) {
                return;
            }
            graphCollections.get(datasetName).remove(graphUri);
            if (graphCollections.get(datasetName).listGraphUris().isEmpty()) {
                graphCollections.remove(datasetName);
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean containsGraph(GraphIdentifier graphIdentifier) {
        final String datasetName = graphIdentifier.datasetName();
        final String graphUri = graphIdentifier.graphUri();
        lock.lock();
        try {
            return graphCollections.containsKey(datasetName)
                    && graphCollections.get(datasetName).containsGraph(graphUri);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<String> listGraphUris(String datasetName) {
        lock.lock();
        try {
            assertThatDatasetExists(datasetName);
            return graphCollections.get(datasetName).listGraphUris();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public PrefixMappingReadOnly getPrefixMapping(String datasetName) {
        lock.lock();
        try {
            assertThatDatasetExists(datasetName);
            return graphCollections.get(datasetName).getPrefixMapping();
        } catch (DataAccessException _) {
            return new PrefixMappingReadOnly(PrefixMapping.Factory.create());
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void setPrefixMapping(String datasetName, PrefixMapping newPrefixes) {
        lock.lock();
        try {
            assertThatDatasetExists(datasetName);
            graphCollections.get(datasetName).setPrefixMapping(newPrefixes);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void writeToDatabase(
            DatabaseConnection databaseConnection, GraphIdentifier graphIdentifier) {
        final String datasetName = graphIdentifier.datasetName();
        final String graphUri = graphIdentifier.graphUri();
        lock.lock();
        GraphRewindableWithUUIDs graph = null;
        try {
            assertThatGraphExists(graphIdentifier);
            graph = begin(graphIdentifier, TxnType.READ);
            var graphSource =
                    new GraphSourceBuilderImpl().setGraph(graph).setGraphName(graphUri).build();
            databaseConnection.insertGraph(graphSource, datasetName);
        } finally {
            if (graph != null) {
                graph.end();
            }
            lock.unlock();
        }
    }

    private void clearGraphCollections() {
        lock.lock();
        try {
            graphCollections.values().forEach(GraphWithContextCollection::clear);
            graphCollections.clear();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void fetchFromDatabase(DatabaseConnection databaseConnection) {
        lock.lock();
        try {
            clearGraphCollections();
            var datasetNames = databaseConnection.listDatasets();
            for (var datasetName : datasetNames) {
                if (!datasetName.startsWith(SNAPSHOT_PREFIX)) {
                    var dataset = fetchDataset(databaseConnection, datasetName);
                    graphCollections.put(datasetName, new GraphWithContextCollection(dataset));
                }
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void fetchSnapshot(DatabaseConnection databaseConnection, String base64Token) {
        lock.lock();
        try {
            var matchingDataset = findSnapshotName(databaseConnection.listDatasets(), base64Token);
            if (matchingDataset != null) {
                var dataset = fetchDataset(databaseConnection, matchingDataset);
                graphCollections.put(matchingDataset, new GraphWithContextCollection(dataset));
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Fetches a dataset from a {@link DatabaseConnection database}.
     *
     * @param databaseConnection The connection to the external Database.
     * @param datasetName The name of the dataset.
     * @return fetched {@link Graph}
     */
    private Dataset fetchDataset(DatabaseConnection databaseConnection, String datasetName) {
        // build query
        var graphVar = "?graph";
        var graphQuery =
                new SelectBuilder()
                        .addVar(graphVar)
                        .setDistinct(true)
                        .addGraph(graphVar, "?s", "?p", "?o")
                        .build();

        // fetch data
        var queryResultSet = databaseConnection.sendSelect(graphQuery, datasetName).asResultSet();
        var prefixMapping = databaseConnection.getPrefixMapping(datasetName);

        // insert prefixes
        var dataset = DatasetFactory.createGeneral();
        dataset.getPrefixMapping().setNsPrefixes(prefixMapping);

        // insert graphs
        // default
        var graph = fetchGraph(databaseConnection, datasetName, "default");
        dataset.setDefaultModel(ModelFactory.createModelForGraph(graph));
        // named
        while (queryResultSet.hasNext()) {
            var graphURI = queryResultSet.next().get(graphVar).asNode();
            graph = fetchGraph(databaseConnection, datasetName, graphURI.getURI());
            dataset.addNamedModel(graphURI.getURI(), ModelFactory.createModelForGraph(graph));
        }
        return dataset;
    }

    /**
     * Fetches a single graph from a {@link DatabaseConnection database}.
     *
     * @param databaseConnection The connection to the external Database.
     * @param datasetName The name of the dataset.
     * @param graphUri The graphUri.
     * @return fetched {@link Graph}
     */
    private Graph fetchGraph(
            DatabaseConnection databaseConnection, String datasetName, String graphUri) {
        var query =
                new CIMBaseQueryBuilder()
                        .setGraph(graphUri)
                        .build()
                        .addVar("?sub")
                        .addVar("?pre")
                        .addVar("?obj")
                        .addWhere("?sub", "?pre", "?obj")
                        .build();
        var queryRes = databaseConnection.sendSelect(query, datasetName).asResultSet();
        var resGraph = GraphFactory.createDefaultGraph();
        while (queryRes.hasNext()) {
            var triple = queryRes.next();
            var sub = triple.get("?sub").asNode();
            var pre = triple.get("?pre").asNode();
            var obj = triple.get("?obj").asNode();
            resGraph.add(sub, pre, obj);
        }
        return resGraph;
    }

    @Override
    public void undo(GraphIdentifier graphIdentifier) {
        final String datasetName = graphIdentifier.datasetName();
        final String graphUri = graphIdentifier.graphUri();
        lock.lock();
        try {
            assertThatDatasetExists(datasetName);
            graphCollections.get(datasetName).undo(graphUri);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void redo(GraphIdentifier graphIdentifier) {
        final String datasetName = graphIdentifier.datasetName();
        final String graphUri = graphIdentifier.graphUri();
        lock.lock();
        try {
            assertThatDatasetExists(datasetName);
            graphCollections.get(datasetName).redo(graphUri);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean canUndo(GraphIdentifier graphIdentifier) {
        final String datasetName = graphIdentifier.datasetName();
        final String graphUri = graphIdentifier.graphUri();
        lock.lock();
        try {
            assertThatDatasetExists(datasetName);
            return graphCollections.get(datasetName).canUndo(graphUri);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean canRedo(GraphIdentifier graphIdentifier) {
        final String datasetName = graphIdentifier.datasetName();
        final String graphUri = graphIdentifier.graphUri();
        lock.lock();
        try {
            assertThatDatasetExists(datasetName);
            return graphCollections.get(datasetName).canRedo(graphUri);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void restore(GraphIdentifier graphIdentifier, UUID versionId) {
        final String datasetName = graphIdentifier.datasetName();
        final String graphUri = graphIdentifier.graphUri();
        lock.lock();
        try {
            assertThatGraphExists(graphIdentifier);
            graphCollections.get(datasetName).restore(graphUri, versionId);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean isReadOnly(String datasetName) {
        lock.lock();
        try {
            assertThatDatasetExists(datasetName);
            return graphCollections.get(datasetName).isReadOnly();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void enableEditing(String datasetName) {
        lock.lock();
        try {
            assertThatDatasetExists(datasetName);
            graphCollections.get(datasetName).setReadOnly(false);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void disableEditing(String datasetName) {
        lock.lock();
        try {
            assertThatDatasetExists(datasetName);
            graphCollections.get(datasetName).setReadOnly(true);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Throws an exceptions if the dataset does not exist
     *
     * @param datasetName The name of the dataset.
     * @throws DataAccessException if the dataset does not exist.
     */
    private void assertThatDatasetExists(String datasetName) {
        if (!graphCollections.containsKey(datasetName)) {
            throw new DataAccessException("Dataset " + datasetName + " does not exist");
        }
    }

    /**
     * Throws an exceptions if the graph or its dataset does not exist
     *
     * @param graphIdentifier The identifier of the graph, which includes the dataset name and the
     *     graph URI..
     * @throws DataAccessException if the dataset or graph does not exist.
     */
    private void assertThatGraphExists(GraphIdentifier graphIdentifier) {
        final String datasetName = graphIdentifier.datasetName();
        final String graphUri = graphIdentifier.graphUri();
        assertThatDatasetExists(datasetName);
        if (!graphCollections.get(datasetName).containsGraph(graphUri)) {
            throw new DataAccessException(
                    "Graph " + graphUri + " does not exist in dataset " + datasetName);
        }
    }
}

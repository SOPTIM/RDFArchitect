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
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.sparql.graph.GraphFactory;
import org.apache.jena.sparql.graph.PrefixMappingReadOnly;
import org.rdfarchitect.database.DatabaseConnection;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.database.WorkspaceTransaction;
import org.rdfarchitect.exception.database.DataAccessException;
import org.rdfarchitect.exception.database.ResourceConflictException;
import org.rdfarchitect.models.changelog.WorkspaceChangeLogEntry;
import org.rdfarchitect.models.cim.queries.select.CIMBaseQueryBuilder;
import org.rdfarchitect.rdf.graph.source.builder.implementations.GraphSourceBuilderImpl;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Holds the workspaces of one session. Contents are reachable only through a {@link
 * WorkspaceTransaction}, which must be closed by the caller.
 */
public class SessionDataStoreImpl implements SessionDataStore {

    private final ConcurrentHashMap<String, Workspace> workspaces = new ConcurrentHashMap<>();

    // lock to prohibit dirty reads/writes
    private final ReentrantLock lock = new ReentrantLock();

    @Override
    public void createDataset(String datasetName) {
        lock.lock();
        try {
            createDatasetIfAbsent(datasetName);
        } finally {
            lock.unlock();
        }
    }

    private void createDatasetIfAbsent(String datasetName) {
        workspaces.putIfAbsent(datasetName, new Workspace(datasetName));
    }

    @Override
    public void deleteDataset(String datasetName) {
        lock.lock();
        try {
            if (!workspaces.containsKey(datasetName)) {
                return;
            }
            workspaces.get(datasetName).clear();
            workspaces.remove(datasetName);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void renameDataset(String oldDatasetName, String newDatasetName) {
        lock.lock();
        try {
            if (oldDatasetName.equals(newDatasetName)) {
                return;
            }
            assertThatDatasetExists(oldDatasetName);
            if (workspaces.containsKey(newDatasetName)) {
                throw new ResourceConflictException(
                        "Dataset " + newDatasetName + " already exists");
            }
            workspaces.put(newDatasetName, workspaces.remove(oldDatasetName));
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void renameGraph(GraphIdentifier graphIdentifier, String newGraphUri) {
        lock.lock();
        try {
            assertThatGraphExists(graphIdentifier);
            workspaces
                    .get(graphIdentifier.datasetName())
                    .rename(graphIdentifier.graphUri(), newGraphUri);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<String> listDatasets() {
        lock.lock();
        try {
            return new ArrayList<>(workspaces.keySet());
        } finally {
            lock.unlock();
        }
    }

    @Override
    public WorkspaceTransaction beginTransaction(String workspaceName, ReadWrite mode) {
        return workspace(workspaceName, true).begin(mode);
    }

    @Override
    public boolean canUndo(String workspaceName) {
        return workspace(workspaceName, false).canUndo();
    }

    @Override
    public boolean canRedo(String workspaceName) {
        return workspace(workspaceName, false).canRedo();
    }

    @Override
    public WorkspaceChangeLogEntry undo(String workspaceName) {
        return workspace(workspaceName, false).undo();
    }

    @Override
    public WorkspaceChangeLogEntry redo(String workspaceName) {
        return workspace(workspaceName, false).redo();
    }

    @Override
    public void restoreToVersion(String workspaceName, UUID versionId) {
        workspace(workspaceName, false).restoreToVersion(versionId);
    }

    @Override
    public List<WorkspaceChangeLogEntry> listChanges(String workspaceName) {
        return workspace(workspaceName, false).getChangeHistory();
    }

    private Workspace workspace(String workspaceName, boolean createIfAbsent) {
        lock.lock();
        try {
            if (createIfAbsent) {
                createDatasetIfAbsent(workspaceName);
            } else {
                assertThatDatasetExists(workspaceName);
            }
            return workspaces.get(workspaceName);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void create(GraphIdentifier graphIdentifier, Graph newGraph) {
        lock.lock();
        try {
            createDatasetIfAbsent(graphIdentifier.datasetName());
            workspaces
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
            if (!workspaces.containsKey(datasetName)) {
                return;
            }
            workspaces.get(datasetName).remove(graphUri);
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
            return workspaces.containsKey(datasetName)
                    && workspaces.get(datasetName).listGraphUris().contains(graphUri);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<String> listGraphUris(String datasetName) {
        lock.lock();
        try {
            assertThatDatasetExists(datasetName);
            return workspaces.get(datasetName).listGraphUris();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public PrefixMappingReadOnly getPrefixMapping(String datasetName) {
        lock.lock();
        try {
            assertThatDatasetExists(datasetName);
            return workspaces.get(datasetName).getPrefixMapping();
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
            workspaces.get(datasetName).setPrefixMapping(newPrefixes);
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
        try {
            assertThatGraphExists(graphIdentifier);
            try (var transaction = beginTransaction(datasetName, ReadWrite.READ)) {
                var graphSource =
                        new GraphSourceBuilderImpl()
                                .setGraph(transaction.graph(graphUri).getRdfGraph())
                                .setGraphName(graphUri)
                                .build();
                databaseConnection.insertGraph(graphSource, datasetName);
            }
        } finally {
            lock.unlock();
        }
    }

    private void clearGraphCollections() {
        lock.lock();
        try {
            workspaces.values().forEach(Workspace::clear);
            workspaces.clear();
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
                    workspaces.put(datasetName, new Workspace(datasetName, dataset));
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
                workspaces.put(matchingDataset, new Workspace(matchingDataset, dataset));
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
    public boolean isReadOnly(String datasetName) {
        lock.lock();
        try {
            assertThatDatasetExists(datasetName);
            return workspaces.get(datasetName).isReadOnly();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void enableEditing(String datasetName) {
        lock.lock();
        try {
            assertThatDatasetExists(datasetName);
            workspaces.get(datasetName).setReadOnly(false);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void disableEditing(String datasetName) {
        lock.lock();
        try {
            assertThatDatasetExists(datasetName);
            workspaces.get(datasetName).setReadOnly(true);
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
        if (!workspaces.containsKey(datasetName)) {
            throw new DataAccessException("Dataset " + datasetName + " does not exist");
        }
    }

    /**
     * Throws an exceptions if the graph or its dataset does not exist
     *
     * @param graphIdentifier The identifier of the graph, which includes the dataset name and the
     *     graph URI.
     * @throws DataAccessException if the dataset or graph does not exist.
     */
    private void assertThatGraphExists(GraphIdentifier graphIdentifier) {
        final String datasetName = graphIdentifier.datasetName();
        final String graphUri = graphIdentifier.graphUri();
        assertThatDatasetExists(datasetName);
        if (!workspaces.get(datasetName).listGraphUris().contains(graphUri)) {
            throw new DataAccessException(
                    "Graph " + graphUri + " does not exist in dataset " + datasetName);
        }
    }
}

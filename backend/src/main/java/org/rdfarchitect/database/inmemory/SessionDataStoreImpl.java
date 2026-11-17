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
import org.rdfarchitect.exception.database.ResourceNotFoundException;
import org.rdfarchitect.models.changelog.HistoryPosition;
import org.rdfarchitect.models.changelog.RevertScope;
import org.rdfarchitect.models.changelog.WorkspaceChangeLogEntry;
import org.rdfarchitect.models.changelog.WorkspaceHistoryStep;
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
    public void createDataset(String workspaceName) {
        createDatasetIfAbsent(workspaceName);
    }

    @Override
    public boolean createDatasetIfAbsent(String workspaceName) {
        lock.lock();
        try {
            return workspaces.putIfAbsent(workspaceName, new Workspace(workspaceName)) == null;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void deleteDataset(String workspaceName) {
        lock.lock();
        try {
            workspaces.remove(workspaceName);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void renameDataset(String oldWorkspaceName, String newWorkspaceName) {
        lock.lock();
        try {
            if (oldWorkspaceName.equals(newWorkspaceName)) {
                return;
            }
            assertThatDatasetExists(oldWorkspaceName);
            if (workspaces.containsKey(newWorkspaceName)) {
                throw new ResourceConflictException(
                        "Dataset " + newWorkspaceName + " already exists");
            }
            workspaces.put(newWorkspaceName, workspaces.remove(oldWorkspaceName));
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
        return workspace(workspaceName).begin(mode);
    }

    @Override
    public boolean canUndo(String workspaceName) {
        return workspace(workspaceName).canUndo();
    }

    @Override
    public WorkspaceChangeLogEntry undoPreview(String workspaceName) {
        return workspace(workspaceName).undoPreview();
    }

    @Override
    public boolean canRedo(String workspaceName) {
        return workspace(workspaceName).canRedo();
    }

    @Override
    public WorkspaceHistoryStep undo(String workspaceName) {
        return workspace(workspaceName).undo();
    }

    @Override
    public WorkspaceHistoryStep undoIfLatest(String workspaceName, UUID changeId) {
        return workspace(workspaceName).undoIfLatest(changeId);
    }

    @Override
    public WorkspaceHistoryStep redo(String workspaceName) {
        return workspace(workspaceName).redo();
    }

    @Override
    public WorkspaceHistoryStep restoreToVersion(
            String workspaceName,
            UUID versionId,
            RevertScope scope,
            HistoryPosition expectedPosition) {
        return workspace(workspaceName).restoreToVersion(versionId, scope, expectedPosition);
    }

    @Override
    public List<WorkspaceChangeLogEntry> listChanges(String workspaceName) {
        return workspace(workspaceName).getChangeHistory();
    }

    private Workspace workspace(String workspaceName) {
        var workspace = findWorkspace(workspaceName);
        if (workspace == null) {
            throw new ResourceNotFoundException("Workspace " + workspaceName + " does not exist");
        }
        return workspace;
    }

    /**
     * Looks a workspace up, or returns {@code null} where the session holds none under that name.
     *
     * <p>The lock guards the set of workspaces, not their contents, and is released before the
     * workspace is touched: a caller inside a transaction already holds that workspace's own lock,
     * so holding both here would invert the order and deadlock.
     */
    private Workspace findWorkspace(String workspaceName) {
        lock.lock();
        try {
            return workspaces.get(workspaceName);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean containsGraph(GraphIdentifier graphIdentifier) {
        var workspace = findWorkspace(graphIdentifier.workspaceName());
        return workspace != null
                && workspace.listGraphNames().contains(graphIdentifier.graphName());
    }

    @Override
    public List<String> listGraphNames(String workspaceName) {
        return workspace(workspaceName).listGraphNames();
    }

    @Override
    public PrefixMappingReadOnly getPrefixMapping(String workspaceName) {
        var workspace = findWorkspace(workspaceName);
        return workspace == null
                ? new PrefixMappingReadOnly(PrefixMapping.Factory.create())
                : workspace.getPrefixMapping();
    }

    @Override
    public void writeToDatabase(
            DatabaseConnection databaseConnection, GraphIdentifier graphIdentifier) {
        final String workspaceName = graphIdentifier.workspaceName();
        final String graphName = graphIdentifier.graphName();
        assertThatGraphExists(graphIdentifier);
        try (var transaction = beginTransaction(workspaceName, ReadWrite.READ)) {
            var graphSource =
                    new GraphSourceBuilderImpl()
                            .setGraph(transaction.graph(graphName).getRdfGraph())
                            .setGraphName(graphName)
                            .build();
            databaseConnection.insertGraph(graphSource, workspaceName);
        }
    }

    private void clearGraphCollections() {
        lock.lock();
        try {
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
            var workspaceNames = databaseConnection.listDatasets();
            for (var workspaceName : workspaceNames) {
                if (!workspaceName.startsWith(SNAPSHOT_PREFIX)) {
                    var dataset = fetchDataset(databaseConnection, workspaceName);
                    workspaces.put(workspaceName, new Workspace(workspaceName, dataset));
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
     * @param workspaceName The name of the dataset.
     * @return fetched {@link Graph}
     */
    private Dataset fetchDataset(DatabaseConnection databaseConnection, String workspaceName) {
        // build query
        var graphVar = "?graph";
        var graphQuery =
                new SelectBuilder()
                        .addVar(graphVar)
                        .setDistinct(true)
                        .addGraph(graphVar, "?s", "?p", "?o")
                        .build();

        // fetch data
        var queryResultSet = databaseConnection.sendSelect(graphQuery, workspaceName).asResultSet();
        var prefixMapping = databaseConnection.getPrefixMapping(workspaceName);

        // insert prefixes
        var dataset = DatasetFactory.createGeneral();
        dataset.getPrefixMapping().setNsPrefixes(prefixMapping);

        // insert graphs
        // default
        var graph = fetchGraph(databaseConnection, workspaceName, "default");
        dataset.setDefaultModel(ModelFactory.createModelForGraph(graph));
        // named
        while (queryResultSet.hasNext()) {
            var graphName = queryResultSet.next().get(graphVar).asNode();
            graph = fetchGraph(databaseConnection, workspaceName, graphName.getURI());
            dataset.addNamedModel(graphName.getURI(), ModelFactory.createModelForGraph(graph));
        }
        return dataset;
    }

    /**
     * Fetches a single graph from a {@link DatabaseConnection database}.
     *
     * @param databaseConnection The connection to the external Database.
     * @param workspaceName The name of the dataset.
     * @param graphName The graphName.
     * @return fetched {@link Graph}
     */
    private Graph fetchGraph(
            DatabaseConnection databaseConnection, String workspaceName, String graphName) {
        var query =
                new CIMBaseQueryBuilder()
                        .setGraph(graphName)
                        .build()
                        .addVar("?sub")
                        .addVar("?pre")
                        .addVar("?obj")
                        .addWhere("?sub", "?pre", "?obj")
                        .build();
        var queryRes = databaseConnection.sendSelect(query, workspaceName).asResultSet();
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
    public boolean isReadOnly(String workspaceName) {
        return workspace(workspaceName).isReadOnly();
    }

    @Override
    public void enableEditing(String workspaceName) {
        workspace(workspaceName).setReadOnly(false);
    }

    @Override
    public void disableEditing(String workspaceName) {
        workspace(workspaceName).setReadOnly(true);
    }

    /**
     * Throws an exceptions if the dataset does not exist
     *
     * @param workspaceName The name of the dataset.
     * @throws DataAccessException if the dataset does not exist.
     */
    private void assertThatDatasetExists(String workspaceName) {
        if (!workspaces.containsKey(workspaceName)) {
            throw new ResourceNotFoundException("Workspace " + workspaceName + " does not exist");
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
        final String workspaceName = graphIdentifier.workspaceName();
        final String graphName = graphIdentifier.graphName();
        if (!workspace(workspaceName).listGraphNames().contains(graphName)) {
            throw new ResourceNotFoundException(
                    "Graph " + graphName + " does not exist in workspace " + workspaceName);
        }
    }
}

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

package org.rdfarchitect.database.snapshots;

import static org.rdfarchitect.database.snapshots.SnapshotUtils.constructSnapshotName;
import static org.rdfarchitect.database.snapshots.SnapshotUtils.generateBase64Token;

import org.apache.jena.graph.Graph;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.shared.impl.PrefixMappingImpl;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.database.SnapshotPort;
import org.rdfarchitect.exception.database.DataAccessException;
import org.rdfarchitect.exception.database.SnapshotException;
import org.rdfarchitect.rdf.graph.GraphUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * {@link SnapshotPort} adapter that keeps snapshots in process memory instead of a Fuseki server.
 * Snapshots are shared across sessions (the map is global, unlike the session-scoped workspaces)
 * but do not survive a backend restart.
 *
 * <p>A snapshot is held in the layout {@link FusekiSnapshotAdapter} writes — each schema graph,
 * plus the graphs {@link ShapesDocumentGraphs} makes of its constraints documents — and is loaded
 * through the same path a Fuseki snapshot is, so both stores bring back the same workspace.
 */
public class InMemorySnapshotAdapter implements SnapshotPort {

    private static final String DEFAULT_GRAPH_NAME = "default";

    private final DatabasePort databasePort;

    private final ConcurrentMap<String, StoredSnapshot> snapshots = new ConcurrentHashMap<>();

    /** Graphs by name, never handed out: every fetch loads copies, so one share serves many. */
    private record StoredSnapshot(
            String snapshotName, Map<String, Graph> graphsByName, PrefixMapping prefixMapping) {}

    public InMemorySnapshotAdapter(DatabasePort databasePort) {
        this.databasePort = databasePort;
    }

    @Override
    public String createSnapshot(String datasetName) {
        if (!databasePort.listDatasets().contains(datasetName)) {
            throw new DataAccessException("Dataset '" + datasetName + "' does not exist");
        }

        var base64Token = generateBase64Token();
        var snapshotName = constructSnapshotName(datasetName, base64Token);

        var graphsByName = new LinkedHashMap<String, Graph>();
        for (var graphUri : databasePort.listGraphUris(datasetName)) {
            copyGraph(new GraphIdentifier(datasetName, graphUri), graphsByName);
        }
        var prefixMapping =
                new PrefixMappingImpl().setNsPrefixes(databasePort.getPrefixMapping(datasetName));

        snapshots.put(base64Token, new StoredSnapshot(snapshotName, graphsByName, prefixMapping));
        return base64Token;
    }

    @Override
    public void fetchSnapshot(String base64Token) {
        var snapshot = snapshots.get(base64Token);
        if (snapshot == null) {
            throw new SnapshotException("Snapshot with token " + base64Token + " does not exist");
        }
        databasePort.restoreDataset(snapshot.snapshotName(), datasetOf(snapshot));
    }

    @Override
    public boolean snapshotExists(String base64Token) {
        return snapshots.containsKey(base64Token);
    }

    /** Adds the schema graph and its constraints documents, as copies, to {@code graphsByName}. */
    private void copyGraph(GraphIdentifier graphIdentifier, Map<String, Graph> graphsByName) {
        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.READ)) {
            var copiedGraph = GraphUtils.deepCopy(ctx.getRdfGraph());
            GraphUtils.removeUUIDs(copiedGraph);
            graphsByName.put(graphIdentifier.graphUri(), copiedGraph);
            graphsByName.putAll(
                    ShapesDocumentGraphs.of(
                            graphIdentifier.graphUri(), ctx.getShapesDocuments().values()));
        }
    }

    private static Dataset datasetOf(StoredSnapshot snapshot) {
        var dataset = DatasetFactory.createGeneral();
        dataset.getPrefixMapping().setNsPrefixes(snapshot.prefixMapping());
        snapshot.graphsByName()
                .forEach(
                        (name, graph) -> {
                            var model =
                                    ModelFactory.createModelForGraph(GraphUtils.deepCopy(graph));
                            // The dataset's default graph is listed by this name, and is read back
                            // from the default model, as when a snapshot comes from Fuseki.
                            if (DEFAULT_GRAPH_NAME.equals(name)) {
                                dataset.setDefaultModel(model);
                            } else {
                                dataset.addNamedModel(name, model);
                            }
                        });
        return dataset;
    }
}

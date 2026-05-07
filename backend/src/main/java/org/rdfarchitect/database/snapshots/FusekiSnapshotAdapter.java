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
import static org.rdfarchitect.database.snapshots.SnapshotUtils.findSnapshotName;
import static org.rdfarchitect.database.snapshots.SnapshotUtils.generateBase64Token;
import static org.rdfarchitect.rdf.graph.wrapper.GraphRewindableWithUUIDs.removeUUIDs;

import org.apache.jena.query.TxnType;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdfconnection.RDFConnection;
import org.apache.jena.system.Txn;
import org.rdfarchitect.config.DatabaseConfig;
import org.rdfarchitect.database.DatabaseConnection;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.database.SnapshotPort;
import org.rdfarchitect.database.implementations.http.FusekiHttpAdminProtocol;
import org.rdfarchitect.exception.database.DataAccessException;
import org.rdfarchitect.exception.database.FusekiServerException;
import org.rdfarchitect.exception.database.SnapshotException;
import org.rdfarchitect.rdf.graph.GraphUtils;
import org.rdfarchitect.rdf.graph.wrapper.GraphRewindableWithUUIDs;

public class FusekiSnapshotAdapter implements SnapshotPort {

    private final DatabasePort databasePort;
    private final DatabaseConnection databaseConnection;
    private final FusekiHttpAdminProtocol fusekiHttpAdminProtocol;
    private final String fusekiEndpoint;

    public FusekiSnapshotAdapter(
            DatabasePort databasePort,
            DatabaseConnection databaseConnection,
            DatabaseConfig databaseConfig) {
        this.databasePort = databasePort;
        this.databaseConnection = databaseConnection;
        this.fusekiEndpoint = databaseConfig.getHttpEndpoint();
        this.fusekiHttpAdminProtocol = new FusekiHttpAdminProtocol(fusekiEndpoint);
    }

    @Override
    public String createSnapshot(String datasetName) {
        if (!fusekiHttpAdminProtocol.ping()) {
            throw new FusekiServerException("Server not available");
        }
        if (!databasePort.listDatasets().contains(datasetName)) {
            throw new DataAccessException("Dataset '" + datasetName + "' does not exist");
        }

        var base64Token = generateBase64Token();
        String snapshotName = constructSnapshotName(datasetName, base64Token);
        String fusekiSnapshotURL = fusekiEndpoint + "/" + snapshotName;

        fusekiHttpAdminProtocol.createDataset(snapshotName);

        try (RDFConnection conn = RDFConnection.connect(fusekiSnapshotURL)) {
            Txn.executeWrite(
                    conn,
                    () -> {
                        for (var graphURI : databasePort.listGraphUris(datasetName)) {
                            transferGraph(conn, new GraphIdentifier(datasetName, graphURI));
                        }
                    });
        } catch (Exception e) {
            fusekiHttpAdminProtocol.deleteDataset(snapshotName);
            throw new DataAccessException("Error creating dataset snapshot", e);
        }
        return base64Token;
    }

    @Override
    public void fetchSnapshot(String base64Token) {
        if (!fusekiHttpAdminProtocol.ping()) {
            throw new FusekiServerException("Server not available");
        }
        if (!snapshotExists(base64Token)) {
            throw new SnapshotException("Snapshot with token " + base64Token + " does not exist");
        }
        databasePort.fetchSnapshot(databaseConnection, base64Token);
    }

    @Override
    public boolean snapshotExists(String base64Token) {
        return findSnapshotName(databaseConnection.listDatasets(), base64Token) != null;
    }

    private void transferGraph(RDFConnection conn, GraphIdentifier graphIdentifier) {
        GraphRewindableWithUUIDs graph = null;
        try {
            graph = databasePort.getGraphWithContext(graphIdentifier).getRdfGraph();
            graph.begin(TxnType.READ);

            var copiedGraph = GraphUtils.deepCopy(graph);
            copiedGraph
                    .getPrefixMapping()
                    .setNsPrefixes(databasePort.getPrefixMapping(graphIdentifier.datasetName()));

            removeUUIDs(copiedGraph);

            conn.put(graphIdentifier.graphUri(), ModelFactory.createModelForGraph(copiedGraph));
        } catch (Exception e) {
            throw new FusekiServerException(e.getMessage());
        } finally {
            if (graph != null) {
                graph.end();
            }
        }
    }
}

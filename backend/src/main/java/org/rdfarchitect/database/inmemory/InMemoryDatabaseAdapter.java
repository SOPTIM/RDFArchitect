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

import lombok.RequiredArgsConstructor;
import org.apache.jena.graph.Graph;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.shared.impl.PrefixMappingImpl;
import org.apache.jena.sparql.graph.GraphFactory;
import org.rdfarchitect.cim.rdf.resources.CIM;
import org.rdfarchitect.database.DatabaseConnection;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.models.cim.rdf.resources.CIMS;

import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
public class InMemoryDatabaseAdapter implements DatabasePort {

    private static final String CIM_PREFIX = "cim";
    private static final String CIMS_PREFIX = "cims";

    private final InMemoryDatabase database;

    @Override
    public GraphWithContext getGraphWithContext(GraphIdentifier graphIdentifier) {
        return database.getGraphWithContext(graphIdentifier);
    }

    @Override
    public PrefixMapping getPrefixMapping(String datasetName) {
        return database.getPrefixMapping(datasetName);
    }

    @Override
    public void deleteGraph(GraphIdentifier graphIdentifier) {
        database.remove(graphIdentifier);
    }

    @Override
    public void createGraph(GraphIdentifier graphIdentifier, Graph graph) {
        database.create(graphIdentifier, graph);
        var currentPrefixMapping = new PrefixMappingImpl()
                  .setNsPrefixes(database.getPrefixMapping(graphIdentifier.getDatasetName()))
                  .setNsPrefixes(graph.getPrefixMapping());
        database.setPrefixMapping(graphIdentifier.getDatasetName(), currentPrefixMapping);
    }

    @Override
    public void createEmptyGraph(GraphIdentifier graphIdentifier) {
        var datasetName = graphIdentifier.getDatasetName();
        var isNewDataset = !database.listDatasets().contains(datasetName);
        database.create(graphIdentifier, GraphFactory.createDefaultGraph());
        if (isNewDataset) {
            database.enableEditing(datasetName);
            var prefixMapping = new PrefixMappingImpl()
                      .setNsPrefixes(PrefixMapping.Standard)
                      .setNsPrefix(CIM_PREFIX, CIM.namespace)
                      .setNsPrefix(CIMS_PREFIX, CIMS.namespace);
            database.setPrefixMapping(graphIdentifier.getDatasetName(), prefixMapping);
        }
    }

    @Override
    public Boolean canRedo(GraphIdentifier graphIdentifier) {
        return database.canRedo(graphIdentifier);
    }

    @Override
    public Boolean canUndo(GraphIdentifier graphIdentifier) {
        return database.canUndo(graphIdentifier);
    }

    @Override
    public void redo(GraphIdentifier graphIdentifier) {
        database.redo(graphIdentifier);
    }

    @Override
    public void undo(GraphIdentifier graphIdentifier) {
        database.undo(graphIdentifier);
    }

    @Override
    public void restore(GraphIdentifier graphIdentifier, UUID versionId) {
        database.restore(graphIdentifier, versionId);
    }

    @Override
    public List<String> listGraphUris(String datasetName) {
        return database.listGraphUris(datasetName);
    }

    @Override
    public void persist(DatabaseConnection databaseConnection, GraphIdentifier graphIdentifier) {
        database.writeToDatabase(databaseConnection, graphIdentifier);
    }

    @Override
    public void setPrefixMapping(String datasetName, PrefixMapping prefixMapping) {
        database.setPrefixMapping(datasetName, prefixMapping);
    }

    @Override
    public List<String> listDatasets() {
        return database.listDatasets();
    }

    @Override
    public void deleteDataset(String datasetName) {
        database.deleteDataset(datasetName);
    }

    @Override
    public void fetchFromDatabase(DatabaseConnection databaseConnection) {
        database.fetchFromDatabase(databaseConnection);
    }

    @Override
    public void fetchSnapshot(DatabaseConnection databaseConnection, String base64Token) {
        database.fetchSnapshot(databaseConnection, base64Token);
    }

    @Override
    public boolean isReadOnly(String datasetName) {
        return database.isReadOnly(datasetName);
    }

    @Override
    public void enableEditing(String datasetName) {
        database.enableEditing(datasetName);
    }

    @Override
    public void disableEditing(String datasetName) {
        database.disableEditing(datasetName);
    }
}

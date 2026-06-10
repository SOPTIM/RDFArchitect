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
import org.rdfarchitect.database.DatabaseConnection;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphContext;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.database.inmemory.diagrams.CustomDiagram;
import org.rdfarchitect.rdf.graph.wrapper.DiagramLayout;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RequiredArgsConstructor
public class InMemoryDatabaseAdapter implements DatabasePort {

    private final InMemoryDatabase database;

    @Override
    public GraphContext getGraphWithContext(GraphIdentifier graphIdentifier) {
        return database.getGraphWithContext(graphIdentifier);
    }

    @Override
    public Map<UUID, CustomDiagram> getDatasetDiagrams(String datasetName) {
        return database.getDatasetDiagrams(datasetName);
    }

    @Override
    public DiagramLayout getDatasetDiagramLayout(String datasetName) {
        return database.getDatasetDiagramLayout(datasetName);
    }

    @Override
    public UUID getCrossProfileDiagramUUID(String datasetName) {
        return database.getCrossProfileDiagramUUID(datasetName);
    }

    @Override
    public String getCrossProfileDiagramColor(GraphIdentifier graphIdentifier) {
        return database.getCrossProfileDiagramColor(graphIdentifier);
    }

    @Override
    public void setCrossProfileDiagramColor(GraphIdentifier graphIdentifier, String color) {
        database.setCrossProfileDiagramColor(graphIdentifier, color);
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
        database.createGraph(graphIdentifier, graph);
    }

    @Override
    public void createEmptyGraph(GraphIdentifier graphIdentifier) {
        database.createEmptyGraph(graphIdentifier);
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

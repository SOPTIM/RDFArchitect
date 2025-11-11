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

package org.rdfarchitect.database.implementations.file;

import org.apache.jena.query.Query;
import org.apache.jena.riot.Lang;
import org.apache.jena.sparql.graph.PrefixMappingReadOnly;
import org.apache.jena.update.Update;
import org.rdfarchitect.database.DatabaseConnection;
import org.rdfarchitect.database.implementations.file.command.FileSelectCommandImpl;
import org.rdfarchitect.database.implementations.file.command.FileUpdateCommandImpl;
import org.rdfarchitect.exception.database.DataAccessException;
import org.rdfarchitect.rdf.formatter.ResultFormatter;
import org.rdfarchitect.rdf.graph.source.GraphSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class FileConnectionImpl implements DatabaseConnection {

    private static final Logger logger = LoggerFactory.getLogger(FileConnectionImpl.class);

    private final String path;
    private final String defaultDataset;
    private final Lang lang;

    public FileConnectionImpl(String path, String defaultDataset, Lang lang) {
        this.path = path;
        this.defaultDataset = defaultDataset;
        this.lang = lang;
        new FileDatabase(this.path, this.lang)
                  .createDatabase();
        logger.info("Initialized a file databaseConnection at: {} with default dataset: {} using language: {}", this.path, this.defaultDataset, this.lang.getName());
    }

    @Override
    public void sendInsert(Update insertUpdate) {
        this.sendInsert(insertUpdate, this.defaultDataset);
    }

    @Override
    public void sendInsert(Update insertUpdate, String datasetName) {
        new FileUpdateCommandImpl(this.lang)
                  .setEndpoint(this.path)
                  .setDatasetName(datasetName)
                  .setUpdate(insertUpdate)
                  .execute();
    }

    @Override
    public void insertGraph(GraphSource graphSource) throws DataAccessException {
        this.insertGraph(graphSource, this.defaultDataset);
    }

    @Override
    public void insertGraph(GraphSource graphSource, String datasetName) throws DataAccessException {
        new FileUpdateCommandImpl(this.lang)
                  .setEndpoint(this.path)
                  .setDatasetName(datasetName)
                  .insertGraph(graphSource);
    }

    @Override
    public void sendDelete(Update deleteUpdate) {
        this.sendDelete(deleteUpdate, this.defaultDataset);
    }

    @Override
    public void sendDelete(Update deleteUpdate, String datasetName) {
        new FileUpdateCommandImpl(this.lang)
                  .setEndpoint(this.path)
                  .setDatasetName(datasetName)
                  .setUpdate(deleteUpdate)
                  .execute();
    }

    @Override
    public void deleteGraph(String graphName) {
        this.deleteGraph(this.defaultDataset, graphName);
    }

    @Override
    public void deleteGraph(String datasetName, String graphName) {
        new FileUpdateCommandImpl(this.lang)
                  .setEndpoint(this.path)
                  .setDatasetName(datasetName)
                  .deleteGraph(graphName);
    }

    @Override
    public void deleteDefaultGraph() {
        this.deleteDefaultGraph(defaultDataset);
    }

    @Override
    public void deleteDefaultGraph(String datasetName) {
        this.deleteGraph(datasetName, null);
    }

    @Override
    public void deleteDataset() {
        this.deleteDataset(defaultDataset);
    }

    @Override
    public void deleteDataset(String datasetName) {
        new FileUpdateCommandImpl(this.lang)
                  .setEndpoint(this.path)
                  .setDatasetName(datasetName)
                  .deleteDataset();
    }

    @Override
    public void sendUpdate(Update update) {
        this.sendUpdate(update, defaultDataset);
    }

    @Override
    public void sendUpdate(Update update, String datasetName) {
        new FileUpdateCommandImpl(this.lang)
                  .setEndpoint(this.path)
                  .setDatasetName(datasetName)
                  .setUpdate(update)
                  .execute();
    }

    @Override
    public ResultFormatter sendSelect(Query query) {
        return this.sendSelect(query, defaultDataset);
    }

    @Override
    public ResultFormatter sendSelect(Query query, String datasetName) {
        return new FileSelectCommandImpl(this.lang)
                  .setEndpoint(this.path)
                  .setDatasetName(datasetName)
                  .setQuery(query)
                  .execute();
    }

    @Override
    public PrefixMappingReadOnly getPrefixMapping(String datasetName) {
        return new FileSelectCommandImpl(this.lang)
                  .setEndpoint(this.path)
                  .setDatasetName(datasetName)
                  .getCurrentPrefixMapping();
    }

    @Override
    public void addPrefix(String datasetName, String prefix, String uri) {
        new FileUpdateCommandImpl(this.lang)
                  .setEndpoint(this.path)
                  .setDatasetName(datasetName)
                  .addPrefix(prefix, uri);
    }

    @Override
    public void deletePrefix(String datasetName, String prefix) {
        new FileUpdateCommandImpl(this.lang)
                  .setEndpoint(this.path)
                  .setDatasetName(datasetName)
                  .deletePrefix(prefix);
    }

    @Override
    public List<String> listDatasets() {
        return new FileSelectCommandImpl(this.lang)
                  .setEndpoint(this.path)
                  .listDatasets();
    }
}

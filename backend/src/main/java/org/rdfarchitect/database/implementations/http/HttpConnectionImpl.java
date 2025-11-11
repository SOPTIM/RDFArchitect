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

package org.rdfarchitect.database.implementations.http;

import org.apache.jena.query.Query;
import org.apache.jena.sparql.graph.PrefixMappingReadOnly;
import org.apache.jena.update.Update;
import org.rdfarchitect.database.DatabaseConnection;
import org.rdfarchitect.database.implementations.DatabaseAdminProtocol;
import org.rdfarchitect.database.implementations.http.command.HttpSelectCommandImpl;
import org.rdfarchitect.database.implementations.http.command.HttpUpdateCommandImpl;
import org.rdfarchitect.exception.database.DataAccessException;
import org.rdfarchitect.rdf.formatter.ResultFormatter;
import org.rdfarchitect.rdf.graph.source.GraphSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class HttpConnectionImpl implements DatabaseConnection {

    private static final Logger logger = LoggerFactory.getLogger(HttpConnectionImpl.class);

    private final String url;
    private final String defaultDataset;
    private final DatabaseAdminProtocol databaseAdminProtocol;

    public HttpConnectionImpl(String url, String defaultDataset, DatabaseAdminProtocol databaseAdminProtocol) {
        this.url = url;
        this.defaultDataset = defaultDataset;
        this.databaseAdminProtocol = databaseAdminProtocol;
        logger.info("Initialized a databaseConnection via http with url: {} with default dataset: {}", this.url, this.defaultDataset);
    }

    @Override
    public void sendInsert(Update insertUpdate) throws DataAccessException {
        this.sendInsert(insertUpdate, this.defaultDataset);
    }

    @Override
    public void sendInsert(Update insertUpdate, String datasetName) throws DataAccessException {
        new HttpUpdateCommandImpl(this.databaseAdminProtocol)
                  .setEndpoint(this.url)
                  .setDatasetName(datasetName)
                  .setUpdate(insertUpdate)
                  .execute();
    }

    @Override
    public void insertGraph(GraphSource graphsource) throws DataAccessException {
        this.insertGraph(graphsource, this.defaultDataset);
    }

    @Override
    public void insertGraph(GraphSource graphsource, String datasetName) throws DataAccessException {
        new HttpUpdateCommandImpl(this.databaseAdminProtocol)
                  .setEndpoint(this.url)
                  .setDatasetName(datasetName)
                  .insertGraph(graphsource);
    }

    @Override
    public void sendDelete(Update deleteUpdate) throws DataAccessException {
        this.sendDelete(deleteUpdate, this.defaultDataset);
    }

    @Override
    public void sendDelete(Update deleteUpdate, String datasetName) throws DataAccessException {
        new HttpUpdateCommandImpl(this.databaseAdminProtocol)
                  .setEndpoint(this.url)
                  .setDatasetName(datasetName)
                  .setUpdate(deleteUpdate)
                  .execute();
    }

    @Override
    public void deleteGraph(String datasetName) throws DataAccessException {
        this.deleteGraph(datasetName, this.defaultDataset);
    }

    @Override
    public void deleteGraph(String datasetName, String graphName) throws DataAccessException {
        new HttpUpdateCommandImpl(this.databaseAdminProtocol)
                  .setEndpoint(this.url)
                  .setDatasetName(datasetName)
                  .deleteGraph(graphName);
    }

    @Override
    public void deleteDefaultGraph() throws DataAccessException {
        this.deleteDefaultGraph(this.defaultDataset);
    }

    @Override
    public void deleteDefaultGraph(String datasetName) throws DataAccessException {
        this.deleteGraph(datasetName, null);
    }

    @Override
    public void deleteDataset() {
        this.deleteDataset(this.defaultDataset);
    }

    @Override
    public void deleteDataset(String datasetName) {
        new HttpUpdateCommandImpl(this.databaseAdminProtocol)
                  .setEndpoint(this.url)
                  .setDatasetName(datasetName)
                  .deleteDataset();
    }

    @Override
    public void sendUpdate(Update update) throws DataAccessException {
        this.sendUpdate(update, this.defaultDataset);
    }

    @Override
    public void sendUpdate(Update update, String datasetName) throws DataAccessException {
        new HttpUpdateCommandImpl(this.databaseAdminProtocol)
                  .setEndpoint(this.url)
                  .setDatasetName(datasetName)
                  .setUpdate(update)
                  .execute();
    }

    @Override
    public ResultFormatter sendSelect(Query query) throws DataAccessException {
        return this.sendSelect(query, this.defaultDataset);
    }

    @Override
    public ResultFormatter sendSelect(Query query, String datasetName) throws DataAccessException {
        return new HttpSelectCommandImpl()
                  .setEndpoint(this.url)
                  .setDatasetName(datasetName)
                  .setQuery(query)
                  .execute();
    }

    @Override
    public PrefixMappingReadOnly getPrefixMapping(String datasetName) {
        return new HttpSelectCommandImpl(this.databaseAdminProtocol)
                  .setEndpoint(this.url)
                  .setDatasetName(datasetName)
                  .getCurrentPrefixMapping();
    }

    @Override
    public void addPrefix(String datasetName, String prefix, String uri) {
        new HttpUpdateCommandImpl(this.databaseAdminProtocol)
                  .setEndpoint(this.url)
                  .setDatasetName(datasetName)
                  .addPrefix(prefix, uri);
    }

    @Override
    public void deletePrefix(String datasetName, String prefix) {
        new HttpUpdateCommandImpl(this.databaseAdminProtocol)
                  .setEndpoint(this.url)
                  .setDatasetName(datasetName)
                  .deletePrefix(prefix);
    }

    @Override
    public List<String> listDatasets() {
        return new HttpSelectCommandImpl(this.databaseAdminProtocol)
                  .setEndpoint(this.url)
                  .listDatasets();
    }
}

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

package org.rdfarchitect.database.implementations.http.command;

import org.apache.jena.atlas.web.HttpException;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.shared.JenaException;
import org.apache.jena.sparql.engine.http.QueryExceptionHTTP;
import org.apache.jena.sparql.exec.http.GSP;
import org.apache.jena.sparql.exec.http.UpdateExecutionHTTPBuilder;
import org.apache.jena.sparql.modify.request.QuadDataAcc;
import org.apache.jena.sparql.modify.request.UpdateDataInsert;
import org.apache.jena.sparql.modify.request.UpdateDrop;
import org.apache.jena.update.Update;
import org.apache.jena.update.UpdateRequest;
import org.rdfarchitect.database.command.DatabaseUpdateCommand;
import org.rdfarchitect.database.implementations.DatabaseAdminProtocol;
import org.rdfarchitect.exception.database.DataAccessException;
import org.rdfarchitect.exception.database.UpdateException;
import org.rdfarchitect.rdf.graph.source.GraphSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;

public class HttpUpdateCommandImpl implements DatabaseUpdateCommand {

    private static final Logger logger = LoggerFactory.getLogger(HttpUpdateCommandImpl.class);

    private String endpoint;
    private String url;
    private String datasetName;
    private Update update;
    private final DatabaseAdminProtocol databaseAdminProtocol;

    public HttpUpdateCommandImpl(DatabaseAdminProtocol databaseAdminProtocol) {
        this.endpoint = null;
        this.url = null;
        this.datasetName = null;
        this.update = null;
        this.databaseAdminProtocol = databaseAdminProtocol;
    }

    @Override
    public HttpUpdateCommandImpl setEndpoint(String endpoint) {
        this.url = endpoint;
        this.endpoint = this.url + "/" + this.datasetName;
        return this;
    }

    @Override
    public HttpUpdateCommandImpl setDatasetName(String datasetName) {
        this.datasetName = datasetName;
        this.endpoint = this.url + "/" + datasetName;
        return this;
    }

    @Override
    public HttpUpdateCommandImpl setUpdate(Update update) {
        this.update = update;
        return this;
    }

    @Override
    public void execute() throws DataAccessException {
        if (update == null) {
            throw new UpdateException(
                    "Update is null. Execution against endpoint " + this.endpoint + " skipped.");
        }
        String hex = Integer.toHexString(this.update.hashCode());
        logger.debug(
                "Execute update@{} against endpoint \"{}\":\n{}", hex, this.endpoint, this.update);
        try {
            UpdateExecutionHTTPBuilder.create()
                    .endpoint(this.endpoint)
                    .update(this.update)
                    .build()
                    .execute();
        } catch (QueryExceptionHTTP e) {
            throw new DataAccessException(
                    "Failed to execute update against endpoint "
                            + this.endpoint
                            + " due to "
                            + "an incorrect SPARQL query or internal server error.",
                    e);
        } catch (HttpException e) {
            throw new DataAccessException(
                    "Failed to execute update against endpoint "
                            + this.endpoint
                            + " due to "
                            + "an HTTP communication error.",
                    e);
        } catch (JenaException e) {
            throw new DataAccessException(
                    "Failed to execute update against endpoint" + this.endpoint + ".", e);
        }
        logger.debug(
                "Successfully executed update@{} against endpoint: \"{}\"", hex, this.endpoint);
    }

    private void ensureDatasetExists() {
        if (!databaseAdminProtocol.listDatasets().contains(this.datasetName)) {
            databaseAdminProtocol.createDataset(this.datasetName);
        }
    }

    @Override
    public void insertGraph(GraphSource graphSource) throws DataAccessException {
        ensureDatasetExists();
        // insert graph into dataset/database
        String graphName = graphSource.graphName();
        Graph graph = graphSource.graph();
        if (graphName == null || graphName.isEmpty()) {
            GSP.service(endpoint).defaultGraph().POST(graph);
            logger.debug("Insert default graph into endpoint \"{}\"", this.endpoint);
        } else {
            GSP.service(endpoint).graphName(graphName).POST(graph);
            logger.debug("Insert graph \"{}\" into endpoint \"{}\"", graphName, this.endpoint);
        }
    }

    /**
     * A SPARQL update rather than a graph store PUT, for two reasons. Fuseki applies one update
     * request as one transaction, where several PUTs are several. And a PUT over an existing graph
     * clears the prefixes of the whole dataset, which is every schema's prefixes.
     */
    @Override
    public void replaceGraphs(Collection<String> removed, Map<String, Graph> written)
            throws DataAccessException {
        ensureDatasetExists();
        var request = new UpdateRequest();
        var names = new LinkedHashSet<>(removed);
        names.addAll(written.keySet());
        names.forEach(name -> request.add(new UpdateDrop(NodeFactory.createURI(name), true)));
        if (!written.isEmpty()) {
            var quads = new QuadDataAcc();
            written.forEach(
                    (name, graph) -> {
                        quads.setGraph(NodeFactory.createURI(name));
                        graph.find().forEachRemaining(quads::addTriple);
                    });
            request.add(new UpdateDataInsert(quads));
        }
        logger.debug("Replace graphs {} in endpoint \"{}\"", names, this.endpoint);
        try {
            UpdateExecutionHTTPBuilder.create()
                    .endpoint(this.endpoint)
                    .update(request)
                    .build()
                    .execute();
        } catch (HttpException | JenaException e) {
            throw new DataAccessException(
                    "Failed to replace graphs at endpoint " + this.endpoint + ".", e);
        }
    }

    @Override
    public void deleteGraph(String graphName) {
        if (graphName == null || graphName.isEmpty()) {
            logger.debug("Delete default graph from endpoint \"{}\"", endpoint);
            GSP.service(endpoint).defaultGraph().DELETE();
        } else {
            logger.debug("Delete graph: \"{}\" from endpoint  \"{}\"", graphName, endpoint);
            GSP.service(endpoint).graphName(graphName).DELETE();
        }
    }

    @Override
    public void deleteDataset() {
        this.databaseAdminProtocol.deleteDataset(this.datasetName);
    }

    @Override
    public void addPrefix(String prefix, String uri) {
        @SuppressWarnings("java:S2095") // warning doesn't make sense, since it's not closable
        HttpClient client = HttpClient.newHttpClient();

        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(URI.create(endpoint + "/prefixes-rw?prefix=" + prefix + "&uri=" + uri))
                        .POST(HttpRequest.BodyPublishers.ofString(""))
                        .build();

        try {
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == HttpStatus.BAD_REQUEST.value()) {
                throw new DataAccessException(response.body());
            } else if (response.statusCode() != HttpStatus.OK.value()) {
                throw new DataAccessException(
                        "Failed to update prefixes at endpoint \""
                                + this.endpoint
                                + "/prefixes-rw\""
                                + ".");
            }
        } catch (IOException e) {
            throw new DataAccessException(
                    "I/O-Error! Failed to access prefixes from endpoint \""
                            + this.endpoint
                            + "/prefixes\""
                            + ".",
                    e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DataAccessException(
                    "Thread interrupted! Failed to access prefixes from endpoint \""
                            + this.endpoint
                            + "/prefixes\""
                            + ".",
                    e);
        }
    }

    @Override
    public void deletePrefix(String prefix) {
        @SuppressWarnings("java:S2095") // warning doesn't make sense, since it's not closable
        HttpClient client = HttpClient.newHttpClient();

        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(URI.create(endpoint + "/prefixes-rw?prefix=" + prefix))
                        .DELETE()
                        .build();

        try {
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == HttpStatus.BAD_REQUEST.value()) {
                throw new DataAccessException(response.body());
            } else if (response.statusCode() != HttpStatus.OK.value()) {
                throw new DataAccessException(
                        "Failed to delete prefix at endpoint \""
                                + this.endpoint
                                + "/prefixes-rw\""
                                + ".");
            }
        } catch (IOException e) {
            throw new DataAccessException(
                    "I/O-Error! Failed to access prefixes from endpoint \""
                            + this.endpoint
                            + "/prefixes\""
                            + ".",
                    e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DataAccessException(
                    "Thread interrupted! Failed to access prefixes from endpoint \""
                            + this.endpoint
                            + "/prefixes\""
                            + ".",
                    e);
        }
    }
}

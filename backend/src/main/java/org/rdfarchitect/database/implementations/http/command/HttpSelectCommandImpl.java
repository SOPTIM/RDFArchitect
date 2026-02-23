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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.jena.atlas.web.HttpException;
import org.apache.jena.query.Query;
import org.apache.jena.query.ResultSetFormatter;
import org.apache.jena.shared.JenaException;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.sparql.engine.http.QueryExceptionHTTP;
import org.apache.jena.sparql.exec.http.QueryExecutionHTTPBuilder;
import org.apache.jena.sparql.graph.PrefixMappingReadOnly;
import org.rdfarchitect.database.command.DatabaseSelectCommand;
import org.rdfarchitect.database.implementations.DatabaseAdminProtocol;
import org.rdfarchitect.exception.database.DataAccessException;
import org.rdfarchitect.exception.database.QueryException;
import org.rdfarchitect.rdf.formatter.ResultFormatter;
import org.rdfarchitect.rdf.formatter.ResultSetFormatterImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

public class HttpSelectCommandImpl implements DatabaseSelectCommand {

    private static final Logger logger = LoggerFactory.getLogger(HttpSelectCommandImpl.class);

    private String endpoint;
    private String url;
    private String datasetName;
    private Query query;
    private final DatabaseAdminProtocol databaseAdminProtocol;

    public HttpSelectCommandImpl() {
        this(null);
    }

    public HttpSelectCommandImpl(DatabaseAdminProtocol databaseAdminProtocol) {
        this.endpoint = null;
        this.url = null;
        this.datasetName = null;
        this.query = null;
        this.databaseAdminProtocol = databaseAdminProtocol;
    }

    @Override
    public HttpSelectCommandImpl setEndpoint(String endpoint) {
        this.url = endpoint;
        this.endpoint = this.url + "/" + this.datasetName;
        return this;
    }

    @Override
    public HttpSelectCommandImpl setDatasetName(String datasetName) {
        this.datasetName = datasetName;
        this.endpoint = this.url + "/" + datasetName;
        return this;
    }

    @Override
    public HttpSelectCommandImpl setQuery(Query query) {
        this.query = query;
        return this;
    }

    @Override
    public ResultFormatter execute() throws DataAccessException {
        if (query == null) {
            throw new QueryException("Query is null. Execution against endpoint " + this.endpoint + " skipped.");
        }

        var hex = Integer.toHexString(this.query.hashCode());
        logger.debug("Execute query@{} against endpoint \"{}\":\n{}", hex, this.endpoint, this.query);
        try (var qExec = QueryExecutionHTTPBuilder
                  .create()
                  .endpoint(this.endpoint)
                  .query(this.query)
                  .build()){
            var result = qExec.execSelect().rewindable();

            if (logger.isDebugEnabled()) {
                logger.debug("Received result for query@{} from \"{}\":\n{}", hex, this.endpoint, ResultSetFormatter.asText(result));
            }
            result.reset();
            return new ResultSetFormatterImpl(result);
        } catch (QueryExceptionHTTP e) {
            throw new DataAccessException("Failed to execute query against endpoint " + this.endpoint + " due to " +
                                                    "an incorrect SPARQL query or internal server error.", e);
        } catch (HttpException e) {
            throw new DataAccessException("Failed to execute query against endpoint " + this.endpoint + " due to " +
                                                    "an HTTP communication error.", e);
        } catch (JenaException e) {
            throw new DataAccessException("Failed to execute query against endpoint" + this.endpoint + ".", e);
        }
    }

    @Override
    public PrefixMappingReadOnly getCurrentPrefixMapping() {
        try (var client = HttpClient.newHttpClient()) {
            var request = HttpRequest.newBuilder()
                                     .uri(URI.create(endpoint + "/prefixes"))
                                     .GET()
                                     .build();

            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            var result = PrefixMapping.Factory.create();
            List<Map<String, String>> httpResult = new ObjectMapper().readValue(response.body(), new TypeReference<>() {
            });
            for (var map : httpResult) {
                var prefix = map.get("prefix");
                var uri = map.get("uri");
                result.setNsPrefix(prefix, uri);
            }
            return new PrefixMappingReadOnly(result);
        } catch (IOException e) {
            throw new DataAccessException("I/O-Error! Failed to access prefixes from endpoint \"" + this.endpoint + "/prefixes\"" + ".", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DataAccessException("Thread interrupted! Failed to access prefixes from endpoint \"" + this.endpoint + "/prefixes\"" + ".", e);
        }
    }

    @Override
    public List<String> listDatasets() {
        return databaseAdminProtocol.listDatasets();
    }
}

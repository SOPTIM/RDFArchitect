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

package org.rdfarchitect.database.implementations.file.command;

import org.apache.jena.query.Dataset;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.ResultSetFormatter;
import org.apache.jena.query.ResultSetRewindable;
import org.apache.jena.riot.Lang;
import org.apache.jena.sparql.graph.PrefixMappingReadOnly;
import org.rdfarchitect.database.command.DatabaseSelectCommand;
import org.rdfarchitect.database.implementations.file.FileDatabase;
import org.rdfarchitect.exception.database.DataAccessException;
import org.rdfarchitect.exception.database.QueryException;
import org.rdfarchitect.rdf.formatter.ResultFormatter;
import org.rdfarchitect.rdf.formatter.ResultSetFormatterImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class FileSelectCommandImpl implements DatabaseSelectCommand {

    private static final Logger logger = LoggerFactory.getLogger(FileSelectCommandImpl.class);

    private String path;
    private String datasetName;
    private Query query;

    private final Lang lang;

    public FileSelectCommandImpl(Lang lang) {
        this.path = null;
        this.datasetName = null;
        this.query = null;
        this.lang = lang;
    }

    @Override
    public FileSelectCommandImpl setEndpoint(String endpoint) {
        this.path = endpoint;
        return this;
    }

    @Override
    public FileSelectCommandImpl setDatasetName(String datasetName) {
        this.datasetName = datasetName;
        return this;
    }

    @Override
    public FileSelectCommandImpl setQuery(Query query) {
        this.query = query;
        return this;
    }

    @Override
    public ResultFormatter execute() {
        if (query == null) {
            throw new QueryException("Query is null. Execution against endpoint \"" + this.path + "\\" +
                                               this.datasetName + "." + this.lang.getFileExtensions().get(0) + "\" skipped.");
        }

        String hex = Integer.toHexString(this.query.hashCode());
        logger.debug("Execute query@{} against endpoint \"{}/{}\":\n{}",
                     hex,
                     this.path,
                     this.datasetName,
                     this.query);

        Dataset dataset = new FileDatabase(this.path, this.lang)
                  .getDataset(datasetName);


        try (QueryExecution qExec = QueryExecutionFactory.create(query, dataset)) {
            ResultSetRewindable result = qExec.execSelect().rewindable();
            logger.debug("Received result for query@{} from \"{}/{}\":\n{}",
                         hex,
                         this.path,
                         this.datasetName,
                         ResultSetFormatter.asText(result));
            result.reset();
            return new ResultSetFormatterImpl(result);
        } catch (Exception e) {
            logger.debug("Failed to execute query@{} against endpoint \"{}/{}\"",
                         hex,
                         this.path,
                         this.datasetName);
            throw new DataAccessException("Failed to execute query@" + hex + " against endpoint \"" + this.path + "\\" +
                                                    this.datasetName + "." + this.lang.getFileExtensions().get(0) + "\"", e);
        }
    }

    @Override
    public PrefixMappingReadOnly getCurrentPrefixMapping() {
        FileDatabase database = new FileDatabase(this.path, this.lang);
        return new PrefixMappingReadOnly(database.getDataset(this.datasetName).getPrefixMapping());
    }

    @Override
    public List<String> listDatasets() {
        return new FileDatabase(this.path, this.lang).listDatasets();
    }
}

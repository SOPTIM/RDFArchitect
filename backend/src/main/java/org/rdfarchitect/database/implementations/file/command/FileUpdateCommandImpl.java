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

import org.apache.jena.graph.Graph;
import org.apache.jena.query.Dataset;
import org.apache.jena.riot.Lang;
import org.apache.jena.update.Update;
import org.apache.jena.update.UpdateExecutionFactory;
import org.rdfarchitect.database.command.DatabaseUpdateCommand;
import org.rdfarchitect.database.implementations.file.FileDatabase;
import org.rdfarchitect.exception.database.DataAccessException;
import org.rdfarchitect.exception.database.QueryException;
import org.rdfarchitect.exception.database.UpdateException;
import org.rdfarchitect.rdf.graph.source.GraphSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileUpdateCommandImpl implements DatabaseUpdateCommand {

    private static final Logger logger = LoggerFactory.getLogger(FileUpdateCommandImpl.class);

    private String path;
    private String datasetName;
    private Update update;

    private final Lang lang;

    public FileUpdateCommandImpl(Lang lang) {
        this.path = null;
        this.datasetName = null;
        this.update = null;
        this.lang = lang;
    }

    @Override
    public FileUpdateCommandImpl setEndpoint(String endpoint) {
        this.path = endpoint;
        return this;
    }

    @Override
    public FileUpdateCommandImpl setDatasetName(String datasetName) {
        this.datasetName = datasetName;
        return this;
    }

    @Override
    public FileUpdateCommandImpl setUpdate(Update update) {
        this.update = update;
        return this;
    }

    @Override
    public void execute() {
        if (update == null) {
            throw new QueryException("Query is null. Execution against endpoint \"" + this.path + "\\" +
                                               this.datasetName + "." + this.lang.getFileExtensions().get(0) + "\" skipped.");
        }

        String hex = Integer.toHexString(this.update.hashCode());
        logger.debug("Execute update@{} against endpoint \"{}/{}\":\n{}",
                     hex,
                     this.path,
                     this.datasetName,
                     this.update);

        FileDatabase database = new FileDatabase(this.path, this.lang);
        Dataset dataset = database
                  .getDataset(this.datasetName);
        try {
            UpdateExecutionFactory.create(this.update, dataset).execute();
            database.write(dataset, this.datasetName);
        } catch (Exception e) {
            logger.debug("Failed to execute update@{} against endpoint \"{}/{}\"",
                         hex,
                         this.path,
                         this.datasetName);
            throw new UpdateException("Failed to execute update", e);
        }
        logger.debug("Successfully executed update@{} against endpoint \"{}/{}\"",
                     hex,
                     this.path,
                     this.datasetName);
    }

    @Override
    public void insertGraph(GraphSource graphSource) throws DataAccessException {
        Graph graph = graphSource.graph();
        String graphName = graphSource.graphName();
        var newGraphPrefixMapping = graph.getPrefixMapping().getNsPrefixMap();
        var currentPrefixMapping = new FileSelectCommandImpl(lang)
                  .setEndpoint(this.path)
                  .setDatasetName(datasetName)
                  .getCurrentPrefixMapping()
                  .getNsPrefixMap();
        for (var newPrefix : newGraphPrefixMapping.entrySet()) {
            if (currentPrefixMapping.containsKey(newPrefix.getKey())
                      && !currentPrefixMapping.get(newPrefix.getKey()).equals(newPrefix.getValue())) {
                throw new DataAccessException("Graph prefix '" + newPrefix.getKey() + "' already exists in combination with uri '" + newPrefix.getValue() + "' in dataset: '" + datasetName + "'.");
            }
        }
        new FileDatabase(this.path, this.lang)
                  .write(graph, this.datasetName, graphName);
    }

    @Override
    public void deleteGraph(String graphName) {
        FileDatabase database = new FileDatabase(this.path, this.lang);
        Dataset dataset = database.getDataset(this.datasetName);
        if (graphName == null || graphName.isEmpty()) { //add default model
            dataset.getDefaultModel().removeAll();
        } else if (dataset.containsNamedModel(graphName)) {
            dataset.removeNamedModel(graphName);
        } else {
            logger.debug("Failed to delete graph \"{}\" from dataset \"{}\", because it doesnt exist", graphName, this.datasetName);
            throw new DataAccessException("Failed to delete graph \"" + graphName + "\" from dataset \"" + this.datasetName + "\", because it doesnt exist");
        }
        database.write(dataset, this.datasetName);
        logger.info("Deleted graph \"{}\" from dataset \"{}\"", graphName, this.datasetName);
    }

    @Override
    public void deleteDataset() {
        FileDatabase database = new FileDatabase(this.path, this.lang);
        database.deleteDataset(this.datasetName);
    }

    @Override
    public void addPrefix(String prefix, String uri) {
        FileDatabase database = new FileDatabase(this.path, this.lang);
        var dataset = database.getDataset(this.datasetName);
        dataset.getPrefixMapping().setNsPrefix(prefix, uri);
        new FileDatabase(this.path, this.lang)
                  .write(dataset, this.datasetName);
    }

    @Override
    public void deletePrefix(String prefix) {
        FileDatabase database = new FileDatabase(this.path, this.lang);
        var dataset = database.getDataset(this.datasetName);
        dataset.getPrefixMapping().removeNsPrefix(prefix);
        new FileDatabase(this.path, this.lang)
                  .write(dataset, this.datasetName);
    }
}

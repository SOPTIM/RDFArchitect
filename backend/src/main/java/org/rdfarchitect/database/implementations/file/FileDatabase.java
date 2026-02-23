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

import org.apache.jena.graph.Graph;
import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.rdfarchitect.exception.database.DataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class FileDatabase {

    private static final Logger logger = LoggerFactory.getLogger(FileDatabase.class);

    private final Path databasePath;
    private final Lang lang;

    public FileDatabase(String path, Lang lang) {
        this.databasePath = Paths.get(path);
        this.lang = lang;
        createDatabase();
    }

    /**
     * created a Database directory if it doesn't exist yet
     */
    public void createDatabase() {
        if (FileDatabaseReadWriter.ping(this.databasePath)) {
            logger.debug("{} file database already exists so there is no need to create one", lang.getName());
            return;
        }
        try {
            FileDatabaseReadWriter.createDatabase(this.databasePath);
        } catch (RuntimeException e) {
            throw new DataAccessException("Database creation failed", e);
        }
        logger.info("Successfully created empty database directory at path: {}", databasePath);
    }

    /**
     * creates an empty Dataset file
     *
     * @param datasetName Name of the Dataset
     */
    public void createDataset(String datasetName) {
        if (FileDatabaseReadWriter.listDatasets(this.databasePath, lang).contains(datasetName)) {
            logger.debug("Dataset {} already exists", datasetName);
            return;
        }
        FileDatabaseReadWriter.createDataset(this.databasePath, datasetName, lang);
    }

    /**
     * Writes a {@link Graph} to a file, if the Graph already exists in the {@link Dataset} it will be overwritten
     *
     * @param graph       {@link Graph}
     * @param datasetName Name of the Dataset
     * @param graphName   Name of the graph, null or "" for default
     */
    public void write(Graph graph, String datasetName, String graphName) {
        write(ModelFactory.createModelForGraph(graph), datasetName, graphName);
    }

    /**
     * Writes a {@link Model} to a file, if the Model already exists in the {@link Dataset} it will be overwritten
     *
     * @param model       {@link Model}
     * @param datasetName Name of the Dataset
     * @param graphName   Name of the graph, null or "" for default
     */
    public void write(Model model, String datasetName, String graphName) {
        this.createDatabase();
        this.createDataset(datasetName);

        logger.info("Storing graph  \"{}\" in file database at {}/{}.{} ...", graphName, databasePath, datasetName, lang.getFileExtensions().getFirst());
        Dataset dataset = FileDatabaseReadWriter.readDataset(databasePath, datasetName, lang);
        if (graphName == null || graphName.isEmpty()) { //add default model
            dataset.getDefaultModel().removeAll();
            dataset.getDefaultModel().add(model);
        } else {
            if (dataset.containsNamedModel(graphName)) {
                dataset.removeNamedModel(graphName);
            }
            dataset.addNamedModel(graphName, model);
        }
        dataset.getPrefixMapping().setNsPrefixes(model.getNsPrefixMap());

        try {
            FileDatabaseReadWriter.writeToFile(dataset, this.databasePath, datasetName, lang);
            logger.info("Storing graph \"{}\" in file database at: \"{}/{}.{}\" successful", graphName, databasePath,
                        datasetName, lang.getFileExtensions().getFirst());
        } catch (RuntimeException e) {
            throw new DataAccessException("Storing graph \"" + graphName + "\" in the file database at: " +
                                                    databasePath + "/" + datasetName + "." + lang.getFileExtensions().getFirst() + " failed", e);
        }
    }

    /**
     * Writes a {@link Dataset} to a file
     *
     * @param dataset     {@link Dataset}
     * @param datasetName Name of the Dataset
     */
    public void write(Dataset dataset, String datasetName) {
        FileDatabaseReadWriter.writeToFile(dataset, this.databasePath, datasetName, lang);
    }

    /**
     * returns a dataset by name
     *
     * @param datasetName Name of the dataset
     *
     * @return dataset
     */
    public Dataset getDataset(String datasetName) {
        return FileDatabaseReadWriter.readDataset(databasePath, datasetName, lang);
    }

    /**
     * returns a Graph from a dataset as {@link Model}
     *
     * @param datasetName name of the dataset
     * @param graphName   name of the graph, set to null or "" for default
     *
     * @return a model or null if non existent
     */
    public Model getGraphAsModel(String datasetName, String graphName) {
        Dataset ds = FileDatabaseReadWriter.readDataset(this.databasePath, datasetName, lang);
        if (graphName == null || graphName.isEmpty()) {
            return ds.getDefaultModel();
        }
        if (ds.containsNamedModel(graphName)) {
            return ds.getNamedModel(graphName);
        }
        throw new DataAccessException("No model named: \"" + graphName + "\"");
    }

    public void deleteDataset(String datasetName) {
        FileDatabaseReadWriter.deleteDataset(databasePath, datasetName, lang);
    }

    public List<String> listDatasets() {
        return FileDatabaseReadWriter.listDatasets(databasePath, lang);
    }
}

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

import org.apache.jena.query.Dataset;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.rdfarchitect.exception.database.DataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

public class FileDatabaseReadWriter {

    private static final Logger logger = LoggerFactory.getLogger(FileDatabaseReadWriter.class);

    private FileDatabaseReadWriter() {}

    /**
     * Resolves the path of a dataset file inside the database directory and ensures it cannot
     * escape the database directory via path traversal.
     */
    private static Path resolveDatasetPath(Path pathDatabase, String datasetName, Lang lang) {
        if (datasetName == null || datasetName.isEmpty()) {
            throw new DataAccessException("Dataset name must not be empty.");
        }
        if (datasetName.contains("/")
                || datasetName.contains("\\")
                || datasetName.contains("\0")
                || datasetName.equals(".")
                || datasetName.equals("..")) {
            throw new DataAccessException("Invalid dataset name: " + datasetName);
        }
        Path baseDir = pathDatabase.toAbsolutePath().normalize();
        Path resolved =
                baseDir.resolve(datasetName + "." + lang.getFileExtensions().get(0)).normalize();
        Path parent = resolved.getParent();
        if (!resolved.startsWith(baseDir) || parent == null || !parent.equals(baseDir)) {
            throw new DataAccessException("Invalid dataset name: " + datasetName);
        }
        return resolved;
    }

    /**
     * created a Database directory if it doesn't exist yet
     *
     * @param pathDatabase path where to create the Directory
     */
    public static void createDatabase(Path pathDatabase) {
        try {
            Files.createDirectory(pathDatabase);
        } catch (IOException e) {
            throw new DataAccessException("Failed to create database directory.", e);
        }
    }

    /**
     * checks whether a dataset exists at a given path
     *
     * @param pathDatabase Path of the database directory
     * @param datasetName Name of the dataset
     * @param lang Lang the dataset is stored in
     * @return true if it exists, otherwise false
     */
    public static boolean datasetExists(Path pathDatabase, String datasetName, Lang lang) {
        Path pathDataset = resolveDatasetPath(pathDatabase, datasetName, lang);
        if (!Files.exists(pathDataset)) {
            logger.debug("dataset file \"{}\" doesnt exist", pathDataset);
            return false;
        }
        return true;
    }

    /**
     * writes a dataset to a file
     *
     * @param dataset to be written to the file
     * @param pathDatabase path of the db directory
     * @param datasetName name of the dataset
     * @param lang Lang the dataset should be stored in
     */
    public static void writeToFile(
            Dataset dataset, Path pathDatabase, String datasetName, Lang lang) {
        Path pathDataset = resolveDatasetPath(pathDatabase, datasetName, lang);
        try {
            OutputStream out = Files.newOutputStream(pathDataset);
            RDFDataMgr.write(out, dataset, lang);
        } catch (IOException e) {
            throw new DataAccessException("Error writing dataset to File: " + pathDataset, e);
        }
    }

    /**
     * reads a dataset from a given path
     *
     * @param pathDatabase Path of the db directory
     * @param datasetName Name of the Dataset
     * @param lang Lang the dataset is stored in
     * @return the Dataset
     */
    public static Dataset readDataset(Path pathDatabase, String datasetName, Lang lang) {
        Path pathDataset = resolveDatasetPath(pathDatabase, datasetName, lang);
        try {
            return RDFDataMgr.loadDataset(pathDataset.toString(), lang);
        } catch (Exception e) {
            throw new DataAccessException("Error reading dataset from File: " + pathDataset, e);
        }
    }

    /**
     * Checks if the database directory exists.
     *
     * @return true if the database directory can be found, otherwise false.
     */
    public static boolean ping(Path databasePath) {
        return Files.isDirectory(databasePath);
    }

    /**
     * Lists the names of all datasets in the Database:
     *
     * @param databasePath Path where the database is located
     * @return A List containing the names of all datasets
     */
    public static List<String> listDatasets(Path databasePath, Lang lang) {
        try (Stream<Path> files = Files.list(databasePath)) {
            return files.filter(path -> !Files.isDirectory(path))
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .filter(name -> name.endsWith(lang.getFileExtensions().get(0)))
                    .map(
                            name ->
                                    name.substring(
                                            0,
                                            name.length()
                                                    - lang.getFileExtensions().get(0).length()))
                    .toList();
        } catch (IOException e) {
            throw new DataAccessException("Error listing datasets", e);
        }
    }

    /**
     * Creates an empty dataset in a database.
     *
     * @param databasePath Path where the database is located
     * @param lang Lang the dataset will be stored in
     * @param datasetName Name of the dataset to be created
     */
    public static void createDataset(Path databasePath, String datasetName, Lang lang) {
        Path datasetPath = resolveDatasetPath(databasePath, datasetName, lang);
        try {
            Files.createFile(datasetPath);
            logger.info("Successfully created dataset {}", datasetPath);
        } catch (IOException e) {
            throw new DataAccessException("Error creating dataset \"" + datasetName + "\"", e);
        }
    }

    /**
     * Deletes a dataset in a database.
     *
     * @param databasePath Path where the database is located
     * @param lang Lang of the dataset to be deleted
     * @param datasetName Name of the dataset to be deleted
     */
    public static void deleteDataset(Path databasePath, String datasetName, Lang lang) {
        Path path = resolveDatasetPath(databasePath, datasetName, lang);
        try {
            Files.delete(path);
        } catch (IOException e) {
            throw new DataAccessException("Error deleting dataset \"" + datasetName + "\"", e);
        }
    }
}

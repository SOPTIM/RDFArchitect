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

package org.rdfarchitect.database.implementations;

import java.util.List;

/**
 * Defines how to manage datasets in a database and provide other database utilities.
 */
public interface DatabaseAdminProtocol {

    /**
     * Checks if the database is accessible.
     *
     * @return true if the database is alive, otherwise false
     */
    boolean ping();

    /**
     * Lists the names of all datasets in the Database:
     *
     * @return A List containing the names of all datasets
     */
    List<String> listDatasets();

    /**
     * Creates an empty dataset in a database.
     *
     * @param datasetName Name of the dataset to be created
     */
    void createDataset(String datasetName);

    /**
     * Deletes a dataset in a database.
     *
     * @param datasetName Name of the dataset to be deleted
     */
    void deleteDataset(String datasetName);
}
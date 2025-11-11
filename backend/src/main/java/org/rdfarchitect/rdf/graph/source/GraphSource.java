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

package org.rdfarchitect.rdf.graph.source;

import org.apache.jena.graph.Graph;
import org.rdfarchitect.exception.database.DataAccessException;

/**
 * Defines how the graph can be imported
 */
public interface GraphSource {

    /**
     * Gets the graph from a source (file, variable, etc...)
     *
     * @return imported graph
     */
    Graph graph() throws DataAccessException;

    /**
     * @return the name of the graph of this {@link GraphSource}
     */
    String graphName();

    /**
     * Gets the type of source the Graph is from, e.g.: file, db, variable, etc.
     *
     * @return the source type of the graph
     */
    String getGraphSourceType();
}

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

package org.rdfarchitect.rdf.graph.source.builder;

import org.rdfarchitect.exception.database.DataAccessException;
import org.rdfarchitect.rdf.graph.source.GraphSource;

/**
 * Defines Builder for {@link GraphSource}
 */
public interface GraphSourceBuilder {

    /**
     * Sets the name of the graph.
     *
     * @param graphName name of the graph, set to {@code null} or {@code ""} for default
     *
     * @return This {@link GraphSourceBuilder object} with the name set.
     */
    GraphSourceBuilder setGraphName(String graphName);

    /**
     * Builds a {@link GraphSource} from a source (e.g. file)
     *
     * @return The built {@link GraphSource}
     */
    GraphSource build() throws DataAccessException;
}

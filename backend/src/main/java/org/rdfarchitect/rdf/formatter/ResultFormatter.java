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

package org.rdfarchitect.rdf.formatter;

import org.apache.jena.graph.Graph;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;

/**
 * Formats an Object to different representations like {@link ResultSet}, {@link Graph}, {@link
 * Model} or {@link String}.
 */
public interface ResultFormatter {

    /**
     * Returns a {@link ResultSet} representation of an object, like e.g. a {@link ResultSet}.
     *
     * @return {@link ResultSet}
     */
    ResultSet asResultSet();

    /**
     * Returns a {@link Graph} representation of an object, like e.g. a {@link ResultSet}.
     *
     * @return {@link Graph}
     */
    Graph asGraph();

    /**
     * Returns a textual representation of an object, like e.g. a {@link ResultSet}.
     *
     * @return {@link String}
     */
    String asText();

    /**
     * Returns a {@link Model} representation of an object, like e.g. a {@link ResultSet}.
     *
     * @return {@link Model}
     */
    Model asModel();
}

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

package org.rdfarchitect.services.rendering;

import org.apache.jena.graph.Graph;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.models.cim.data.dto.CIMCollection;
import org.rdfarchitect.models.cim.rendering.GraphFilter;

/** Converts a Graph to a {@link CIMCollection}. */
public interface GraphToCIMCollectionConverterUseCase {

    /**
     * Opens a READ transaction, copies the RDF graph, and converts it to a {@link CIMCollection}.
     */
    CIMCollection convert(GraphIdentifier graphIdentifier, GraphFilter filter);

    /**
     * Converts an already-copied RDF graph to a {@link CIMCollection} without opening a
     * transaction. Use this when the caller already holds a transaction and has deep-copied the
     * graph.
     */
    CIMCollection convert(Graph copiedGraph, GraphIdentifier graphIdentifier, GraphFilter filter);
}

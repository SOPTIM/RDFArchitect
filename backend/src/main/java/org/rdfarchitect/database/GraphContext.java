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

package org.rdfarchitect.database;

import org.apache.jena.graph.Graph;
import org.apache.jena.query.ReadWrite;
import org.rdfarchitect.database.inmemory.diagrams.CustomDiagram;
import org.rdfarchitect.models.changelog.ChangeLog;
import org.rdfarchitect.rdf.graph.wrapper.DiagramLayoutDelta;
import org.rdfarchitect.rdf.graph.wrapper.RDFGraphDelta;

import java.util.Map;
import java.util.UUID;

/**
 * Transactional access to the RDF graph, diagram layout, and custom SHACL data for a single named
 * graph. Extends {@link Transactional} so it can be used in try-with-resources.
 */
public interface GraphContext extends Transactional, VersionControl {

    @Override
    GraphContext begin(ReadWrite mode);

    Graph getRdfGraph();

    DiagramLayoutDelta getDiagramLayout();

    RDFGraphDelta getCustomSHACL();

    ChangeLog getChangeLog();

    Map<UUID, CustomDiagram> getCustomDiagrams();
}

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

package org.rdfarchitect.services.update.graph;

import org.rdfarchitect.database.GraphIdentifier;

public interface RenameGraphUseCase {

    /**
     * Renames the graph addressed by {@code graphIdentifier} to {@code newGraphUri}. The content
     * and the history of the graph are kept, references to it are rewritten.
     *
     * <p>If {@code newName} is given, it is written where the profile keeps the name it is listed
     * under: {@code dcterms:title} on the ontology object, or {@code rdfs:label} on the profile's
     * package for a CGMES 2.4.15 profile, which has no ontology object. A graph that is no profile
     * has nowhere to keep a name and is left alone — the tail of its URI names it. Should the write
     * fail, the rename is rolled back.
     *
     * @param graphIdentifier identifies dataset and current graph URI
     * @param newGraphUri the graph URI to rename to
     * @param newName the name to show the schema under, or {@code null} to leave it as it is
     */
    void renameGraph(GraphIdentifier graphIdentifier, String newGraphUri, String newName);
}

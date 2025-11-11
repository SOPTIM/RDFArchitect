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

package org.rdfarchitect.services.shacl;

import org.apache.jena.riot.system.PrefixEntry;
import org.rdfarchitect.database.GraphIdentifier;

public interface SHACLGenerateUseCase {

    /**
     * Generate SHACL graph for a given graph identifier.
     *
     * @param graphIdentifier The identifier of the graph to generate SHACL for.
     * @param shaclPrefix The prefix entry to use for SHACL shapes.
     * @return A String containing the exported SHACL graph.
     */
    String exportGeneratedSHACLGraph(GraphIdentifier graphIdentifier, PrefixEntry shaclPrefix);
}

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

import org.rdfarchitect.database.GraphIdentifier;

public interface SHACLReplaceShapeUseCase {

    /**
     * Deletes a SHACL shape from a graph and inserts SHACL coded as Turtle. Prefix changes are not adopted.
     *
     * @param graphIdentifier the identifier of the graph
     * @param shaclShapeURI the uri of the SHACL shape
     * @param shaclToInsert the shacl to insert
     */
    void replaceSHACLShape(GraphIdentifier graphIdentifier, String shaclShapeURI, String shaclToInsert);
}

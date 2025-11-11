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
import org.rdfarchitect.shacl.dto.CustomAndGeneratedTuple;
import org.rdfarchitect.shacl.dto.NodeShape;
import org.rdfarchitect.shacl.dto.PropertyShape;

import java.util.List;
import java.util.UUID;

public interface SHACLGetShapeUseCase {
    /**
     * Get SHACL shapes related to an attribute.
     * @param graphIdentifier Identifier to specify, which graph the attribute is in.
     * @param attributeUUID The uuid of the attribute to get SHACL shapes for.
     * @return A List of {@link PropertyShape} objects containing the string representation of the SHACL shapes related to the attribute.
     */
    CustomAndGeneratedTuple<List<PropertyShape>> getPropertyShapesForAttribute(GraphIdentifier graphIdentifier, UUID attributeUUID);

    /**
     * Get SHACL shapes related to an association.
     * @param graphIdentifier Identifier to specify, which graph the association is in.
     * @param associationUUID The UUID of the attribute to get SHACL shapes for.
     * @return A List of {@link PropertyShape} objects containing the string representation of the SHACL shapes related to the association.
     */
    CustomAndGeneratedTuple<List<PropertyShape>> getPropertyShapesForAssociation(GraphIdentifier graphIdentifier, UUID associationUUID);

    /**
     * Get SHACL node shapes related to a class.
     * @param graphIdentifier Identifier to specify, which graph the class is in.
     * @param classUUID The uuid of the class to get SHACL node shapes for.
     * @return A List of {@link NodeShape} objects containing the string representation of the SHACL node shapes related to the class.
     */
    CustomAndGeneratedTuple<List<NodeShape>> getNodeShapesForClass(GraphIdentifier graphIdentifier, UUID classUUID);
}

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

package org.rdfarchitect.shacl.dto;

import lombok.Data;

/** A single change the form makes to a document's text. */
@Data
public class ShapeEditRequest {

    /** The document as it currently stands, which the edit is applied to. */
    private String turtle;

    /** The shape to write. Its {@code iri} says which statement is replaced or added. */
    private NodeShapeModel shape;

    /** Set instead of {@link #shape} to delete a shape by IRI. */
    private String removeShapeIri;

    /**
     * Set instead of {@link #shape} to change a rule the document writes as a shape of its own.
     *
     * <p>Its {@code iri} says which statement is rewritten. A shared rule is edited on itself
     * rather than through a shape referencing it, because the two are different requests: changing
     * the rule changes it for every shape that uses it, which is exactly what {@link #getSplit()}
     * exists to offer a way out of.
     */
    private PropertyShapeModel propertyShape;

    /** With {@link #propertyShape}: copy the rule first, and change the copy instead. */
    private PropertyShapeSplit split;
}

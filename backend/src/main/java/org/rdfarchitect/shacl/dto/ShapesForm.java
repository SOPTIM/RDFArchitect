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

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * A constraints document seen as shapes rather than as text, so it can be edited by people who do
 * not read Turtle.
 */
@Data
@Builder
public class ShapesForm {

    private List<NodeShapeModel> shapes;

    /**
     * The rules the document writes as shapes of their own, rather than inside a node shape.
     *
     * <p>A second list rather than more entries in {@link #shapes}, because a property shape is not
     * a node shape and answers a different question: a node shape says which class is constrained,
     * a property shape says what one property has to look like, and the official {@code
     * -Con-Simple-} profiles put every constraint they have in one. Each also appears inline under
     * the node shapes that reference it, so the same rule can be reached from either direction.
     */
    private List<PropertyShapeModel> propertyShapes;

    /** A syntax error, when the text does not parse. The form then has nothing to show. */
    private ShapesValidationFinding parseError;
}

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

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Builder(builderClassName = "PropertyShapesWrapperBuilder")
@Data
public class PropertyShapesWrapper {

    private UUID domain;

    private String label;

    private String propertyType;

    private List<PropertyShape> propertyShapes;

    /**
     * What these shapes require between them, in words — {@code "0..1, xsd:float"}.
     *
     * <p>The conjunction of every shape in the wrapper, because that is what SHACL means by having
     * several of them. It is here so a reader can see the rule without expanding the Turtle, which
     * is the whole reason the class dialog exists. Empty when the shapes state nothing this can
     * summarise, such as a SPARQL constraint.
     */
    private String summary;

    // Custom builder to allow custom setter
    public static class PropertyShapesWrapperBuilder {
        public PropertyShapesWrapperBuilder propertyShapes(List<PropertyShape> propertyShapes) {
            if (propertyShapes != null) {
                propertyShapes.sort(Comparator.comparing(PropertyShape::getOrder));
            }
            this.propertyShapes = propertyShapes;
            return this;
        }
    }
}

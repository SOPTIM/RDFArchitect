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

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** One shape targeting a class, with the property constraints written under it. */
@Data
@Builder
// The form sends these back to be applied, so Jackson has to construct them as well as read
// them. Lombok's @Jacksonized is no help: it emits Jackson 2 annotations, and Spring Boot 4
// binds with Jackson 3, which ignores them.
@NoArgsConstructor
@AllArgsConstructor
public class NodeShapeModel {

    private String iri;

    private String targetClass;

    private Boolean closed;

    private List<String> ignoredProperties;

    private String name;

    private String description;

    private String severity;

    private String message;

    private Boolean deactivated;

    private List<PropertyShapeModel> properties;

    /**
     * Predicates the form does not represent, such as {@code sh:or} or {@code sh:sparql}.
     *
     * <p>Listed rather than ignored because writing the shape back from the form would drop them. A
     * shape with any of these is shown read-only, and the Turtle view is where it is edited.
     */
    private List<String> unsupported;

    /**
     * Whether the form may write this shape back. False when it is not fully represented.
     *
     * <p>Boxed on purpose: the shape travels back to be applied, and Jackson refuses to map an
     * absent JSON member onto a primitive.
     */
    private Boolean editable;

    /**
     * Why the form will not write this shape back, in words, or {@code null} when it will.
     *
     * <p>{@link #unsupported} names the predicates, which answers "which part?" but not "why can I
     * not edit this?" — a shape can be read-only for something no predicate list shows, such as
     * being a shape SHACL infers rather than one the document types.
     */
    private String readOnlyReason;
}

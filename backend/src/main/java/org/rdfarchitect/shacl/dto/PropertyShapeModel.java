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

/**
 * One constraint on one property of a class, in the form a form can show and edit.
 *
 * <p>Every field is optional, because SHACL says nothing about a property unless a constraint is
 * written; an absent {@code minCount} means "no lower bound stated", not zero.
 */
@Data
@Builder
// The form sends these back to be applied, so Jackson has to construct them as well as read
// them. Lombok's @Jacksonized is no help: it emits Jackson 2 annotations, and Spring Boot 4
// binds with Jackson 3, which ignores them.
@NoArgsConstructor
@AllArgsConstructor
public class PropertyShapeModel {

    /** Set when the property shape is written as a named resource rather than inline. */
    private String iri;

    /** {@code sh:path}, always a plain IRI here — a path expression is not form-editable. */
    private String path;

    private String name;

    private String description;

    private String dataType;

    /** {@code sh:class}: the class a value must belong to. */
    private String classIri;

    private String nodeKind;

    private Integer minCount;

    private Integer maxCount;

    /** {@code sh:in}: the closed list of values allowed, as written. */
    private List<String> allowedValues;

    private String pattern;

    private String severity;

    private String message;

    private Integer order;

    private String group;

    private Boolean deactivated;

    /**
     * Whether the document states {@code a sh:PropertyShape} on this shape.
     *
     * <p>Carried so a rewrite puts it back. Most property shapes leave the type implicit, so the
     * writer cannot simply always state it, and dropping it would edit a line nobody asked it to.
     */
    private Boolean typed;
}

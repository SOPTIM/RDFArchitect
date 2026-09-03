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

    /**
     * The 1-based line the document writes this shape on, or {@code null} where it writes none.
     *
     * <p>Carried for two things at once: the form lists shapes in the order the document writes
     * them rather than by IRI — an official profile's order is the one its author chose — and a
     * card can send the reader to the text it was read from.
     */
    private Integer line;

    /**
     * The classes the shape applies to, in the order the document writes them.
     *
     * <p>A list because SHACL lets a shape target several — {@code sh:targetClass
     * cim:AsynchronousMachine , cim:SynchronousMachine} is one clause with two classes, and 462
     * shapes in the official library are written that way. Holding one of them made every one of
     * those shapes read-only, because writing the shape back would have dropped the others.
     */
    private List<String> targetClasses;

    /**
     * {@code sh:targetSubjectsOf}: the shape applies to whatever states one of these predicates.
     *
     * <p>One list per kind of target rather than a kind-and-value pair, because SHACL lets a shape
     * carry several kinds at once and each is its own predicate. The form shows them as rows of
     * kind plus value, which is the same thing read the other way round.
     */
    private List<String> targetSubjectsOf;

    /** {@code sh:targetObjectsOf}: the shape applies to whatever is the object of one of these. */
    private List<String> targetObjectsOf;

    /** {@code sh:targetNode}: the shape applies to these resources and no others. */
    private List<String> targetNodes;

    private Boolean closed;

    private List<String> ignoredProperties;

    private String name;

    private String description;

    private String severity;

    private String message;

    private Boolean deactivated;

    private List<PropertyShapeModel> properties;

    /**
     * What this shape says that the form shows but never rewrites.
     *
     * <p>A clause the form has no field for, and a field whose value it could not reproduce, are
     * both kept exactly as the document wrote them. Listing them is what made shapes editable that
     * used to be locked over a single clause: the form now says "these two lines stay as they are"
     * instead of "this shape is Turtle only".
     */
    private List<RetainedClause> retained;

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
     * <p>{@link #retained} names what the form is keeping as written, which answers "which part?"
     * but not "why can I not edit this at all?" — after clause-preserving edits a shape is
     * read-only only for something no clause list shows, such as being written as two statements.
     */
    private String readOnlyReason;
}

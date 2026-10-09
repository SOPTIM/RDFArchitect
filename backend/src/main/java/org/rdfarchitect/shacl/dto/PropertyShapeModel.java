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

    /** The 1-based line the document writes this rule on. See {@link NodeShapeModel#getLine()}. */
    private Integer line;

    /**
     * Where the document writes this rule, so an edit can find it again. Opaque to the form.
     *
     * <p>A rule has no name of its own to be identified by — most are blank nodes — and its path
     * cannot serve as one, because the path is itself editable. So the reader records which {@code
     * sh:property} of the shape a rule came from and the form hands it back untouched. {@code null}
     * means a rule the form has just added, which is written rather than found.
     */
    private Integer sourceIndex;

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

    /**
     * {@code sh:minInclusive} and its three companions, as the number the document writes.
     *
     * <p>The lexical form on its own, not a Java number: every value range in the official library
     * is written {@code "0.0"^^xsd:float}, and a form that read that as a {@code double} would
     * write it back as a bare decimal — changing a datatype nobody asked it to touch. The writer
     * replaces the number inside the literal the document already has and leaves the rest of it
     * alone, so the datatype is preserved by never being handled.
     */
    private String minInclusive;

    private String maxInclusive;

    private String minExclusive;

    private String maxExclusive;

    private Integer minLength;

    private Integer maxLength;

    /** {@code sh:in}: the closed list of values allowed, as written. */
    private List<String> allowedValues;

    /** {@code sh:hasValue}: the one value allowed, an IRI or a plain string. */
    private String hasValue;

    private String pattern;

    /** {@code sh:flags}: how {@link #pattern} is matched, e.g. {@code i} for case-insensitive. */
    private String flags;

    private String severity;

    private String message;

    /**
     * {@code sh:order}, as the number the document writes rather than as an integer.
     *
     * <p>Held the same way as the value ranges above, and for a sharper reason: {@code sh:order
     * 0.1} on one shared rule of the official library used to make every node shape referencing it
     * read-only, over an ordering hint the form did not even display.
     */
    private String order;

    private String group;

    private Boolean deactivated;

    /**
     * Whether the document states {@code a sh:PropertyShape} on this shape.
     *
     * <p>Carried so a rewrite puts it back. Most property shapes leave the type implicit, so the
     * writer cannot simply always state it, and dropping it would edit a line nobody asked it to.
     */
    private Boolean typed;

    /**
     * What this rule says that the form shows but never rewrites.
     *
     * <p>The rule-level half of clause preservation: {@code sh:minInclusive}, an embedded query, or
     * an {@code sh:order 0.1} the form cannot spell as an integer are kept as the document wrote
     * them, and the rest of the rule stays editable. See {@link NodeShapeModel#getRetained()}.
     */
    private List<RetainedClause> retained;

    /**
     * The node shapes that state {@code sh:property} on this rule, when it has a name of its own.
     *
     * <p>Empty for an inline rule, which by construction belongs to the one shape it is written in.
     * A named rule is shared, and the number of shapes sharing it is the thing the user has to know
     * before changing it: in the official {@code -Con-Simple-} profiles one rule commonly carries
     * the cardinality of forty classes at once.
     */
    private List<String> usedBy;

    /**
     * Whether the form may write this rule back. False when it cannot place an edit in it.
     *
     * <p>Locking is per rule rather than per shape: a rule the form cannot find in the text, or one
     * of two rules under a shape that say exactly the same thing, is shown with what it says while
     * the rest of the shape stays editable.
     *
     * <p>Boxed like {@link NodeShapeModel#getEditable()}, and for the same reason — the rule
     * travels back to be applied, and Jackson cannot map an absent JSON member onto a primitive.
     */
    private Boolean editable;

    /** Why the form will not write this rule back, in words, or {@code null} when it will. */
    private String readOnlyReason;
}

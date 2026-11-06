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

/**
 * Something a shape says that the form shows but never rewrites.
 *
 * <p>This is what replaced locking a whole shape over one clause. A form edit changes the clause it
 * was made on and copies every other clause through character for character, so a {@code sh:sparql}
 * query, an {@code sh:order 0.1} or a message with a language tag no longer stop the rest of the
 * shape from being edited — they are listed here instead, with the text the document gave them.
 *
 * <p>Sent to the form so it can say what it is keeping, and so a field it cannot write can be shown
 * with the value the document actually holds rather than a rounded-off version of it.
 */
@Data
@Builder
// Travels back with an edited shape, so Jackson has to construct these as well as read them.
// Lombok's @Jacksonized is no help: it emits Jackson 2 annotations, and Spring Boot 4 binds with
// Jackson 3, which ignores them.
@NoArgsConstructor
@AllArgsConstructor
public class RetainedClause {

    /** The predicate, as an IRI when one resolves, else the token as the document writes it. */
    private String predicate;

    /** The clause's object list, exactly as the document writes it. */
    private String value;

    /**
     * The form field this clause fills, or {@code null} when the form has no field for it.
     *
     * <p>Named the way the field is named on the model — {@code minCount}, {@code targetClasses} —
     * so the form can show that one field as read-only without a second mapping to keep in step.
     */
    private String field;

    /** Why the form will not write this clause back, in words, for the field that shows it. */
    private String reason;
}

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
import java.util.UUID;

/** What a workspace's schema knows about one term, for an editor to show on hover. */
@Data
@Builder
public class SchemaTermDetail {

    private SchemaTerm.Kind kind;

    private String iri;

    private String namespace;

    private String localName;

    private String label;

    /** The term's {@code rdfs:comment}, which is the part a reader actually wants. */
    private String comment;

    /** Classes a property may be used on. Empty for a class or an enum member. */
    private List<String> domains;

    /** What a property's value may be. Empty for a class or an enum member. */
    private List<String> ranges;

    /** The CIM multiplicity as written, e.g. {@code 1..1} or {@code 0..n}. */
    private String multiplicity;

    private Integer minCount;

    /** {@code null} when the multiplicity is unbounded. */
    private Integer maxCount;

    /** Every profile in the workspace that declares the term. */
    private List<String> profiles;

    /**
     * The graph holding the class this term belongs to, or {@code null} when the workspace has no
     * editable class for it.
     *
     * <p>Present so that following a term from a constraints document lands on the class it
     * constrains. For a property that is the class the property is on, which is the useful
     * destination — a property is edited as a row of its class, not on its own.
     */
    private String graphUri;

    /** The class's id in {@link #graphUri}, or {@code null} when there is none to open. */
    private UUID classUUID;
}

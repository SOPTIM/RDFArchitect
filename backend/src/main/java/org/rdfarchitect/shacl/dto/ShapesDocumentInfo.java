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

import org.rdfarchitect.database.ShapesDocument;

import java.util.Set;
import java.util.UUID;

/**
 * A shapes document as the API describes it, without its content.
 *
 * <p>Carries no precedence or priority: SHACL is conjunctive, so every enabled document applies and
 * none overrides another. {@link #order} decides list position and serialisation order only.
 */
@Data
@Builder
public class ShapesDocumentInfo {

    private UUID id;

    private String name;

    /** File the document was uploaded from, or {@code null} when it was authored here. */
    private String sourceFileName;

    private ShapesDocument.Origin origin;

    /** Whether the shapes take part in validation and combined export. */
    private boolean enabled;

    private int order;

    /** URIs of the schemas it applies to. A URI the workspace no longer holds counts as unset. */
    private Set<String> schemas;

    /** Number of triples the document holds, so the UI can show an empty document as empty. */
    private long tripleCount;

    /** Node shapes the document declares — what it applies to. */
    private int nodeShapeCount;

    /** Property shapes, named and inline — the rules on individual properties. */
    private int propertyShapeCount;

    /** SPARQL constraints, which neither of the shape counts says anything about. */
    private int sparqlConstraintCount;
}

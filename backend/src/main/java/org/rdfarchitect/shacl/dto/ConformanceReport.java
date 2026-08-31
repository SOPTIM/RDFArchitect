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

/**
 * Whether a constraints document still agrees with the schema it describes.
 *
 * <p>The question only RDFArchitect can answer, because only it holds both: the schema being edited
 * and the constraints file that was imported alongside it.
 */
@Data
@Builder
public class ConformanceReport {

    private UUID documentId;

    private String documentName;

    /**
     * True when nothing the two both state disagrees, and the documents state nothing the schema
     * does not have.
     *
     * <p>Coverage is deliberately not part of this. A constraints file that says nothing about a
     * property does not disagree with the schema about it — official releases split their rules
     * across several files, and some of those files carry a single cross-profile rule.
     */
    private boolean conforms;

    /** Of {@link #compared}, how many say the same thing. */
    private int agreeing;

    /**
     * Property constraints <em>both</em> sides state — the only ones agreement is a question about.
     */
    private int compared;

    /** Every property constraint the schema implies, whether or not a document states it. */
    private int impliedBySchema;

    /** Every property constraint the documents state, whether or not the schema implies it. */
    private int stated;

    /** The documents the comparison read, in reading order. */
    private List<String> documents;

    private int contradictedCount;

    private int differentCount;

    private int missingInDocumentCount;

    private int notInSchemaCount;

    private List<ConformanceFinding> findings;
}

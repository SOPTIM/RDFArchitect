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

import java.util.UUID;

/**
 * Where a shape came from.
 *
 * <p>Shapes reach a reader merged from every enabled document, and the merge is what makes them
 * answerable at all — official constraints are split across files on purpose. But a reader who
 * wants to change a rule needs the file back, and merging is exactly what throws that away.
 *
 * <p>A shape normally has one origin. It has more than one when two documents state triples about
 * the same subject, which is legal and worth seeing rather than hiding behind whichever document
 * happened to be read first.
 */
@Builder
@Data
public class ShapeOrigin {

    private UUID documentId;

    private String documentName;

    /**
     * The 1-based line the shape starts on in that document, or {@code null} when it cannot be
     * found — a document restored from a snapshot carries triples but no text to count lines in.
     */
    private Integer line;
}

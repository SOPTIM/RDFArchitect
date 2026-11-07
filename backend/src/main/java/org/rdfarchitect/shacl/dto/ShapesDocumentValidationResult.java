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

/** What validating one set of SHACL shapes found, with the counts a list badge needs. */
@Data
@Builder
public class ShapesDocumentValidationResult {

    /** The document validated, or {@code null} when Turtle was posted rather than stored. */
    private UUID documentId;

    private String documentName;

    /** Whether the document produced no error. Warnings and infos do not make it invalid. */
    private boolean valid;

    private int errorCount;

    private int warningCount;

    private int infoCount;

    private List<ShapesValidationFinding> findings;
}

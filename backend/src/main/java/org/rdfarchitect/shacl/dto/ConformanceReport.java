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

    /** True when nothing disagrees. */
    private boolean conforms;

    /** Property constraints the schema and the document both state and agree on. */
    private int agreeing;

    /** Property constraints compared in total, agreeing ones included. */
    private int compared;

    private int contradictedCount;

    private int differentCount;

    private int missingInDocumentCount;

    private int notInSchemaCount;

    private List<ConformanceFinding> findings;
}

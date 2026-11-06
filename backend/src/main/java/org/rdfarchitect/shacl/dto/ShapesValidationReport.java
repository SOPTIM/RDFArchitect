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

/**
 * The outcome of validating a graph's SHACL shapes against its workspace's CIM schema.
 *
 * <p>Documents are reported separately, never merged: a finding is only useful if it names the file
 * and line it is about. The totals are the sum over the documents.
 */
@Data
@Builder
public class ShapesValidationReport {

    /** Whether no document produced an error. */
    private boolean valid;

    private int errorCount;

    private int warningCount;

    private int infoCount;

    /**
     * Version IRIs of the profiles the shapes were checked against — every profile the workspace
     * holds. A {@code urn:rdfa:profile:} IRI is one RDFArchitect made up for a graph that declares
     * no {@code owl:versionIRI}.
     */
    private List<String> profiles;

    private List<ShapesDocumentValidationResult> documents;
}

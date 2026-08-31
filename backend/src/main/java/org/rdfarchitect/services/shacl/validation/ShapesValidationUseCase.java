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

package org.rdfarchitect.services.shacl.validation;

import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.shacl.dto.ShapesValidationReport;

import java.util.UUID;

/**
 * Checking a graph's SHACL shapes against the CIM schema of its workspace.
 *
 * <p>This answers "is this SHACL valid?" — do its classes and properties exist, do its
 * cardinalities agree with the schema's multiplicities, does the SPARQL embedded in it parse and
 * refer to real terms — and says where in the source each answer belongs. It has nothing to do with
 * validating instance data against the shapes, which RDFArchitect deliberately leaves to other
 * tools.
 */
public interface ShapesValidationUseCase {

    /**
     * Validates the graph's enabled shapes documents, or the one named by {@code documentId}.
     *
     * @param documentId a single document to validate, or {@code null} for all enabled ones. A
     *     document is validated when named explicitly even if it is disabled — the user asked.
     */
    ShapesValidationReport validateShapes(GraphIdentifier graphIdentifier, UUID documentId);

    /**
     * Validates Turtle that is not stored, in the context of {@code graphIdentifier}'s workspace.
     *
     * <p>This is what an editor calls while the user types, so unparseable text is expected and is
     * reported as a finding rather than refused.
     *
     * <p>The text is checked on its own: it is not compared with the graph's stored documents, so
     * contradictions between it and them are not reported here. Naming which document the text is
     * the unsaved version of would be needed first, or every shape IRI it shares with its own saved
     * copy would be reported as defined twice. {@link #validateShapes} answers the cross-document
     * question over what is actually stored.
     *
     * @param name what to call the text in the report
     */
    ShapesValidationReport validateTurtle(
            GraphIdentifier graphIdentifier, String name, String turtle);
}

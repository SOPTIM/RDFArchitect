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

package org.rdfarchitect.services.shacl.conformance;

import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.shacl.dto.ConformanceReport;

import java.util.UUID;

/**
 * Whether an imported constraints document still agrees with the schema it describes.
 *
 * <p>The question a modeller has after editing a profile: has my schema drifted away from the
 * official constraints that came with it? Nothing outside RDFArchitect can answer it, because
 * answering needs both the edited schema and the imported file at once.
 */
public interface ConformanceUseCase {

    /**
     * Compares the constraints the graph's schema implies with those a document asserts.
     *
     * @param documentId the constraints document to compare against
     */
    ConformanceReport compare(GraphIdentifier graphIdentifier, UUID documentId);
}

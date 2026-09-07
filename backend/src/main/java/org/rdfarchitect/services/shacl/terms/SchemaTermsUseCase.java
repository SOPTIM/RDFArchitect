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

package org.rdfarchitect.services.shacl.terms;

import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.shacl.dto.SchemaTermDetail;
import org.rdfarchitect.shacl.dto.SchemaTerms;

/**
 * The CIM terms a workspace's schema declares, for an editor to complete and explain.
 *
 * <p>Answers the same questions a language server would — "what can I write here?" and "what is
 * this?" — from the schema index that already backs shape validation. Deliberately not an LSP: the
 * knowledge lives in the index, and the index is in this process.
 */
public interface SchemaTermsUseCase {

    /** Every class, property and enum member of the workspace, for completion. */
    SchemaTerms listTerms(GraphIdentifier graphIdentifier);

    /**
     * What the schema knows about one term.
     *
     * @param iri the term's absolute IRI
     * @return the detail, or {@code null} when no profile in the workspace declares the term
     */
    SchemaTermDetail detailOf(GraphIdentifier graphIdentifier, String iri);
}

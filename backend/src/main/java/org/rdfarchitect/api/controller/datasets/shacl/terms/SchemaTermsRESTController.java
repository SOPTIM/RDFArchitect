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

package org.rdfarchitect.api.controller.datasets.shacl.terms;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import lombok.RequiredArgsConstructor;

import org.rdfarchitect.services.shacl.terms.SchemaTermsUseCase;
import org.rdfarchitect.shacl.dto.SchemaTermDetail;
import org.rdfarchitect.shacl.dto.SchemaTerms;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The CIM terms a constraints editor can complete and explain.
 *
 * <p>Answered from the whole workspace's schema, the same scope shape validation uses, because
 * official constraints reference terms across profiles on purpose.
 */
@RestController
@RequestMapping("api/datasets/{datasetName}/shacl/terms")
@RequiredArgsConstructor
public class SchemaTermsRESTController {

    private static final Logger logger = LoggerFactory.getLogger(SchemaTermsRESTController.class);

    private final SchemaTermsUseCase schemaTermsUseCase;

    @Operation(
            summary = "list the schema's terms",
            description =
                    "Every class, property and enum member the workspace's profiles declare, for an "
                            + "editor to complete against. Sent whole so the client can filter it "
                            + "locally; it changes only when the schema does.",
            tags = {"shacl"},
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = SchemaTerms.class)))
            })
    @GetMapping
    public SchemaTerms listTerms(
            @Parameter(description = "The name/url of the inquirer.")
                    @RequestHeader(
                            value = HttpHeaders.ORIGIN,
                            required = false,
                            defaultValue = "unknown")
                    String originURL,
            @Parameter(description = "The literal name of the dataset.") @PathVariable
                    String datasetName) {
        logger.info(
                "Received GET request: \"/api/datasets/{{}}/shacl/terms\" from \"{}\".",
                datasetName,
                originURL);

        return schemaTermsUseCase.listTerms(datasetName);
    }

    @Operation(
            summary = "describe one schema term",
            description =
                    "What the workspace's schema knows about a term: its label and comment, a "
                            + "property's domain, range and multiplicity, the profiles declaring it, "
                            + "and where its class can be opened for editing.",
            tags = {"shacl"},
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = SchemaTermDetail.class))),
                @ApiResponse(
                        responseCode = "204",
                        description = "No profile in this workspace declares the term.")
            })
    @GetMapping("/detail")
    public ResponseEntity<SchemaTermDetail> describeTerm(
            @Parameter(description = "The name/url of the inquirer.")
                    @RequestHeader(
                            value = HttpHeaders.ORIGIN,
                            required = false,
                            defaultValue = "unknown")
                    String originURL,
            @Parameter(description = "The literal name of the dataset.") @PathVariable
                    String datasetName,
            @Parameter(description = "The absolute IRI of the term.") @RequestParam String iri) {
        logger.debug(
                "Received GET request: \"/api/datasets/{{}}/shacl/terms/detail\" for \"{}\" from \"{}\".",
                datasetName,
                iri,
                originURL);

        // Most of what an editor hovers over is not a schema term, so "unknown" is an ordinary
        // answer rather than an error the browser would log for every one of them.
        var detail = schemaTermsUseCase.detailOf(datasetName, iri);
        return detail == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(detail);
    }
}

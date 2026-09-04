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

package org.rdfarchitect.api.controller.datasets.graphs.shacl.terms;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import lombok.RequiredArgsConstructor;

import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.exception.database.ResourceNotFoundException;
import org.rdfarchitect.services.ExpandURIUseCase;
import org.rdfarchitect.services.shacl.terms.SchemaTermsUseCase;
import org.rdfarchitect.shacl.dto.SchemaTermDetail;
import org.rdfarchitect.shacl.dto.SchemaTerms;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The CIM terms a constraints editor can complete and explain.
 *
 * <p>Scoped to a graph in the path because that is what an editor has in hand, but answered from
 * the whole workspace's schema — the same scope shape validation uses, and for the same reason:
 * official constraints reference terms across profiles on purpose.
 */
@RestController
@RequestMapping("api/datasets/{datasetName}/graphs/{graphURI}/shacl/terms")
@RequiredArgsConstructor
public class SchemaTermsRESTController {

    private static final Logger logger = LoggerFactory.getLogger(SchemaTermsRESTController.class);

    private final ExpandURIUseCase expandURIUseCase;

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
                    String datasetName,
            @Parameter(
                            description =
                                    "The url encoded uri of the graph, or \"default\" to access the default graph.")
                    @PathVariable
                    String graphURI) {
        logger.info(
                "Received GET request: \"/api/datasets/{{}}/graphs/{{}}/shacl/terms\" from \"{}\".",
                datasetName,
                graphURI,
                originURL);

        return schemaTermsUseCase.listTerms(graphIdentifier(datasetName, graphURI));
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
                                        schema = @Schema(implementation = SchemaTermDetail.class)))
            })
    @GetMapping("/detail")
    public SchemaTermDetail describeTerm(
            @Parameter(description = "The name/url of the inquirer.")
                    @RequestHeader(
                            value = HttpHeaders.ORIGIN,
                            required = false,
                            defaultValue = "unknown")
                    String originURL,
            @Parameter(description = "The literal name of the dataset.") @PathVariable
                    String datasetName,
            @Parameter(
                            description =
                                    "The url encoded uri of the graph, or \"default\" to access the default graph.")
                    @PathVariable
                    String graphURI,
            @Parameter(description = "The absolute IRI of the term.") @RequestParam String iri) {
        logger.debug(
                "Received GET request: \"/api/datasets/{{}}/graphs/{{}}/shacl/terms/detail\" for \"{}\" from \"{}\".",
                datasetName,
                graphURI,
                iri,
                originURL);

        var detail = schemaTermsUseCase.detailOf(graphIdentifier(datasetName, graphURI), iri);
        if (detail == null) {
            throw new ResourceNotFoundException(
                    "No profile in this workspace declares the term " + iri + ".");
        }
        return detail;
    }

    private GraphIdentifier graphIdentifier(String datasetName, String graphURI) {
        return new GraphIdentifier(datasetName, expandURIUseCase.expandUri(datasetName, graphURI));
    }
}

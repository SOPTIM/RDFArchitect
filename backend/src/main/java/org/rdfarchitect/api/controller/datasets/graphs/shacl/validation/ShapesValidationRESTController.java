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

package org.rdfarchitect.api.controller.datasets.graphs.shacl.validation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import lombok.RequiredArgsConstructor;

import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.services.ExpandURIUseCase;
import org.rdfarchitect.services.shacl.validation.ShapesValidationUseCase;
import org.rdfarchitect.shacl.dto.ShapesValidationReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Checking a graph's SHACL shapes against the CIM schema of its workspace.
 *
 * <p>{@code POST} rather than {@code GET} because validation is a computation over a body or over
 * the whole workspace's schema, not the retrieval of a stored resource, and because the text
 * variant needs a request body.
 */
@RestController
@RequestMapping("api/datasets/{datasetName}/graphs/{graphURI}/shacl/validate")
@RequiredArgsConstructor
public class ShapesValidationRESTController {

    private static final Logger logger =
            LoggerFactory.getLogger(ShapesValidationRESTController.class);

    private final ExpandURIUseCase expandURIUseCase;

    private final ShapesValidationUseCase shapesValidationUseCase;

    @Operation(
            summary = "validate stored constraints",
            description =
                    "Validates the graph's SHACL shapes against the CIM schema of its workspace, and "
                            + "reports each problem with the document and source position it belongs "
                            + "to. Validates every enabled document unless one is named, in which "
                            + "case that document is validated even when disabled. The per-document "
                            + "counts are what a document list needs for its status badges.",
            tags = {"shacl"},
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema =
                                                @Schema(
                                                        implementation =
                                                                ShapesValidationReport.class)))
            })
    @PostMapping
    public ShapesValidationReport validateShapes(
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
            @Parameter(description = "A single constraints document to validate.")
                    @RequestParam(required = false)
                    UUID documentId) {
        logger.info(
                "Received POST request: \"/api/datasets/{{}}/graphs/{{}}/shacl/validate\" from \"{}\".",
                datasetName,
                graphURI,
                originURL);

        return shapesValidationUseCase.validateShapes(
                graphIdentifier(datasetName, graphURI), documentId);
    }

    @Operation(
            summary = "validate constraints that are not stored",
            description =
                    "Validates a Turtle document that has not been saved, so an editor can show "
                            + "problems while the user types. Turtle that does not parse is reported "
                            + "as a finding with its position, not as a failed request.",
            tags = {"shacl"},
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema =
                                                @Schema(
                                                        implementation =
                                                                ShapesValidationReport.class)))
            })
    // Raw text, not JSON: Spring reads a String @RequestBody verbatim, so a JSON-quoted
    // body would reach Jena with its surrounding quotes and fail to parse.
    @PostMapping(path = "/text", consumes = MediaType.TEXT_PLAIN_VALUE)
    public ShapesValidationReport validateShapesText(
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
            @Parameter(description = "What to call the document in the report.")
                    @RequestParam(required = false, defaultValue = "unsaved")
                    String name,
            @Parameter(description = "The SHACL shapes in Turtle syntax.") @RequestBody
                    String turtle) {
        logger.info(
                "Received POST request: \"/api/datasets/{{}}/graphs/{{}}/shacl/validate/text\" from \"{}\".",
                datasetName,
                graphURI,
                originURL);

        return shapesValidationUseCase.validateTurtle(
                graphIdentifier(datasetName, graphURI), name, turtle);
    }

    private GraphIdentifier graphIdentifier(String datasetName, String graphURI) {
        return new GraphIdentifier(datasetName, expandURIUseCase.expandUri(datasetName, graphURI));
    }
}

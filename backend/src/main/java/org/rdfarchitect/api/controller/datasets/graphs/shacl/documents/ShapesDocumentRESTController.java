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

package org.rdfarchitect.api.controller.datasets.graphs.shacl.documents;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import lombok.RequiredArgsConstructor;

import org.rdfarchitect.api.controller.Response;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.services.ExpandURIUseCase;
import org.rdfarchitect.services.shacl.SHACLDocumentUseCase;
import org.rdfarchitect.shacl.dto.ShapesDocumentInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** A single set of SHACL shapes: its Turtle source, its metadata, and its removal. */
@RestController
@RequestMapping("api/datasets/{datasetName}/graphs/{graphURI}/shacl/documents/{documentId}")
@RequiredArgsConstructor
public class ShapesDocumentRESTController {

    private static final Logger logger =
            LoggerFactory.getLogger(ShapesDocumentRESTController.class);

    private final ExpandURIUseCase expandURIUseCase;

    private final SHACLDocumentUseCase shaclDocumentUseCase;

    @Operation(
            summary = "get constraints document content",
            description =
                    "Returns the document's Turtle source, as the user last saved it where that text is known.",
            tags = {"shacl"},
            responses = {@ApiResponse(responseCode = "200")})
    @GetMapping(produces = MediaType.TEXT_PLAIN_VALUE)
    public String getShapesDocumentText(
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
            @Parameter(description = "The id of the constraints document.") @PathVariable
                    UUID documentId) {
        logger.info(
                "Received GET request: \"/api/datasets/{{}}/graphs/{{}}/shacl/documents/{{}}\" from \"{}\".",
                datasetName,
                graphURI,
                documentId,
                originURL);

        return shaclDocumentUseCase.getShapesDocumentText(
                graphIdentifier(datasetName, graphURI), documentId);
    }

    @Operation(
            summary = "replace constraints document content",
            description = "Replaces the document's shapes with the given Turtle.",
            tags = {"shacl"},
            responses = {@ApiResponse(responseCode = "200")})
    // Raw text, not JSON: Spring reads a String @RequestBody verbatim, so a JSON-quoted
    // body would reach Jena with its surrounding quotes and fail to parse.
    @PutMapping(consumes = MediaType.TEXT_PLAIN_VALUE)
    public String replaceShapesDocumentText(
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
            @Parameter(description = "The id of the constraints document.") @PathVariable
                    UUID documentId,
            @Parameter(description = "The SHACL shapes in Turtle syntax.") @RequestBody
                    String turtle) {
        logger.info(
                "Received PUT request: \"/api/datasets/{{}}/graphs/{{}}/shacl/documents/{{}}\" from \"{}\".",
                datasetName,
                graphURI,
                documentId,
                originURL);

        shaclDocumentUseCase.replaceShapesDocumentText(
                graphIdentifier(datasetName, graphURI), documentId, turtle);
        return Response.SUCCESS;
    }

    @Operation(
            summary = "update constraints document metadata",
            description =
                    "Renames a document, switches it on or off, or moves it in the list. Omitted "
                            + "parameters are left unchanged. Order affects list position and "
                            + "serialisation order only — SHACL has no precedence between documents.",
            tags = {"shacl"},
            responses = {@ApiResponse(responseCode = "200")})
    @PatchMapping
    public ShapesDocumentInfo updateShapesDocument(
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
            @Parameter(description = "The id of the constraints document.") @PathVariable
                    UUID documentId,
            @Parameter(description = "New display name.") @RequestParam(required = false)
                    String name,
            @Parameter(
                            description =
                                    "Whether the shapes take part in validation and combined export.")
                    @RequestParam(required = false)
                    Boolean enabled,
            @Parameter(description = "New position in the document list.")
                    @RequestParam(required = false)
                    Integer order) {
        logger.info(
                "Received PATCH request: \"/api/datasets/{{}}/graphs/{{}}/shacl/documents/{{}}\" from \"{}\".",
                datasetName,
                graphURI,
                documentId,
                originURL);

        return shaclDocumentUseCase.updateShapesDocument(
                graphIdentifier(datasetName, graphURI), documentId, name, enabled, order);
    }

    @Operation(
            summary = "delete a constraints document",
            description =
                    "Deletes a set of SHACL shapes. The graph's default document cannot be deleted, "
                            + "only emptied.",
            tags = {"shacl"},
            responses = {@ApiResponse(responseCode = "200")})
    @DeleteMapping
    public String deleteShapesDocument(
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
            @Parameter(description = "The id of the constraints document.") @PathVariable
                    UUID documentId) {
        logger.info(
                "Received DELETE request: \"/api/datasets/{{}}/graphs/{{}}/shacl/documents/{{}}\" from \"{}\".",
                datasetName,
                graphURI,
                documentId,
                originURL);

        shaclDocumentUseCase.deleteShapesDocument(
                graphIdentifier(datasetName, graphURI), documentId);
        return Response.SUCCESS;
    }

    private GraphIdentifier graphIdentifier(String datasetName, String graphURI) {
        return new GraphIdentifier(datasetName, expandURIUseCase.expandUri(datasetName, graphURI));
    }
}

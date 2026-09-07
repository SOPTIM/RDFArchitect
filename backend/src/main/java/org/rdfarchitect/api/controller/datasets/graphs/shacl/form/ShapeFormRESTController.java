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

package org.rdfarchitect.api.controller.datasets.graphs.shacl.form;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import lombok.RequiredArgsConstructor;

import org.rdfarchitect.services.shacl.form.ShapeFormUseCase;
import org.rdfarchitect.shacl.dto.ShapeEditRequest;
import org.rdfarchitect.shacl.dto.ShapeEditResult;
import org.rdfarchitect.shacl.dto.ShapesForm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Editing a graph's constraints as shapes instead of as Turtle.
 *
 * <p>Both operations work on text the client sends and hands text back; nothing is stored. That is
 * what lets an editor offer a form over its unsaved buffer and switch back to the Turtle view with
 * no round trip through the database, and it keeps saving an explicit act.
 */
@RestController
@RequestMapping("api/datasets/{datasetName}/graphs/{graphURI}/shacl/form")
@RequiredArgsConstructor
public class ShapeFormRESTController {

    private static final Logger logger = LoggerFactory.getLogger(ShapeFormRESTController.class);

    private final ShapeFormUseCase shapeFormUseCase;

    @Operation(
            summary = "read constraints as shapes",
            description =
                    "Reads a Turtle document into the node shapes and property shapes a form can "
                            + "show. A shape using anything the form does not model — sh:or or an "
                            + "embedded query, say — comes back marked not editable, so it can be "
                            + "displayed without the form ever offering to write it back.",
            tags = {"shacl"},
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ShapesForm.class)))
            })
    // Raw text, not JSON: Spring reads a String @RequestBody verbatim, so a JSON-quoted body
    // would reach Jena with its surrounding quotes and fail to parse.
    @PostMapping(consumes = MediaType.TEXT_PLAIN_VALUE)
    public ShapesForm readForm(
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
            @Parameter(description = "The SHACL shapes in Turtle syntax.") @RequestBody
                    String turtle) {
        logger.debug(
                "Received POST request: \"/api/datasets/{{}}/graphs/{{}}/shacl/form\" from \"{}\".",
                datasetName,
                graphURI,
                originURL);

        return shapeFormUseCase.parse(turtle);
    }

    @Operation(
            summary = "apply a form edit",
            description =
                    "Writes one shape back into a document and returns the new text. Only the "
                            + "edited shape's statement is rewritten; every other byte of the "
                            + "document is preserved, so an imported constraints file keeps its "
                            + "comments and layout.",
            tags = {"shacl"},
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ShapeEditResult.class)))
            })
    @PostMapping("/apply")
    public ShapeEditResult applyEdit(
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
            @RequestBody ShapeEditRequest request) {
        logger.info(
                "Received POST request: \"/api/datasets/{{}}/graphs/{{}}/shacl/form/apply\" from \"{}\".",
                datasetName,
                graphURI,
                originURL);

        return shapeFormUseCase.apply(request);
    }
}

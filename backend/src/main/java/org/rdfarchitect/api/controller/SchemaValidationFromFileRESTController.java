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

package org.rdfarchitect.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import lombok.RequiredArgsConstructor;

import org.rdfarchitect.api.dto.validation.SchemaValidationReport;
import org.rdfarchitect.rdf.graph.source.builder.implementations.GraphFileSourceBuilderImpl;
import org.rdfarchitect.services.validation.SchemaValidationUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("api/validate")
public class SchemaValidationFromFileRESTController {

    private static final String GRAPH_URI = "http://example.org/graph";

    private static final Logger logger =
            LoggerFactory.getLogger(SchemaValidationFromFileRESTController.class);

    private final SchemaValidationUseCase schemaValidationUseCase;

    @Operation(
            summary = "Validate schema",
            description =
                    "Validates the RDFS schema of a given graph file for completeness, including the profile header.",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema =
                                                @Schema(
                                                        implementation =
                                                                SchemaValidationReport.class)))
            })
    @PostMapping
    public SchemaValidationReport validateSchema(
            @Parameter(description = "The name/url of the inquirer.")
                    @RequestHeader(
                            value = HttpHeaders.ORIGIN,
                            required = false,
                            defaultValue = "unknown")
                    String originURL,
            @Parameter(description = "The graph file to validate.") @RequestParam("file")
                    MultipartFile file) {
        logger.info("Received POST request: \"/api/validation\" from \"{}\".", originURL);

        var graph =
                new GraphFileSourceBuilderImpl()
                        .setFile(file)
                        .setGraphName(GRAPH_URI)
                        .build()
                        .graph();

        var report = schemaValidationUseCase.validateSchema(graph);

        logger.info(
                "Sending response to POST request: \"/api/validation\" from \"{}\".", originURL);

        return report;
    }
}

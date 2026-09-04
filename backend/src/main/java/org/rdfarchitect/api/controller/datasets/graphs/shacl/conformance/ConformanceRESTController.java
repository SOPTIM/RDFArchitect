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

package org.rdfarchitect.api.controller.datasets.graphs.shacl.conformance;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import lombok.RequiredArgsConstructor;

import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.services.ExpandURIUseCase;
import org.rdfarchitect.services.shacl.conformance.ConformanceUseCase;
import org.rdfarchitect.shacl.dto.ConformanceReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Comparing an imported constraints document with the schema it describes.
 *
 * <p>{@code GET} because it computes an answer about stored state and changes nothing, and because
 * a report worth looking at twice should be a URL.
 */
@RestController
@RequestMapping("api/datasets/{datasetName}/graphs/{graphURI}/shacl/conformance")
@RequiredArgsConstructor
public class ConformanceRESTController {

    private static final Logger logger = LoggerFactory.getLogger(ConformanceRESTController.class);

    private final ExpandURIUseCase expandURIUseCase;

    private final ConformanceUseCase conformanceUseCase;

    @Operation(
            summary = "compare constraints with the schema",
            description =
                    "Reports where a constraints document and the schema it describes disagree: what "
                            + "the schema implies and the document omits, what the document says that "
                            + "the schema does not have, and — the case worth acting on — what the two "
                            + "assert that cannot both be true. Compared per class and property, not "
                            + "by shape name, because generated and official shapes share no naming "
                            + "convention.",
            tags = {"shacl"},
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ConformanceReport.class)))
            })
    @GetMapping
    public ConformanceReport compare(
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
            @Parameter(description = "The constraints document to compare against.") @RequestParam
                    UUID documentId) {
        logger.info(
                "Received GET request: \"/api/datasets/{{}}/graphs/{{}}/shacl/conformance\" from \"{}\".",
                datasetName,
                graphURI,
                originURL);

        return conformanceUseCase.compare(
                new GraphIdentifier(datasetName, expandURIUseCase.expandUri(datasetName, graphURI)),
                documentId);
    }
}

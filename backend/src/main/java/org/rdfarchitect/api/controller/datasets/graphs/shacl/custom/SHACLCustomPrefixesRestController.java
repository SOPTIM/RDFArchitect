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

package org.rdfarchitect.api.controller.datasets.graphs.shacl.custom;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import lombok.RequiredArgsConstructor;

import org.apache.jena.riot.RDFFormat;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.services.ExpandURIUseCase;
import org.rdfarchitect.services.shacl.SHACLExportUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("api/datasets/{datasetName}/graphs/{graphURI}/shacl/custom/prefixes")
@RequiredArgsConstructor
public class SHACLCustomPrefixesRestController {

    private static final Logger logger =
            LoggerFactory.getLogger(SHACLCustomPrefixesRestController.class);

    private final ExpandURIUseCase expandURIUseCase;

    private final SHACLExportUseCase shaclExportUseCase;

    @Operation(
            summary = "export custom SHACL prefixes",
            description =
                    "Export the prefixes of the custom SHACL graph for a given graph identifier.",
            tags = {"shacl"},
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        content = {
                            @Content(mediaType = "text/turtle"),
                        })
            })
    @GetMapping("/ttl")
    public String getCustomSHACLAsString(
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
                "Received GET request: \"/api/datasets/{{}}/graphs/{{}}/shacl/custom/prefixes/ttl\" from \"{}\".",
                datasetName,
                graphURI,
                originURL);

        var extendedGraphURI = expandURIUseCase.expandUri(datasetName, graphURI);

        // fetch data
        var shaclString =
                shaclExportUseCase
                        .exportCustomSHACLPrefixes(
                                new GraphIdentifier(datasetName, extendedGraphURI),
                                RDFFormat.TURTLE)
                        .toString(StandardCharsets.UTF_8);

        logger.info(
                "Sending response to GET request: \"/api/datasets/{{}}/graphs/{{}}/shacl/custom/prefixes/ttl\" to \"{}\".",
                datasetName,
                graphURI,
                originURL);
        return shaclString;
    }
}

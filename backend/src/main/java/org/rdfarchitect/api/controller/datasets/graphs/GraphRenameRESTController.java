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

package org.rdfarchitect.api.controller.datasets.graphs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import lombok.RequiredArgsConstructor;

import org.rdfarchitect.api.controller.Response;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.services.ExpandURIUseCase;
import org.rdfarchitect.services.update.graph.RenameGraphUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/datasets/{datasetName}/graphs/{graphURI}/rename")
@RequiredArgsConstructor
public class GraphRenameRESTController {

    private static final Logger logger = LoggerFactory.getLogger(GraphRenameRESTController.class);

    private final ExpandURIUseCase expandURIUseCase;
    private final RenameGraphUseCase renameGraphUseCase;

    @Operation(
            summary = "Rename graph",
            description =
                    "Renames a graph within its dataset, keeping its content and history. References to the graph, i.e. in custom diagrams, are rewritten. If a new keyword is given, it replaces the dcat:keyword of the profile header.",
            tags = {"graph"},
            responses = {
                @ApiResponse(responseCode = "200"),
                @ApiResponse(responseCode = "409", description = "Graph URI already exists")
            })
    @PostMapping
    public String renameGraph(
            @Parameter(description = "The name/url of the inquirer.")
                    @RequestHeader(
                            value = HttpHeaders.ORIGIN,
                            required = false,
                            defaultValue = "unknown")
                    String originURL,
            @Parameter(description = "The literal name of the dataset.") @PathVariable
                    String datasetName,
            @Parameter(description = "The url encoded uri of the graph.") @PathVariable
                    String graphURI,
            @Parameter(description = "The url encoded uri to rename the graph to.") @RequestParam
                    String newGraphURI,
            @Parameter(
                            description =
                                    "The display name to store as dcat:keyword in the profile header.")
                    @RequestParam(required = false)
                    String newKeyword) {
        logger.info(
                "Received POST request: \"/api/datasets/{{}}/graphs/{{}}/rename\" from \"{}\".",
                datasetName,
                graphURI,
                originURL);

        var extendedGraphURI = expandURIUseCase.expandUri(datasetName, graphURI);
        var extendedNewGraphURI = expandURIUseCase.expandUri(datasetName, newGraphURI);

        renameGraphUseCase.renameGraph(
                new GraphIdentifier(datasetName, extendedGraphURI),
                extendedNewGraphURI,
                newKeyword);

        logger.info(
                "Sending response to POST request: \"/api/datasets/{{}}/graphs/{{}}/rename\" to \"{}\".",
                datasetName,
                graphURI,
                originURL);
        return Response.SUCCESS;
    }
}

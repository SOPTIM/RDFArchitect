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

package org.rdfarchitect.api.controller.datasets.graphs.versioncontrol;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import lombok.RequiredArgsConstructor;

import org.rdfarchitect.api.controller.Response;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.services.ExpandURIUseCase;
import org.rdfarchitect.services.versioncontrol.UndoUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/datasets/{datasetName}/graphs/{graphURI}/undo")
@RequiredArgsConstructor
public class UndoRESTController {

    private static final Logger logger = LoggerFactory.getLogger(UndoRESTController.class);

    private final ExpandURIUseCase expandURIUseCase;
    private final UndoUseCase undoUseCase;

    @Operation(
            summary = "undo ",
            description = "Undo the last change",
            tags = {"graph"},
            responses = {@ApiResponse(responseCode = "200")})
    @PostMapping
    public String undo(
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
                "Received POST request: \"/api/datasets/{{}}/graphs/{{}}/undo\" from \"{}\".",
                datasetName,
                graphURI,
                originURL);

        var extendedGraphURI = expandURIUseCase.expandUri(datasetName, graphURI);

        undoUseCase.undo(new GraphIdentifier(datasetName, extendedGraphURI));

        logger.info(
                "Sending response to POST request: \"/api/datasets/{{}}/graphs/{{}}/undo\" to \"{}\".",
                datasetName,
                graphURI,
                originURL);
        return Response.SUCCESS;
    }
}

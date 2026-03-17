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
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.services.ExpandURIUseCase;
import org.rdfarchitect.services.select.ResolveIdentifierUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/datasets/{datasetName}/graphs/{graphURI}/resolve")
@RequiredArgsConstructor
public class ResolveIdentifierRESTController {

    private static final Logger logger = LoggerFactory.getLogger(ResolveIdentifierRESTController.class);

    private final ExpandURIUseCase expandURIUseCase;
    private final ResolveIdentifierUseCase resolveIdentifierUseCase;

    @Operation(
              summary = "resolve iri identifier",
              description = "Resolve iri identifier of a cim resource to its uuid.",
              tags = {"graph"},
              responses = {
                        @ApiResponse(responseCode = "200")
              }
    )
    @GetMapping("iri/{iriIdentifier}")
    public String resolveIri(
              @Parameter(description = "The name/url of the inquirer.")
              @RequestHeader(value = HttpHeaders.ORIGIN, required = false, defaultValue = "unknown")
              String originURL,
              @Parameter(description = "The literal name of the dataset.")
              @PathVariable
              String datasetName,
              @Parameter(description = "The url encoded uri of the graph, or \"default\" to access the default graph.")
              @PathVariable
              String graphURI,
              @Parameter(description = "The url encoded iri identifier of the cim resource.")
              @PathVariable
              String iriIdentifier) {
        logger.info("Received GET request: \"/api/datasets/{{}}/graphs/{{}}/resolve/iri/{{}}\" from \"{}\".", datasetName, graphURI, iriIdentifier, originURL);

        var extendedGraphURI = expandURIUseCase.expandUri(datasetName, graphURI);
        var extendedResourceIRI = expandURIUseCase.expandUri(datasetName, iriIdentifier);

        var uuid = resolveIdentifierUseCase.resolveIRI(new GraphIdentifier(datasetName, extendedGraphURI), extendedResourceIRI);

        logger.info("Sending response to GET request: \"/api/datasets/{{}}/graphs/{{}}/resolve/iri/{{}}\" to \"{}\".", datasetName, graphURI, iriIdentifier, originURL);
        return uuid.toString();
    }
}

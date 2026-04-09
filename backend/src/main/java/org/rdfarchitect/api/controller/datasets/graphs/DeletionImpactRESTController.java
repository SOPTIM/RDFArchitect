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
import org.rdfarchitect.api.dto.delete.relations.AffectedResource;
import org.rdfarchitect.services.delete.FindOnDeleteRelationsUseCase;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.services.ExpandURIUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("api/datasets/{datasetName}/graphs/{graphURI}/uuid/{uuid}/deletion-impact")
@RequiredArgsConstructor
public class DeletionImpactRESTController {

    private static final Logger logger = LoggerFactory.getLogger(DeletionImpactRESTController.class);

    private final ExpandURIUseCase expandURIUseCase;
    private final FindOnDeleteRelationsUseCase findOnDeleteRelationsUseCase;

    //TODO: anpassen der beschreibung
    @Operation(
              summary = "resolve iri identifier",
              description = "Resolve iri identifier of a cim resource to its uuid.",
              tags = {"graph"},
              responses = {
                        @ApiResponse(responseCode = "200")
              }
    )
    @GetMapping
    public AffectedResource getDeletionImpact(
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
              String uuid) {
        logger.info("Received GET request: \"/api/datasets/{{}}/graphs/{{}}/uuid/{{}/deletion-impact\" from \"{}\".", datasetName, graphURI, uuid, originURL);

        var extendedGraphURI = expandURIUseCase.expandUri(datasetName, graphURI);

        var resultObj = findOnDeleteRelationsUseCase.getDeleteRelations(new GraphIdentifier(datasetName, extendedGraphURI), UUID.fromString(uuid));

        logger.info("Sending response to GET request: \"/api/datasets/{{}}/graphs/{{}}/uuid/{{}/deletion-impact\" from \"{}\".", datasetName, graphURI, uuid, originURL);
        return resultObj;
    }
}

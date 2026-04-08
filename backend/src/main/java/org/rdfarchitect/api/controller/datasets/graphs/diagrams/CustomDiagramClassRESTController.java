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

package org.rdfarchitect.api.controller.datasets.graphs.diagrams;

import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;
import org.rdfarchitect.api.controller.Response;
import org.rdfarchitect.api.dto.rendering.RenderingDataDTO;
import org.rdfarchitect.cim.rendering.RenderCIMCollectionUseCase;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.database.inmemory.diagrams.CustomDiagram;
import org.rdfarchitect.services.ExpandURIUseCase;
import org.rdfarchitect.services.diagrams.DeleteCustomDiagramUseCase;
import org.rdfarchitect.services.diagrams.RemoveFromDiagramUseCase;
import org.rdfarchitect.services.diagrams.ReplaceCustomDiagramUseCase;
import org.rdfarchitect.services.rendering.DiagramToCIMCollectionConverterUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/datasets/{datasetName}/graphs/{graphURI}/diagrams/{diagramId}/classes{classId}")
@RequiredArgsConstructor
public class CustomDiagramClassRESTController {

    private static final Logger logger = LoggerFactory.getLogger(CustomDiagramClassRESTController.class);

    private final ExpandURIUseCase expandURIUseCase;

    private final RemoveFromDiagramUseCase removeFromDiagramUseCase;

    @DeleteMapping
    public String removeFromDiagram(
              @Parameter(description = "The name/url of the inquirer.")
              @RequestHeader(value = HttpHeaders.ORIGIN, required = false, defaultValue = "unknown")
              String originURL,
              @Parameter(description = "The literal name of the dataset.")
              @PathVariable
              String datasetName,
              @Parameter(description = "The url encoded uri of the graph, or \"default\" to access the default graph.")
              @PathVariable
              String graphURI,
              @Parameter(description = "The uuid of the diagram.")
              @PathVariable
              String diagramId,
              @Parameter(description = "The uuid of the class to be removed from the diagram.")
              @PathVariable
              UUID classId) {
        logger.info("Received DELETE request: \"/api/datasets/{{}}/graphs/{{}}/diagrams/{{}}/classes/{{}}\" from \"{}\"", datasetName, graphURI, diagramId, classId, originURL);

        var extendedGraphURI = expandURIUseCase.expandUri(datasetName, graphURI);
        removeFromDiagramUseCase.removeFromDiagram(new GraphIdentifier(datasetName, extendedGraphURI), diagramId, classId);

        logger.info("Sending response to DELETE request: \"/api/datasets/{{}}/graphs/{{}}/diagrams/{{}}/classes/{{}}\" from \"{}\"", datasetName, graphURI, diagramId, classId, originURL);
        return Response.SUCCESS;
    }
}

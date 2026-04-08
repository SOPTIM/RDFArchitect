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
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.database.inmemory.diagrams.ClassInDiagram;
import org.rdfarchitect.services.ExpandURIUseCase;
import org.rdfarchitect.services.diagrams.AddToDiagramUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/datasets/{datasetName}/graphs/{graphURI}/diagrams/{diagramId}/classes")
@RequiredArgsConstructor
public class CustomDiagramAllClassesRESTController {

    private static final Logger logger = LoggerFactory.getLogger(CustomDiagramAllClassesRESTController.class);

    private final ExpandURIUseCase expandURIUseCase;

    private final AddToDiagramUseCase addToDiagramUseCase;

    @PostMapping
    public String addToDiagram(
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
              @Parameter(description = "The list of the classes to be added to the diagram")
              @RequestBody List<ClassInDiagram> classes) {
        logger.info("Received DELETE request: \"/api/datasets/{{}}/graphs/{{}}/diagrams/{{}}/classes\" from \"{}\"", datasetName, graphURI, diagramId, originURL);

        var extendedGraphURI = expandURIUseCase.expandUri(datasetName, graphURI);
        addToDiagramUseCase.addToDiagram(new GraphIdentifier(datasetName, extendedGraphURI), diagramId, classes);

        logger.info("Sending response to DELETE request: \"/api/datasets/{{}}/graphs/{{}}/diagrams/{{}}/classes\" from \"{}\"", datasetName, graphURI, diagramId, originURL);
        return Response.SUCCESS;
    }
}

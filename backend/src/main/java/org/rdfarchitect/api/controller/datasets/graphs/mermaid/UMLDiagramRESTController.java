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

package org.rdfarchitect.api.controller.datasets.graphs.mermaid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.rdfarchitect.cim.data.dto.CIMCollection;
import org.rdfarchitect.cim.rendering.GraphFilter;
import org.rdfarchitect.cim.rendering.RenderCIMCollectionUseCase;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.services.ExpandURIUseCase;
import org.rdfarchitect.services.GraphToCIMCollectionConverterUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/datasets/{datasetName}/graphs/{graphURI}/mermaid/UMLDiagram")
@RequiredArgsConstructor
public class UMLDiagramRESTController {

    private static final Logger logger = LoggerFactory.getLogger(UMLDiagramRESTController.class);

    private final GraphToCIMCollectionConverterUseCase converter;

    private final RenderCIMCollectionUseCase renderer;

    private final ExpandURIUseCase expandURIUseCase;

    @Operation(
              summary = "get mermaid UML",
              description = "Get a string containing the mermaid definition for a UML class diagram.",
              tags = {"mermaid"},
              responses = {@ApiResponse(
                        responseCode = "200",
                        content = @Content(
                                  mediaType = "application/vnd.mermaid",
                                  schema = @Schema(implementation = String.class)
                        ))
              }
    )
    @PostMapping
    public String getMermaidUmlParameterized(
              @Parameter(description = "The name/url of the inquirer.")
              @RequestHeader(value = "origin", required = false, defaultValue = "unknown")
              String originURL,
              @Parameter(description = "The literal name of the dataset.")
              @PathVariable
              String datasetName,
              @Parameter(description = "The url encoded uri of the graph, or \"default\" to access the default graph.")
              @PathVariable
              String graphURI,
              @RequestBody
              GraphFilter filter) {
        var extendedGraphURI = expandURIUseCase.expandUri(datasetName, graphURI);
        logger.info("Received GET request: \"/api/datasets/{{}}/graphs/{{}}/mermaid/UMLDiagram\" from \"{}\".", datasetName, graphURI, originURL);

        var graphIdentifier = new GraphIdentifier(datasetName, extendedGraphURI);

        CIMCollection cimCollection = converter.convert(graphIdentifier, filter);
        var mermaidString = renderer.renderUML(cimCollection);

        logger.info("Sending response to GET request \"/api/datasets/{{}}/graphs/{{}}/mermaid/UMLDiagram\" to \"{}\".", datasetName, graphURI, originURL);
        return mermaidString;
    }
}

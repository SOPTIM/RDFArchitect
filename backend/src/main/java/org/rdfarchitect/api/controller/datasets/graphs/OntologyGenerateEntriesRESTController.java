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
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.rdfarchitect.api.dto.ontology.OntologyEntry;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.services.ExpandURIUseCase;
import org.rdfarchitect.services.select.ontology.GenerateOntologyEntriesUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/datasets/{datasetName}/graphs/{graphURI}/ontology/generate")
@RequiredArgsConstructor
public class OntologyGenerateEntriesRESTController {

    private static final Logger logger = LoggerFactory.getLogger(OntologyGenerateEntriesRESTController.class);

    private final ExpandURIUseCase expandURIUseCase;
    private final GenerateOntologyEntriesUseCase generateOntologyEntriesUseCase;

    @Operation(
              summary = "Get generated ontology entries",
              description = "Get the Ontology fields that can be automatically generated.",
              tags = {"ontology"},
              responses = {@ApiResponse(
                        responseCode = "200",
                        content = @Content(
                                  mediaType = "application/json",
                                  array = @ArraySchema(
                                            schema = @Schema(implementation = OntologyEntry.class)
                                  )
                        )
              )}
    )
    @GetMapping
    public List<OntologyEntry> getOntology(
              @Parameter(description = "The name/url of the inquirer.")
              @RequestHeader(value = "origin", required = false, defaultValue = "unknown")
              String originURL,
              @Parameter(description = "The literal name of the dataset.")
              @PathVariable
              String datasetName,
              @Parameter(description = "The url encoded uri of the graph, or \"default\" to access the default graph.")
              @PathVariable
              String graphURI) {
        logger.info("Received GET request: \"/api/datasets/{{}}/graphs/{{}}/ontology/generate\" from \"{}\".", datasetName, graphURI, originURL);

        var extendedGraphURI = expandURIUseCase.expandUri(datasetName, graphURI);
        var graphIdentifier = new GraphIdentifier(datasetName, extendedGraphURI);

        var ontologyEntries = generateOntologyEntriesUseCase.generateOntologyEntries(graphIdentifier);

        logger.info("Sending response to GET request: \"/api/datasets/{{}}/graphs/{{}}/ontology/generate\" to \"{}\".", datasetName, graphURI, originURL);
        return ontologyEntries;
    }
}

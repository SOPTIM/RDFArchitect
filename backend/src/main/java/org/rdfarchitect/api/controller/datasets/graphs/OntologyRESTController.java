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
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.rdfarchitect.api.controller.Response;
import org.rdfarchitect.api.dto.ontology.OntologyDTO;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.services.ExpandURIUseCase;
import org.rdfarchitect.services.select.ontology.ReadOntologyUseCase;
import org.rdfarchitect.services.update.ontology.CreateOntologyUseCase;
import org.rdfarchitect.services.update.ontology.DeleteOntologyUseCase;
import org.rdfarchitect.services.update.ontology.UpdateOntologyUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/datasets/{datasetName}/graphs/{graphURI}/ontology")
@RequiredArgsConstructor
public class OntologyRESTController {

    private static final Logger logger = LoggerFactory.getLogger(OntologyRESTController.class);

    private final ExpandURIUseCase expandURIUseCase;
    private final CreateOntologyUseCase createOntologyUseCase;
    private final ReadOntologyUseCase readOntologyUseCase;
    private final UpdateOntologyUseCase updateOntologyUseCase;
    private final DeleteOntologyUseCase deleteOntologyUseCase;

    @Operation(
              summary = "Get ontology",
              description = "Get the currently stored ontology. null if none stored.",
              tags = {"ontology"},
              responses = {@ApiResponse(
                        responseCode = "200",
                        content = @Content(
                                  mediaType = "application/json",
                                  schema = @Schema(implementation = OntologyDTO.class)))}
    )
    @GetMapping
    public OntologyDTO getOntology(
              @Parameter(description = "The name/url of the inquirer.")
              @RequestHeader(value = "origin", required = false, defaultValue = "unknown")
              String originURL,
              @Parameter(description = "The literal name of the dataset.")
              @PathVariable
              String datasetName,
              @Parameter(description = "The url encoded uri of the graph, or \"default\" to access the default graph.")
              @PathVariable
              String graphURI) {
        logger.info("Received GET request: \"/api/datasets/{{}}/graphs/{{}}/ontology\" from \"{}\".", datasetName, graphURI, originURL);

        var extendedGraphURI = expandURIUseCase.expandUri(datasetName, graphURI);
        var graphIdentifier = new GraphIdentifier(datasetName, extendedGraphURI);

        var classObject = readOntologyUseCase.getCurrentOntology(graphIdentifier);

        logger.info("Sending response to GET request: \"/api/datasets/{{}}/graphs/{{}}/ontology\" to \"{}\".", datasetName, graphURI, originURL);
        return classObject;
    }

    @Operation(
              summary = "Create ontology",
              description = "create a new ontology.",
              tags = {"ontology"},
              responses = {@ApiResponse(
                        responseCode = "200"
              )}
    )
    @PostMapping
    public String createOntology(
              @Parameter(description = "The name/url of the inquirer.")
              @RequestHeader(value = "origin", required = false, defaultValue = "unknown")
              String originURL,
              @Parameter(description = "The literal name of the dataset.")
              @PathVariable
              String datasetName,
              @Parameter(description = "The url encoded uri of the graph, or \"default\" to access the default graph.")
              @PathVariable
              String graphURI,
              @io.swagger.v3.oas.annotations.parameters.RequestBody(
                        required = true,
                        description = "The new ontology", content = @Content(
                        schema = @Schema(implementation = OntologyDTO.class)
              ))
              @RequestBody OntologyDTO newOntology) {
        logger.info("Received POST request: \"/api/datasets/{{}}/graphs/{{}}/ontology\" from \"{}\".", datasetName, graphURI, originURL);

        var extendedGraphURI = expandURIUseCase.expandUri(datasetName, graphURI);
        var graphIdentifier = new GraphIdentifier(datasetName, extendedGraphURI);

        createOntologyUseCase.createOntology(graphIdentifier, newOntology);

        logger.info("Sending response to POST request: \"/api/datasets/{{}}/graphs/{{}}/ontology\" to \"{}\".", datasetName, graphURI, originURL);
        return Response.SUCCESS;
    }

    @Operation(
              summary = "Replace ontology",
              description = "Replace the currently stored ontology with a new one.",
              tags = {"ontology"},
              responses = {@ApiResponse(
                        responseCode = "200"
              )}
    )
    @PutMapping
    public String replaceOntology(
              @Parameter(description = "The name/url of the inquirer.")
              @RequestHeader(value = "origin", required = false, defaultValue = "unknown")
              String originURL,
              @Parameter(description = "The literal name of the dataset.")
              @PathVariable
              String datasetName,
              @Parameter(description = "The url encoded uri of the graph, or \"default\" to access the default graph.")
              @PathVariable
              String graphURI,
              @io.swagger.v3.oas.annotations.parameters.RequestBody(
                        required = true,
                        description = "The new ontology", content = @Content(
                        schema = @Schema(implementation = OntologyDTO.class)
              ))
              @RequestBody OntologyDTO newOntology) {
        logger.info("Received PUT request: \"/api/datasets/{{}}/graphs/{{}}/ontology\" from \"{}\".", datasetName, graphURI, originURL);

        var extendedGraphURI = expandURIUseCase.expandUri(datasetName, graphURI);
        var graphIdentifier = new GraphIdentifier(datasetName, extendedGraphURI);

        updateOntologyUseCase.replaceOntology(graphIdentifier, newOntology);

        logger.info("Sending response to PUT request: \"/api/datasets/{{}}/graphs/{{}}/ontology\" to \"{}\".", datasetName, graphURI, originURL);
        return Response.SUCCESS;
    }

    @Operation(
              summary = "Delete ontology",
              description = "Deletes the currently stored ontology.",
              tags = {"ontology"},
              responses = {@ApiResponse(
                        responseCode = "200"
              )}
    )
    @DeleteMapping
    public String deleteOntology(
              @Parameter(description = "The name/url of the inquirer.")
              @RequestHeader(value = "origin", required = false, defaultValue = "unknown")
              String originURL,
              @Parameter(description = "The literal name of the dataset.")
              @PathVariable
              String datasetName,
              @Parameter(description = "The url encoded uri of the graph, or \"default\" to access the default graph.")
              @PathVariable
              String graphURI) {
        logger.info("Received DELETE request: \"/api/datasets/{{}}/graphs/{{}}/ontology\" from \"{}\".", datasetName, graphURI, originURL);

        var extendedGraphURI = expandURIUseCase.expandUri(datasetName, graphURI);
        var graphIdentifier = new GraphIdentifier(datasetName, extendedGraphURI);

        deleteOntologyUseCase.deleteOntology(graphIdentifier);

        logger.info("Sending response to DELETE request: \"/api/datasets/{{}}/graphs/{{}}/ontology\" to \"{}\".", datasetName, graphURI, originURL);
        return Response.SUCCESS;
    }
}

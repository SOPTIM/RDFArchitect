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

package org.rdfarchitect.api.controller.datasets.graphs.classes;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.rdfarchitect.api.controller.Response;
import org.rdfarchitect.api.dto.ClassUMLAdaptedDTO;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.services.ExpandURIUseCase;
import org.rdfarchitect.services.select.GetClassInformationUseCase;
import org.rdfarchitect.services.update.classes.DeleteClassUseCase;
import org.rdfarchitect.services.update.classes.ReplaceClassUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/datasets/{datasetName}/graphs/{graphURI}/classes/{classUUID}")
@RequiredArgsConstructor
public class ClassRESTController {

    private static final Logger logger = LoggerFactory.getLogger(ClassRESTController.class);

    private final ExpandURIUseCase expandURIUseCase;
    private final GetClassInformationUseCase getClassInformationUseCase;
    private final ReplaceClassUseCase replaceClassUseCase;
    private final DeleteClassUseCase deleteClassUseCase;

    @Operation(
              summary = "Get class information",
              description = "Get all information about a specified class.",
              tags = {"class"},
              responses = {@ApiResponse(
                        responseCode = "200",
                        content = @Content(
                                  mediaType = "application/json",
                                  schema = @Schema(implementation = ClassUMLAdaptedDTO.class)))}
    )
    @GetMapping
    public ClassUMLAdaptedDTO getClassInformation(
              @Parameter(description = "The name/url of the inquirer.")
              @RequestHeader(value = "origin", required = false, defaultValue = "unknown")
              String originURL,
              @Parameter(description = "The literal name of the dataset.")
              @PathVariable
              String datasetName,
              @Parameter(description = "The url encoded uri of the graph, or \"default\" to access the default graph.")
              @PathVariable
              String graphURI,
              @Parameter(description = "The uuid of the class.")
              @PathVariable
              String classUUID) {
        logger.info("Received GET request: \"/api/datasets/{{}}/graphs/{{}}/classes/{{}}\" from \"{}\".", datasetName, graphURI, classUUID, originURL);

        var extendedGraphURI = expandURIUseCase.expandUri(datasetName, graphURI);
        var graphIdentifier = new GraphIdentifier(datasetName, extendedGraphURI);

        var classObject = getClassInformationUseCase.getClassInformation(graphIdentifier, classUUID);

        logger.info("Sending response to GET request: \"/api/datasets/{{}}/graphs/{{}}/classes/{{}}\" to \"{}\".", datasetName, graphURI, classUUID, originURL);
        return classObject;
    }

    @Operation(
              summary = "Replace class",
              description = "Replace a whole class.",
              tags = {"class"},
              responses = {@ApiResponse(
                        responseCode = "200"
              )
              }
    )
    @PutMapping
    public String replaceClass(
              @Parameter(description = "The name/url of the inquirer.")
              @RequestHeader(value = "origin", required = false, defaultValue = "unknown")
              String originURL,
              @Parameter(description = "The literal name of the dataset.")
              @PathVariable
              String datasetName,
              @Parameter(description = "The url encoded uri of the graph, or \"default\" to access the default graph.")
              @PathVariable
              String graphURI,
              @Parameter(description = "The uuid of the class.")
              @PathVariable
              String classUUID,
              @io.swagger.v3.oas.annotations.parameters.RequestBody(
                        required = true,
                        description = "The new class", content = @Content(
                        schema = @Schema(implementation = ClassUMLAdaptedDTO.class)
              ))
              @RequestBody ClassUMLAdaptedDTO newClass) {
        logger.info("Received PUT request: \"/api/datasets/{{}}/graphs/{{}}/classes/{{}}\" from \"{}\".", datasetName, graphURI, classUUID, originURL);

        var extendedGraphURI = expandURIUseCase.expandUri(datasetName, graphURI);
        var graphIdentifier = new GraphIdentifier(datasetName, extendedGraphURI);

        replaceClassUseCase.replaceClass(graphIdentifier, newClass);

        logger.info("Sending response to PUT request: \"/api/datasets/{{}}/graphs/{{}}/classes/{{}}\" to \"{}\".", datasetName, graphURI, classUUID, originURL);
        return Response.SUCCESS;
    }

    @Operation(
              summary = "Delete class",
              description = "Deletes a whole class.",
              tags = {"class"},
              responses = {@ApiResponse(
                        responseCode = "200"
              )
              }
    )
    @DeleteMapping
    public String deleteClass(
              @Parameter(description = "The name/url of the inquirer.")
              @RequestHeader(value = "origin", required = false, defaultValue = "unknown")
              String originURL,
              @Parameter(description = "The literal name of the dataset.")
              @PathVariable
              String datasetName,
              @Parameter(description = "The url encoded uri of the graph, or \"default\" to access the default graph.")
              @PathVariable
              String graphURI,
              @Parameter(description = "The uuid of the class.")
              @PathVariable
              String classUUID) {
        logger.info("Received DELETE request: \"/api/datasets/{{}}/graphs/{{}}/classes/{{}}\" from \"{}\".", datasetName, graphURI, classUUID, originURL);

        var graphIdentifier = new GraphIdentifier(datasetName, graphURI);

        deleteClassUseCase.deleteClass(graphIdentifier, classUUID);

        logger.info("Sending response to DELETE request: \"/api/datasets/{{}}/graphs/{{}}/classes/{{}}\" to \"{}\".", datasetName, graphURI, classUUID, originURL);
        return Response.SUCCESS;
    }
}

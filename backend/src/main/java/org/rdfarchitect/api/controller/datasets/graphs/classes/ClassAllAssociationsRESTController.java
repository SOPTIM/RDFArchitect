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
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.RequiredArgsConstructor;
import org.rdfarchitect.api.controller.Response;
import org.rdfarchitect.api.dto.association.AssociationPairDTO;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.services.ExpandURIUseCase;
import org.rdfarchitect.services.update.classes.associations.UpdateAssociationsUseCase;
import org.rdfarchitect.services.update.classes.associations.CreateAssociationUseCase;
import org.rdfarchitect.services.update.classes.associations.AssociationsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("api/datasets/{datasetName}/graphs/{graphURI}/classes/{classUUID}/associations")
@RequiredArgsConstructor
public class ClassAllAssociationsRESTController {

    private static final Logger logger = LoggerFactory.getLogger(ClassAllAssociationsRESTController.class);

    private final ExpandURIUseCase expandURIUseCase;
    private final CreateAssociationUseCase createAssociationUseCase;
    private final UpdateAssociationsUseCase updateAssociationsUseCase;

    @Operation(
              summary = "Replace all association",
              description = "Replaces all associations of a specified class.",
              tags = {"class"}
    )
    @PutMapping
    public String replaceAllAssociations(
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
                        description = "The new association", content = @Content(
                        array = @ArraySchema(schema = @Schema(implementation = AssociationPairDTO.class))
              ))
              @RequestBody List<AssociationPairDTO> associationPairList) {
        logger.info("Received PUT request: \"/api/datasets/{{}}/graphs/{{}}/classes/{{}}/associations\" from \"{}\".", datasetName, graphURI, classUUID, originURL);

        var extendedGraphURI = expandURIUseCase.expandUri(datasetName, graphURI);
        var graphIdentifier = new GraphIdentifier(datasetName, extendedGraphURI);

        updateAssociationsUseCase.replaceAllAssociations(graphIdentifier, classUUID, associationPairList);

        logger.info("Sending response to PUT request: \"/api/datasets/{{}}/graphs/{{}}/classes/{{}}/associations\" to \"{}\".", datasetName, graphURI, classUUID, originURL);
        return Response.SUCCESS;
    }

    @Operation(
              summary = "Creates association",
              description = "Creates a new association for a specified class.",
              tags = {"class"}
    )
    @PostMapping
    public AssociationsService.AssociationUUIDs createAssociation(
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
                        description = "The new association", content = @Content(
                        schema = @Schema(implementation = AssociationPairDTO.class)
              ))
              @RequestBody
              AssociationPairDTO associationPair) {
        logger.info("Received POST request: \"/api/datasets/{{}}/graphs/{{}}/classes/{{}}/associations/\" from \"{}\".", datasetName, graphURI, classUUID, originURL);

        var extendedGraphURI = expandURIUseCase.expandUri(datasetName, graphURI);
        var graphIdentifier = new GraphIdentifier(datasetName, extendedGraphURI);

        var newAssociationUUIDs = createAssociationUseCase.createAssociation(graphIdentifier, associationPair);

        logger.info("Sending response to POST request: \"/api/datasets/{{}}/graphs/{{}}/classes/{{}}/associations/\" from \"{}\".", datasetName, graphURI, classUUID, originURL);
        return newAssociationUUIDs;
    }
}

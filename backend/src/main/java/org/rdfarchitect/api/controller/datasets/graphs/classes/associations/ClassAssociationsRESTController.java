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

package org.rdfarchitect.api.controller.datasets.graphs.classes.associations;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.RequiredArgsConstructor;
import org.rdfarchitect.api.dto.association.AssociationPairDTO;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.services.ExpandURIUseCase;
import org.rdfarchitect.services.update.classes.associations.UpdateAssociationsUseCase;
import org.rdfarchitect.services.update.classes.associations.AssociationsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/datasets/{datasetName}/graphs/{graphURI}/classes/{classUUID}/associations/{associationUUID}")
@RequiredArgsConstructor
public class ClassAssociationsRESTController {

    private static final Logger logger = LoggerFactory.getLogger(ClassAssociationsRESTController.class);

    private final UpdateAssociationsUseCase updateAssociationsUseCase;
    private final ExpandURIUseCase expandURIUseCase;

    @Operation(
              summary = "Replace/Create association",
              description = "Replaces an association of a specified class.",
              tags = {"class"}
    )
    @PutMapping
    public AssociationsService.AssociationUUIDs replaceAssociation(
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
              @Parameter(description = "The old, to be replaced association.")
              @PathVariable
              String associationUUID,
              @io.swagger.v3.oas.annotations.parameters.RequestBody(
                        required = true,
                        description = "The new association", content = @Content(
                        schema = @Schema(implementation = AssociationPairDTO.class)
              ))
              @RequestBody
              AssociationPairDTO associationPairDTO) {
        logger.info("Received PUT request: \"/api/datasets/{{}}/graphs/{{}}/classes/{{}}/associations/{{}}\" from \"{}\".", datasetName, graphURI, classUUID, associationUUID,
                    originURL);

        var extendedGraphURI = expandURIUseCase.expandUri(datasetName, graphURI);
        var graphIdentifier = new GraphIdentifier(datasetName, extendedGraphURI);

        var newAssociationUUIDs = updateAssociationsUseCase.replaceAssociation(graphIdentifier, associationPairDTO);

        logger.info("Sending response to PUT request: \"/api/datasets/{{}}/graphs/{{}}/classes/{{}}/associations/{{}}\" to \"{}\".", datasetName, graphURI, classUUID,
                    associationUUID, originURL);
        return newAssociationUUIDs;
    }
}

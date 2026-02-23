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
import lombok.RequiredArgsConstructor;
import org.rdfarchitect.api.controller.Response;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.services.ExpandURIUseCase;
import org.rdfarchitect.services.shacl.SHACLGetShapeUseCase;
import org.rdfarchitect.services.shacl.SHACLUpdateUseCase;
import org.rdfarchitect.shacl.dto.CustomAndGeneratedTuple;
import org.rdfarchitect.shacl.dto.PropertyShape;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("api/datasets/{datasetName}/graphs/{graphURI}/classes/{classUUID}/associations/{associationUUID}/shacl")
@RequiredArgsConstructor
public class ClassAssociationsSHACLRESTController {

    private static final Logger logger = LoggerFactory.getLogger(ClassAssociationsSHACLRESTController.class);

    private final ExpandURIUseCase expandURIUseCase;

    private final SHACLGetShapeUseCase shaclGetShapeUseCase;

    private final SHACLUpdateUseCase shaclUpdateUseCase;

    @Operation(
            summary = "Get SHACL related to an association",
            description = "GET the shacl rules that can be related to a specified association.",
            tags = {"shacl"}
    )
    @GetMapping
    public CustomAndGeneratedTuple<List<PropertyShape>> getAssociationSHACL(
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
            @Parameter(description = "The uuid of the association.")
            @PathVariable
            String associationUUID) {
        logger.info("Received GET request: \"/api/datasets/{{}}/graphs/{{}}/classes/{{}}/associations/{{}}/shacl\" from \"{}\".", datasetName, graphURI, classUUID, associationUUID, originURL);

        var extendedGraphURI = expandURIUseCase.expandUri(datasetName, graphURI);
        var graphIdentifier = new GraphIdentifier(datasetName, extendedGraphURI);

        var shacl = shaclGetShapeUseCase.getPropertyShapesForAssociation(graphIdentifier, UUID.fromString(associationUUID));

        logger.info("Sending response to GET request: \"/api/datasets/{{}}/graphs/{{}}/classes/{{}}/associations/{{}}/shacl\" to \"{}\".", datasetName, graphURI, classUUID, associationUUID, originURL);
        return shacl;
    }

    @Operation(
            summary = "replace SHACL of an association",
            description = "Replace the SHACL rules of an association.",
            tags = {"shacl"}
    )
    @PutMapping
    public String replaceAssociationSHACL(
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
            @Parameter(description = "The uuid of the association.")
            @PathVariable
            String associationUUID,
            @Parameter(description = "The SHACL shapes to be inserted.")
            @RequestBody
            String shaclString) {
        logger.info("Received PUT request: \"/api/datasets/{{}}/graphs/{{}}/classes/{{}}/associations/{{}}/shacl\" from \"{}\".", datasetName, graphURI, classUUID, associationUUID, originURL);

        var extendedGraphURI = expandURIUseCase.expandUri(datasetName, graphURI);
        var graphIdentifier = new GraphIdentifier(datasetName, extendedGraphURI);

        shaclUpdateUseCase.updatePropertyShacl(graphIdentifier, UUID.fromString(associationUUID), shaclString);

        logger.info("Sending response to PUT request: \"/api/datasets/{{}}/graphs/{{}}/classes/{{}}/associations/{{}}/shacl\" to \"{}\".", datasetName, graphURI, classUUID, associationUUID, originURL);
        return Response.SUCCESS;
    }
}

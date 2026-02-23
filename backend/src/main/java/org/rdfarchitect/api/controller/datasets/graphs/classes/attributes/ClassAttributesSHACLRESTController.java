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

package org.rdfarchitect.api.controller.datasets.graphs.classes.attributes;

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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("api/datasets/{datasetName}/graphs/{graphURI}/classes/{classUUID}/attributes/{attributeUUID}/shacl")
@RequiredArgsConstructor
public class ClassAttributesSHACLRESTController {

    private static final Logger logger = LoggerFactory.getLogger(ClassAttributesSHACLRESTController.class);

    private final ExpandURIUseCase expandURIUseCase;

    private final SHACLGetShapeUseCase shaclGetShapeUseCase;

    private final SHACLUpdateUseCase shaclUpdateUseCase;

    @Operation(
            summary = "Get SHACL related to an attribute",
            description = "GET the shacl rules that can be related to a specified attribute.",
            tags = {"shacl"}
    )
    @GetMapping
    public CustomAndGeneratedTuple<List<PropertyShape>> getAttributeSHACL(
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
            @Parameter(description = "The uuid of the attribute.")
            @PathVariable
            String attributeUUID) {
        logger.info("Received GET request: \"/api/datasets/{{}}/graphs/{{}}/classes/{{}}/attributes/{{}}/shacl\" from \"{}\".", datasetName, graphURI, classUUID, attributeUUID, originURL);

        var extendedGraphURI = expandURIUseCase.expandUri(datasetName, graphURI);
        var graphIdentifier = new GraphIdentifier(datasetName, extendedGraphURI);

        var shacl = shaclGetShapeUseCase.getPropertyShapesForAttribute(graphIdentifier, UUID.fromString(attributeUUID));

        logger.info("Sending response to GET request: \"/api/datasets/{{}}/graphs/{{}}/classes/{{}}/attributes/{{}}/shacl\" to \"{}\".", datasetName, graphURI, classUUID, attributeUUID, originURL);
        return shacl;
    }

    @Operation(
            summary = "replace SHACL of an attribute",
            description = "Replace the SHACL rules of an attribute.",
            tags = {"shacl"}
    )
    @PutMapping
    public String replaceAttributeSHACL(
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
            String attributeUUID,
            @Parameter(description = "The SHACL shapes to be inserted.")
            @RequestBody
            String shaclString) {
        logger.info("Received PUT request: \"/api/datasets/{{}}/graphs/{{}}/classes/{{}}/attributes/{{}}/shacl\" from \"{}\".", datasetName, graphURI, classUUID, attributeUUID, originURL);

        var extendedGraphURI = expandURIUseCase.expandUri(datasetName, graphURI);
        var graphIdentifier = new GraphIdentifier(datasetName, extendedGraphURI);

        shaclUpdateUseCase.updatePropertyShacl(graphIdentifier, UUID.fromString(attributeUUID), shaclString);

        logger.info("Sending response to PUT request: \"/api/datasets/{{}}/graphs/{{}}/classes/{{}}/attributes/{{}}/shacl\" to \"{}\".", datasetName, graphURI, classUUID, attributeUUID, originURL);
        return Response.SUCCESS;
    }
}

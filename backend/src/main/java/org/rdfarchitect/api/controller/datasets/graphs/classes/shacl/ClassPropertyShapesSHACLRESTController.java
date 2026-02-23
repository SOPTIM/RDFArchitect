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

package org.rdfarchitect.api.controller.datasets.graphs.classes.shacl;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.services.ExpandURIUseCase;
import org.rdfarchitect.services.shacl.SHACLGetClassRelationsUseCase;
import org.rdfarchitect.shacl.dto.PropertyShapesWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/datasets/{datasetName}/graphs/{graphURI}/classes/{classUUID}/shacl/propertyShapes")
@RequiredArgsConstructor
public class ClassPropertyShapesSHACLRESTController {

    private static final Logger logger = LoggerFactory.getLogger(ClassPropertyShapesSHACLRESTController.class);

    private final ExpandURIUseCase expandURIUseCase;

    private final SHACLGetClassRelationsUseCase shaclGetClassRelationsUseCase;

    @Operation(
            summary = "Get SHACL related to a class",
            description = "Get the shacl rules that can be related to a specified class.",
            tags = {"shacl"},
            responses = {
                    @ApiResponse(responseCode = "200")
            }
    )
    @GetMapping
    public List<PropertyShapesWrapper> getPropertyShapes(
            @Parameter(description = "The name/url of the inquirer.")
            @RequestHeader(value = HttpHeaders.ORIGIN, required = false, defaultValue = "unknown")
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
        logger.info("Received GET request: \"/api/datasets/{{}}/graphs/{{}}/classes/{{}}/shacl/propertyShapes\" from \"{}\".", datasetName, graphURI, classUUID, originURL);

        var extendedGraphURI = expandURIUseCase.expandUri(datasetName, graphURI);
        var graphIdentifier = new GraphIdentifier(datasetName, extendedGraphURI);
        var res = shaclGetClassRelationsUseCase.getPropertyShapes(graphIdentifier, UUID.fromString(classUUID));

        logger.info("Sending response to GET request: \"/api/datasets/{{}}/graphs/{{}}/classes/{{}}/shacl/propertyShapes\" to \"{}\".", datasetName, graphURI, classUUID, originURL);
        return res;
    }
}

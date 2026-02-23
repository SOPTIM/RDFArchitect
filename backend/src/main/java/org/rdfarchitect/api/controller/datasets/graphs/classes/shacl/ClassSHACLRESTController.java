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
import org.rdfarchitect.api.controller.Response;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.services.ExpandURIUseCase;
import org.rdfarchitect.services.shacl.SHACLGetClassRelationsUseCase;
import org.rdfarchitect.services.shacl.SHACLUpdateUseCase;
import org.rdfarchitect.shacl.dto.CustomAndGeneratedTuple;
import org.rdfarchitect.shacl.dto.SHACLToClassRelations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/datasets/{datasetName}/graphs/{graphURI}/classes/{classUUID}/shacl")
@RequiredArgsConstructor
public class ClassSHACLRESTController {

    private static final Logger logger = LoggerFactory.getLogger(ClassSHACLRESTController.class);

    private final ExpandURIUseCase expandURIUseCase;

    private final SHACLGetClassRelationsUseCase shaclGetClassRelationsUseCase;

    private final SHACLUpdateUseCase shaclUpdateUseCase;

    @Operation(
            summary = "Get SHACL related to a class",
            description = "Get the shacl rules that can be related to a specified class.",
            tags = {"shacl"},
            responses = {
                    @ApiResponse(responseCode = "200")
            }
    )
    @GetMapping
    public CustomAndGeneratedTuple<SHACLToClassRelations> getSHACLRelatedToClass(
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
        logger.info("Received GET request: \"/api/datasets/{{}}/graphs/{{}}/classes/{{}}/shacl\" from \"{}\".", datasetName, graphURI, classUUID, originURL);

        var extendedGraphURI = expandURIUseCase.expandUri(datasetName, graphURI);
        var graphIdentifier = new GraphIdentifier(datasetName, extendedGraphURI);
        var res = shaclGetClassRelationsUseCase.getSHACLToClassRelations(graphIdentifier, UUID.fromString(classUUID));

        logger.info("Sending response to GET request: \"/api/datasets/{{}}/graphs/{{}}/classes/{{}}/shacl\" to \"{}\".", datasetName, graphURI, classUUID, originURL);
        return res;
    }

    @Operation(
            summary = "Replace or insert SHACL",
            description = "Replace or insert SHACL rules related to a class.",
            tags = {"shacl"},
            responses = {
                    @ApiResponse(responseCode = "200")
            }
    )
    @PutMapping("/custom")
    public String putSHACL(
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
            String classUUID,
            @Parameter(description = "The SHACL shapes to be inserted.")
            @RequestBody
            String shaclString) {
        logger.info("Received PUT request: \"/api/datasets/{{}}/graphs/{{}}/classes/{{}}/shacl\" from \"{}\".", datasetName, graphURI, classUUID, originURL);

        var extendedGraphURI = expandURIUseCase.expandUri(datasetName, graphURI);
        var graphIdentifier = new GraphIdentifier(datasetName, extendedGraphURI);
        shaclUpdateUseCase.updateClassSHACL(graphIdentifier, UUID.fromString(classUUID), shaclString);

        logger.info("Sending response to PUT request: \"/api/datasets/{{}}/graphs/{{}}/classes/{{}}/shacl\" to \"{}\".", datasetName, graphURI, classUUID, originURL);
        return Response.SUCCESS;
    }
}

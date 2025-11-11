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

package org.rdfarchitect.api.controller.datasets.graphs.shacl.custom;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.services.ExpandURIUseCase;
import org.rdfarchitect.services.shacl.SHACLDeleteShapeUseCase;
import org.rdfarchitect.services.shacl.SHACLReplaceShapeUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@CrossOrigin
@RestController
@RequestMapping("api/datasets/{datasetName}/graphs/{graphURI}/shacl/custom/{shaclShapeURI}")
@RequiredArgsConstructor
public class SHACLCustomShapeRESTController {

    private static final Logger logger = LoggerFactory.getLogger(SHACLCustomShapeRESTController.class);

    private final ExpandURIUseCase expandURIUseCase;

    private final SHACLReplaceShapeUseCase shaclReplaceShapeUseCase;

    private final SHACLDeleteShapeUseCase shaclDeleteShapeUseCase;

    @Operation(
            summary = "Replace/Insert shacl shape",
            description = "Replace or insert a shacl shape from a shacl graph with the given triples in a valid turtle syntax.",
            tags = {"shacl"},
            responses = {
                    @ApiResponse(
                            responseCode = "200")
            }
    )
    @PutMapping
    public String replaceShape(
            @Parameter(description = "The name/url of the inquirer.")
            @RequestHeader(value = "origin", required = false, defaultValue = "unknown")
            String originURL,
            @Parameter(description = "The literal name of the dataset.")
            @PathVariable
            String datasetName,
            @Parameter(description = "The url encoded uri of the graph, or \"default\" to access the default graph.")
            @PathVariable
            String graphURI,
            @Parameter(description = "The property shape to replace.")
            @PathVariable
            String shaclShapeURI,
            @Parameter(description = "the triples to replace, requires valid turtle syntax.")
            @RequestBody
            String shaclString) {
        logger.info("Received PUT request: \"/api/datasets/{{}}/graphs/{{}}/shacl/{{}}\" from \"{}\".", datasetName, graphURI, shaclShapeURI, originURL);

        var extendedGraphURI = expandURIUseCase.expandUri(datasetName, graphURI);
        var extendedShaclShapeURI = expandURIUseCase.expandUri(datasetName, shaclShapeURI);
        var graphIdentifier = new GraphIdentifier(datasetName, extendedGraphURI);
        shaclReplaceShapeUseCase.replaceSHACLShape(graphIdentifier, extendedShaclShapeURI, shaclString);

        logger.info("Sending response to PUT request: \"/api/datasets/{{}}/graphs/{{}}/shacl/{{}}\" to \"{}\".", datasetName, graphURI, shaclShapeURI, originURL);
        return "success";
    }

    @Operation(
            summary = "delete a shacl shape",
            description = "Delete a shacl shape form a shacl graph",
            tags = {"shacl"},
            responses = {
                    @ApiResponse(
                            responseCode = "200")
            }
    )
    @DeleteMapping
    public String deleteShape(
            @Parameter(description = "The name/url of the inquirer.")
            @RequestHeader(value = "origin", required = false, defaultValue = "unknown")
            String originURL,
            @Parameter(description = "The literal name of the dataset.")
            @PathVariable
            String datasetName,
            @Parameter(description = "The url encoded uri of the graph, or \"default\" to access the default graph.")
            @PathVariable
            String graphURI,
            @Parameter(description = "The property shape to delete.")
            @PathVariable
            String shaclShapeURI) {
        logger.info("Received DELETE request: \"/api/datasets/{{}}/graphs/{{}}/shacl/{{}}\" from \"{}\".", datasetName, graphURI, shaclShapeURI, originURL);

        var extendedGraphURI = expandURIUseCase.expandUri(datasetName, graphURI);
        var extendedShaclShapeURI = expandURIUseCase.expandUri(datasetName, shaclShapeURI);
        var graphIdentifier = new GraphIdentifier(datasetName, extendedGraphURI);
        shaclDeleteShapeUseCase.deleteSHACLShape(graphIdentifier, extendedShaclShapeURI);

        logger.info("Sending response to DELETE request: \"/api/datasets/{{}}/graphs/{{}}/shacl/{{}}\" to \"{}\".", datasetName, graphURI, shaclShapeURI, originURL);
        return "success";
    }
}

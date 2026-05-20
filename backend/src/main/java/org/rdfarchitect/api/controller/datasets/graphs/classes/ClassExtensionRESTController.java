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

import lombok.RequiredArgsConstructor;

import org.rdfarchitect.api.dto.ClassDTO;
import org.rdfarchitect.api.dto.attributes.AttributeDTO;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.services.ClassExtensionUseCase;
import org.rdfarchitect.services.ExpandURIUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/datasets/{datasetName}/graphs/{graphURI}/classes/{classUUID}/extend")
@RequiredArgsConstructor
public class ClassExtensionRESTController {
    private static final Logger logger =
            LoggerFactory.getLogger(ClassExtensionRESTController.class);

    private final ExpandURIUseCase expandURIUseCase;
    private final ClassExtensionUseCase classExtensionUseCase;

    @Operation(
            summary = "Extend class",
            description = "extends a class in another graph",
            tags = {"class"})
    @PostMapping
    public ClassDTO extendClass(
            @Parameter(description = "The name/url of the inquirer.")
                    @RequestHeader(
                            value = HttpHeaders.ORIGIN,
                            required = false,
                            defaultValue = "unknown")
                    String originURL,
            @Parameter(description = "The literal name of the dataset.") @PathVariable
                    String datasetName,
            @Parameter(
                            description =
                                    "The url encoded uri of the graph, or \"default\" to access the default graph.")
                    @PathVariable
                    String graphURI,
            @Parameter(description = "The uuid of the class.") @PathVariable String classUUID,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                            required = true,
                            description = "The new attribute",
                            content =
                                    @Content(schema = @Schema(implementation = AttributeDTO.class)))
                    @RequestBody
                    GraphIdentifier newGraphIdentifier) {
        logger.info(
                "Received POST request: \"/api/datasets/{{}}/graphs/{{}}/classes/{{}}/extend\" from \"{}\".",
                datasetName,
                graphURI,
                classUUID,
                originURL);

        var extendedGraphURI = expandURIUseCase.expandUri(datasetName, graphURI);
        var graphIdentifier = new GraphIdentifier(datasetName, extendedGraphURI);

        var newClass =
                classExtensionUseCase.extendClass(graphIdentifier, classUUID, newGraphIdentifier);

        logger.info(
                "Sending response to POST request: \"/api/datasets/{{}}/graphs/{{}}/classes/{{}}/extend\" to \"{}\".",
                datasetName,
                graphURI,
                classUUID,
                originURL);
        return newClass;
    }
}

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
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import lombok.RequiredArgsConstructor;

import org.rdfarchitect.api.dto.ClassUMLAdaptedDTO;
import org.rdfarchitect.api.dto.packages.PackageDTO;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.services.ExpandURIUseCase;
import org.rdfarchitect.services.select.GetClassListUseCase;
import org.rdfarchitect.services.update.classes.AddClassUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("api/datasets/{datasetName}/graphs/{graphURI}/classes")
@RequiredArgsConstructor
public class AllClassesRESTController {

    private static final Logger logger = LoggerFactory.getLogger(AllClassesRESTController.class);

    private final ExpandURIUseCase expandURIUseCase;
    private final GetClassListUseCase getClassListUseCase;
    private final AddClassUseCase addClassUseCase;

    /**
     * Helper record, functions as DTO for accepting the necessary information for adding a new
     * class
     *
     * @param packageDTO PackageDTO object of the package to which the new class is going to be
     *     added
     * @param classURIPrefix URI Prefix of the new class
     * @param className Label of the new class
     */
    public record AddNewClassRequest(
            PackageDTO packageDTO, String classURIPrefix, String className) {}

    @Operation(
            summary = "create new class",
            description =
                    "Create a new class with default name and no attributes, stereotypes or associations. Because no concrete stereotype is added the class is abstract "
                            + "by default.",
            tags = {"graph"})
    @PostMapping
    public String addClass(
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
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                            required = true,
                            description =
                                    "Helper record, functions as DTO for accepting the necessary information for adding a new class")
                    @RequestBody
                    AddNewClassRequest addNewClassRequest) {
        logger.info(
                "Received POST request: \"/api/datasets/{{}}/graphs/{{}}/classes\" from \"{}\".",
                datasetName,
                graphURI,
                originURL);

        var extendedGraphURI = expandURIUseCase.expandUri(datasetName, graphURI);
        var extendedClassURIPrefix =
                expandURIUseCase.expandUri(datasetName, addNewClassRequest.classURIPrefix);
        var graphIdentifier = new GraphIdentifier(datasetName, extendedGraphURI);

        var classUUID =
                addClassUseCase.addClass(
                        graphIdentifier,
                        addNewClassRequest.packageDTO,
                        extendedClassURIPrefix,
                        addNewClassRequest.className);

        logger.info(
                "Sending response to POST request: \"/api/datasets/{{}}/graphs/{{}}/classes\" to \"{}\".",
                datasetName,
                graphURI,
                originURL);

        return classUUID.toString();
    }

    @Operation(
            summary = "list classes",
            description =
                    "Get a list containing all classes. Doesn't include: stereotypes, attributes and associations.",
            tags = {"graph"},
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        array =
                                                @ArraySchema(
                                                        schema =
                                                                @Schema(
                                                                        implementation =
                                                                                ClassUMLAdaptedDTO
                                                                                        .class))))
            })
    @GetMapping
    public List<ClassUMLAdaptedDTO> getClassList(
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
                    String graphURI) {
        logger.info(
                "Received GET request: \"/api/datasets/{{}}/graphs/{{}}/classes\" from \"{}\".",
                datasetName,
                graphURI,
                originURL);

        var extendedGraphURI = expandURIUseCase.expandUri(datasetName, graphURI);

        var cimClassList =
                getClassListUseCase.getClassList(
                        new GraphIdentifier(datasetName, extendedGraphURI));

        logger.info(
                "Sending response to GET request: \"/api/datasets/{{}}/graphs/{{}}/classes\" to \"{}\".",
                datasetName,
                graphURI,
                originURL);
        return cimClassList;
    }
}

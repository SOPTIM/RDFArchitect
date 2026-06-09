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

package org.rdfarchitect.api.controller.datasets.graphs.packages;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;

import lombok.RequiredArgsConstructor;

import org.rdfarchitect.api.controller.Response;
import org.rdfarchitect.api.dto.packages.PackageDTO;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.services.ExpandURIUseCase;
import org.rdfarchitect.services.update.packages.DeletePackageUseCase;
import org.rdfarchitect.services.update.packages.GetPackageUseCase;
import org.rdfarchitect.services.update.packages.ReplacePackageUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("api/datasets/{datasetName}/graphs/{graphURI}/packages/{packageUUID}")
@RequiredArgsConstructor
public class PackageRESTController {

    private static final Logger logger = LoggerFactory.getLogger(PackageRESTController.class);

    private final ExpandURIUseCase expandURIUseCase;
    private final ReplacePackageUseCase replacePackageUseCase;
    private final DeletePackageUseCase deletePackageUseCase;
    private final GetPackageUseCase getPackageUseCase;

    @Operation(
            summary = "replace package",
            description = "Replaces a whole package.",
            tags = {"package", "graph"})
    @PutMapping
    public String replacePackage(
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
            @Parameter(description = "The UUID of the package to be replaced.") @PathVariable
                    String packageUUID,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                            description = "DTO for the package to be replaced")
                    @RequestBody
                    PackageDTO packageDTO) {

        logger.info(
                "Received PUT request: \"/api/datasets/{{}}/graphs/{{}}/packages/{{}}\" from \"{}\".",
                datasetName,
                graphURI,
                originURL,
                packageUUID);

        var extendedGraphURI = expandURIUseCase.expandUri(datasetName, graphURI);
        var graphIdentifier = new GraphIdentifier(datasetName, extendedGraphURI);

        replacePackageUseCase.replacePackage(graphIdentifier, packageDTO);

        logger.info(
                "Sending response to PUT request: \"/api/datasets/{{}}/graphs/{{}}/packages/{{}}\" from \"{}\".",
                datasetName,
                graphURI,
                originURL,
                packageUUID);
        return Response.SUCCESS;
    }

    @Operation(
            summary = "delete package",
            description = "Deletes a package by UUID.",
            tags = {"package", "graph"})
    @DeleteMapping
    public String deletePackage(
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
            @Parameter(description = "The UUID of the package to be deleted.") @PathVariable
                    UUID packageUUID) {

        logger.info(
                "Received DELETE request: \"/api/datasets/{{}}/graphs/{{}}/packages/{{}}\" from \"{}\".",
                datasetName,
                graphURI,
                packageUUID,
                originURL);

        var extendedGraphURI = expandURIUseCase.expandUri(datasetName, graphURI);
        var graphIdentifier = new GraphIdentifier(datasetName, extendedGraphURI);

        deletePackageUseCase.deletePackage(graphIdentifier, packageUUID);

        logger.info(
                "Sending response to DELETE request: \"/api/datasets/{{}}/graphs/{{}}/packages/{{}}\" from \"{}\".",
                datasetName,
                graphURI,
                packageUUID,
                originURL);
        return Response.SUCCESS;
    }

    @Operation(
            summary = "get package",
            description = "Returns the package DTO for the given UUID.",
            tags = {"package", "graph"})
    @GetMapping
    public PackageDTO getPackage(
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
            @Parameter(description = "The UUID of the package to retrieve.") @PathVariable
                    UUID packageUUID) {

        logger.info(
                "Received GET request: \"/api/datasets/{{}}/graphs/{{}}/packages/{{}}\" from \"{}\".",
                datasetName,
                graphURI,
                packageUUID,
                originURL);

        var extendedGraphURI = expandURIUseCase.expandUri(datasetName, graphURI);
        var graphIdentifier = new GraphIdentifier(datasetName, extendedGraphURI);

        var result = getPackageUseCase.getPackage(graphIdentifier, packageUUID);

        logger.info(
                "Sending response to GET request: \"/api/datasets/{{}}/graphs/{{}}/packages/{{}}\" from \"{}\".",
                datasetName,
                graphURI,
                packageUUID,
                originURL);
        return result;
    }
}

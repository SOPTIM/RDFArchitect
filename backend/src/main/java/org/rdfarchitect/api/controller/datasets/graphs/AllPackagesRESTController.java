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

package org.rdfarchitect.api.controller.datasets.graphs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.rdfarchitect.api.dto.packages.PackageDTO;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.services.ExpandURIUseCase;
import org.rdfarchitect.services.select.ListExternalPackagesUseCase;
import org.rdfarchitect.services.select.ListInternalPackagesUseCase;
import org.rdfarchitect.services.update.packages.AddPackageUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("api/datasets/{datasetName}/graphs/{graphURI}/packages")
@RequiredArgsConstructor
public class AllPackagesRESTController {

    private static final Logger logger = LoggerFactory.getLogger(AllPackagesRESTController.class);

    private final ExpandURIUseCase expandURIUseCase;
    private final AddPackageUseCase addPackageUseCase;
    private final ListInternalPackagesUseCase listInternalPackagesUseCase;
    private final ListExternalPackagesUseCase listExternalPackagesUseCase;

    /**
     * Record for response to frontend, contains two lists for internal and external packages.
     */
    public record ListPackagesResponse(
              List<PackageDTO> internalPackageList,
              List<PackageDTO> externalPackageList
    ) {

    }

    @Operation(
              summary = "list packages",
              description = "Get two lists of packages: internal and external packages.",
              tags = {"graph"},
              responses = {@ApiResponse(
                        responseCode = "200",
                        content = @Content(
                                  mediaType = "application/json",
                                  array = @ArraySchema(schema = @Schema(implementation = ListPackagesResponse.class))
                        ))
              }
    )
    @GetMapping
    public ListPackagesResponse listPackages(
              @Parameter(description = "The name/url of the inquirer.")
              @RequestHeader(value = "origin", required = false, defaultValue = "unknown")
              String originURL,
              @Parameter(description = "The literal name of the dataset.")
              @PathVariable
              String datasetName,
              @Parameter(description = "The url encoded uri of the graph, or \"default\" to access the default graph.")
              @PathVariable
              String graphURI) {
        logger.info("Received GET request: \"/api/datasets/{{}}/graphs/{{}}/packages\" from \"{}\".", datasetName, graphURI, originURL);

        var extendedGraphURI = expandURIUseCase.expandUri(datasetName, graphURI);

        var internalPackageList = listInternalPackagesUseCase.listInternalPackages(new GraphIdentifier(datasetName, extendedGraphURI));

        var externalPackageList = listExternalPackagesUseCase.listExternalPackages(new GraphIdentifier(datasetName, extendedGraphURI));

        logger.info("Sending response to GET request: \"/api/datasets/{{}}/graphs/{{}}/packages\" from \"{}\".", datasetName, graphURI, originURL);
        return new ListPackagesResponse(internalPackageList, externalPackageList);
    }

    @Operation(
              summary = "create new package",
              description = "Create a new package with a given name and optional comment, sub-package relation is nulled",
              tags = {"graph"}
    )
    @PostMapping
    public UUID addPackage(
              @Parameter(description = "The name/url of the inquirer.")
              @RequestHeader(value = "origin", required = false, defaultValue = "unknown")
              String originURL,
              @Parameter(description = "The literal name of the dataset.")
              @PathVariable
              String datasetName,
              @Parameter(description = "The url encoded uri of the graph, or \"default\" to access the default graph.")
              @PathVariable
              String graphURI,
              @io.swagger.v3.oas.annotations.parameters.RequestBody(
                        description = "DTO for the package to be created"
              )
              @RequestBody PackageDTO packageDTO) {

        logger.info("Received POST request: \"/api/datasets/{{}}/graphs/{{}}/packages\" from \"{}\".", datasetName, graphURI, originURL);

        var extendedGraphURI = expandURIUseCase.expandUri(datasetName, graphURI);
        var extendedPackageURIPrefix = expandURIUseCase.expandUri(datasetName, packageDTO.getPrefix());
        var graphIdentifier = new GraphIdentifier(datasetName, extendedGraphURI);
        packageDTO.setPrefix(extendedPackageURIPrefix);

        var newPackageUUID = addPackageUseCase.addPackage(graphIdentifier, packageDTO);

        logger.info("Sending response to POST request: \"/api/datasets/{{}}/graphs/{{}}/packages\" to \"{}\".", datasetName, graphURI, originURL);
        return newPackageUUID;
    }
}

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
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import lombok.RequiredArgsConstructor;

import org.rdfarchitect.api.dto.delete.ResourceDeleteRequest;
import org.rdfarchitect.api.dto.delete.relations.AffectedResource;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.services.ExpandURIUseCase;
import org.rdfarchitect.services.delete.DeleteResourcesUseCase;
import org.rdfarchitect.services.delete.FindDeleteDependenciesUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("api/datasets/{datasetName}/graphs/{graphName}")
@RequiredArgsConstructor
public class DeleteRESTController {

    private static final Logger logger = LoggerFactory.getLogger(DeleteRESTController.class);

    private final ExpandURIUseCase expandURIUseCase;
    private final FindDeleteDependenciesUseCase findDeleteDependenciesUseCase;
    private final DeleteResourcesUseCase deleteResourcesUseCase;

    @Operation(
            summary = "Get deletion impact",
            description =
                    "Returns, per requested UUID, a tree of affected resources for deleting that resource.",
            tags = {"graph"},
            responses = {@ApiResponse(responseCode = "200")})
    @PostMapping("/deletion-impact")
    public Map<UUID, AffectedResource> getDeletionImpact(
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
                    String graphName,
            @Parameter(description = "The uuids of the cim resources to analyse.") @RequestBody
                    List<UUID> uuids) {
        logger.info(
                "Received POST request: \"/api/datasets/{{}}/graphs/{{}}/deletion-impact\" for {} resource(s) from \"{}\".",
                datasetName,
                graphName,
                uuids.size(),
                originURL);

        var extendedGraphName = expandURIUseCase.expandUri(datasetName, graphName);
        var graphIdentifier = new GraphIdentifier(datasetName, extendedGraphName);

        var result = findDeleteDependenciesUseCase.getDeleteDependencies(graphIdentifier, uuids);

        logger.info(
                "Sending response to POST request: \"/api/datasets/{{}}/graphs/{{}}/deletion-impact\" from \"{}\".",
                datasetName,
                graphName,
                originURL);
        return result;
    }

    @Operation(
            summary = "Delete resources",
            description =
                    "Processes a list of delete requests, each specifying a resource UUID and the desired action.",
            tags = {"graph"},
            responses = {@ApiResponse(responseCode = "200")})
    @PostMapping("/delete-requests")
    public String deleteResources(
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
                    String graphName,
            @Parameter(
                            description =
                                    "A list of resource delete requests, each containing the uuid of the resource to delete and the type of deletion")
                    @RequestBody
                    List<ResourceDeleteRequest> deleteRequests) {
        logger.info(
                "Received POST request: \"/api/datasets/{{}}/graphs/{{}}/delete-requests\" from \"{}\".",
                datasetName,
                graphName,
                originURL);

        var extendedGraphName = expandURIUseCase.expandUri(datasetName, graphName);

        deleteResourcesUseCase.executeDeleteRequests(
                new GraphIdentifier(datasetName, extendedGraphName), deleteRequests);

        logger.info(
                "Sending response to POST request: \"/api/datasets/{{}}/graphs/{{}}/delete-requests\" from \"{}\".",
                datasetName,
                graphName,
                originURL);
        return "success";
    }
}

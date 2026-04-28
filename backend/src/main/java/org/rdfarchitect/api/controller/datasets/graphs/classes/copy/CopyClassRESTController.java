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

package org.rdfarchitect.api.controller.datasets.graphs.classes.copy;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;

import lombok.RequiredArgsConstructor;

import org.rdfarchitect.api.controller.Response;
import org.rdfarchitect.api.dto.packages.PackageDTO;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.services.ExpandURIUseCase;
import org.rdfarchitect.services.update.classes.CopyClassUseCase;
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
@RequestMapping("api/datasets/{datasetName}/graphs/{graphURI}/classes/{classUUID}/copy")
@RequiredArgsConstructor
public class CopyClassRESTController {

    private static final Logger logger = LoggerFactory.getLogger(CopyClassRESTController.class);

    private final ExpandURIUseCase expandURIUseCase;
    private final CopyClassUseCase copyClassUseCase;

    @Operation(
            summary = "copy a class",
            description = "Create a copy of a class in the specified graph")
    @PostMapping
    public String copyClass(
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
                    description =
                            "Contains the information of the target where the class should be copied to. Also includes if the class should be copied only abstract or "
                                    + "fully.")
            @RequestBody
            CopyClassRESTController.CopyClassRequest copyClassRequest) {
        logger.info(
                "Received POST request: \"/api/datasets/{{}}/graphs/{{}}/classes/{{}}/copy\" from \"{}\".",
                datasetName,
                graphURI,
                classUUID,
                originURL);

        logger.info("--- LOOK HERE---");
        logger.info(
                "datasetName: {}, graphURI: {}, classUUID: {}, targetDatasetName: {}, targetGraphURI: {}, targetPackage: {}, copyAbstract: {}",
                datasetName,
                graphURI,
                classUUID,
                copyClassRequest.targetDatasetName,
                copyClassRequest.targetGraphURI,
                copyClassRequest.targetPackage,
                copyClassRequest.copyAbstract);
        logger.info("--- LOOK HERE---");

        var extendedGraphURI = expandURIUseCase.expandUri(datasetName, graphURI);
        var graphIdentifier = new GraphIdentifier(datasetName, extendedGraphURI);

        var targetExtendedGraphURI =
                expandURIUseCase.expandUri(
                        copyClassRequest.targetDatasetName, copyClassRequest.targetGraphURI);
        var targetGraphIdentifier =
                new GraphIdentifier(copyClassRequest.targetDatasetName, targetExtendedGraphURI);

        copyClassUseCase.copyClass(
                graphIdentifier,
                classUUID,
                targetGraphIdentifier,
                copyClassRequest.targetPackage,
                copyClassRequest.copyAbstract);

        logger.info(
                "Sending response to POST request: \"/api/datasets/{{}}/graphs/{{}}/classes/{{}}/copy\" to \"{}\".",
                datasetName,
                graphURI,
                classUUID,
                originURL);

        return Response.SUCCESS;
    }

    /**
     * Helper record, functions as DTO for accepting the necessary information for copying a class
     *
     * @param targetDatasetName the name of the dataset where the class should be copied to
     * @param targetGraphURI the graph URI where the class should be copied to
     * @param targetPackage the package where the class should be copied to
     * @param copyAbstract if true, only the class itself will be copied, if false, all attributes
     *     will also be copied
     */
    public record CopyClassRequest(
            String targetDatasetName,
            String targetGraphURI,
            PackageDTO targetPackage,
            boolean copyAbstract) {
    }
}

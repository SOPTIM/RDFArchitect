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

package org.rdfarchitect.api.controller.datasets.graphs.rendering;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import lombok.RequiredArgsConstructor;

import org.rdfarchitect.api.dto.rendering.RenderingDataDTO;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.models.cim.data.dto.CIMCollection;
import org.rdfarchitect.models.cim.rendering.GraphFilter;
import org.rdfarchitect.models.cim.rendering.RenderCIMCollectionUseCase;
import org.rdfarchitect.services.ExpandURIUseCase;
import org.rdfarchitect.services.GraphToCIMCollectionConverterUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/datasets/{datasetName}/graphs/{graphURI}/rendering")
@RequiredArgsConstructor
public class RenderingRESTController {

    private static final Logger logger = LoggerFactory.getLogger(RenderingRESTController.class);

    private final GraphToCIMCollectionConverterUseCase converter;

    private final RenderCIMCollectionUseCase renderer;

    private final ExpandURIUseCase expandURIUseCase;

    @Operation(
            summary = "Get rendering data",
            description =
                    "Returns rendering data for UML diagrams. The content varies based on environment configuration (Mermaid or Svelteflow).",
            tags = {"svelteflow", "mermaid"},
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Rendering data (Mermaid or Svelteflow)",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = RenderingDataDTO.class)))
            })
    @PostMapping
    public RenderingDataDTO getRenderingDataParameterized(
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
            @RequestBody GraphFilter filter) {
        var extendedGraphURI = expandURIUseCase.expandUri(datasetName, graphURI);
        logger.info(
                "Received GET request: \"/api/datasets/{{}}/graphs/{{}}/rendering\" from \"{}\".",
                datasetName,
                graphURI,
                originURL);

        var graphIdentifier = new GraphIdentifier(datasetName, extendedGraphURI);

        var packageUUID =
                !filter.getPackageUUID().equals("default")
                        ? UUID.fromString(filter.getPackageUUID())
                        : null;

        CIMCollection cimCollection = converter.convert(graphIdentifier, filter);
        RenderingDataDTO renderingData =
                renderer.renderUML(cimCollection, graphIdentifier, packageUUID);

        logger.info(
                "Sending response to GET request \"/api/datasets/{{}}/graphs/{{}}/rendering\" to \"{}\".",
                datasetName,
                graphURI,
                originURL);
        return renderingData;
    }
}

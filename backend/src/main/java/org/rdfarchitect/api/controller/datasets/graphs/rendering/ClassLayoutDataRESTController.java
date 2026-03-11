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
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.RequiredArgsConstructor;
import org.rdfarchitect.api.dto.dl.ClassPositionDTO;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.services.ExpandURIUseCase;
import org.rdfarchitect.services.dl.update.classlayout.UpdateClassPositionsUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("api/datasets/{datasetName}/graphs/{graphURI}/layout/{diagramUUID}/classes")
@RequiredArgsConstructor
public class ClassLayoutDataRESTController {

    private static final Logger logger = LoggerFactory.getLogger(ClassLayoutDataRESTController.class);

    private final ExpandURIUseCase expandURIUseCase;
    private final UpdateClassPositionsUseCase updateClassPositionsUseCase;

    @Operation(
              summary = "updates class positions",
              description = "Updates the positions for all the classes provided in the request body with the provided coordinates.",
              tags = {"diagram", "layout", "class"}
    )
    @PutMapping
    public String updateClassPositions(
              @Parameter(description = "The name/url of the inquirer.")
              @RequestHeader(value = "origin", required = false, defaultValue = "unknown")
              String originURL,
              @Parameter(description = "The literal name of the dataset.")
              @PathVariable
              String datasetName,
              @Parameter(description = "The url encoded uri of the graph, or \"default\" to access the default graph.")
              @PathVariable
              String graphURI,
              @Parameter(description = "The UUID of the package or custom diagram being updated.")
              @PathVariable
              String diagramUUID,
              @io.swagger.v3.oas.annotations.parameters.RequestBody(
                        required = true,
                        description = "The DTO with necessary information for class reposition",
                        content = @Content(
                                  array = @ArraySchema(schema = @Schema(implementation = ClassPositionDTO.class))
                        ))
              @RequestBody
              List<ClassPositionDTO> classPositionDTOList) {

        logger.info("Received PUT request: \"/api/datasets/{{}}/graphs/{{}}/layout/{{}}/classes\" from \"{}\".", datasetName, graphURI, diagramUUID, originURL);

        var extendedGraphURI = expandURIUseCase.expandUri(datasetName, graphURI);
        var resolvedPackageUUID = !diagramUUID.equals("default") ? UUID.fromString(diagramUUID) : null;

        updateClassPositionsUseCase.updateClassPositions(new GraphIdentifier(datasetName, extendedGraphURI), resolvedPackageUUID, classPositionDTOList);

        logger.info("Sending response to PUT request: \"/api/datasets/{{}}/graphs/{{}}/layout/{{}}/classes\" from \"{}\".", datasetName, graphURI, diagramUUID, originURL);
        return "success";
    }
}

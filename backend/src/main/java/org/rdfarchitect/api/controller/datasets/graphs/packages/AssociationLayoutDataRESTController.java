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
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.RequiredArgsConstructor;
import org.rdfarchitect.api.dto.dl.AssociationDecorationPositionDTO;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.services.ExpandURIUseCase;
import org.rdfarchitect.services.dl.update.associationlayout.UpdateAssociationDecorationPositionsUseCase;
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
@RequestMapping("api/datasets/{datasetName}/graphs/{graphURI}/packages/{packageUUID}/layout/associations")
@RequiredArgsConstructor
public class AssociationLayoutDataRESTController {

    private static final Logger logger = LoggerFactory.getLogger(AssociationLayoutDataRESTController.class);

    private final ExpandURIUseCase expandURIUseCase;
    private final UpdateAssociationDecorationPositionsUseCase updateAssociationDecorationPositionsUseCase;

    @Operation(
              summary = "updates association decoration positions",
              description = "Updates the persisted node-relative offsets for association labels and multiplicities.",
              tags = {"diagram", "layout", "association"}
    )
    @PutMapping
    public String updateAssociationDecorationPositions(
              @Parameter(description = "The name/url of the inquirer.")
              @RequestHeader(value = "origin", required = false, defaultValue = "unknown")
              String originURL,
              @Parameter(description = "The literal name of the dataset.")
              @PathVariable
              String datasetName,
              @Parameter(description = "The url encoded uri of the graph, or \"default\" to access the default graph.")
              @PathVariable
              String graphURI,
              @Parameter(description = "The UUID of the package to be updated.")
              @PathVariable
              String packageUUID,
              @io.swagger.v3.oas.annotations.parameters.RequestBody(
                        required = true,
                        description = "The DTO with necessary information for association decoration reposition",
                        content = @Content(
                                  array = @ArraySchema(schema = @Schema(implementation = AssociationDecorationPositionDTO.class))
                        ))
              @RequestBody
              List<AssociationDecorationPositionDTO> associationDecorationPositionDTOList) {

        logger.info("Received PUT request: \"/api/datasets/{{}}/graphs/{{}}/packages/{{}}/layout/associations\" from \"{}\".", datasetName, graphURI, packageUUID,
                    originURL);

        var extendedGraphURI = expandURIUseCase.expandUri(datasetName, graphURI);
        var resolvedPackageUUID = !packageUUID.equals("default") ? UUID.fromString(packageUUID) : null;

        updateAssociationDecorationPositionsUseCase.updateAssociationDecorationPositions(
                  new GraphIdentifier(datasetName, extendedGraphURI),
                  resolvedPackageUUID,
                  associationDecorationPositionDTOList);

        logger.info("Sending response to PUT request: \"/api/datasets/{{}}/graphs/{{}}/packages/{{}}/layout/associations\" from \"{}\".", datasetName, graphURI,
                    packageUUID, originURL);
        return "success";
    }
}

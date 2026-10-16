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

import org.rdfarchitect.api.dto.dl.DiagramLayoutDTO;
import org.rdfarchitect.api.dto.dl.EdgeLayoutDTO;
import org.rdfarchitect.api.dto.dl.EdgePointIdDTO;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.services.ExpandURIUseCase;
import org.rdfarchitect.services.dl.update.UpdateDiagramLayoutUseCase;
import org.rdfarchitect.services.dl.update.edgelayout.UpdateEdgeLayoutUseCase;
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
@RequestMapping("api/datasets/{datasetName}/graphs/{graphURI}/layout/{diagramUUID}")
@RequiredArgsConstructor
public class DiagramLayoutDataRESTController {

    private static final Logger logger =
            LoggerFactory.getLogger(DiagramLayoutDataRESTController.class);

    private final ExpandURIUseCase expandURIUseCase;
    private final UpdateDiagramLayoutUseCase updateDiagramLayoutUseCase;
    private final UpdateEdgeLayoutUseCase updateEdgeLayoutUseCase;

    @Operation(
            summary = "updates the layout of a diagram",
            description =
                    "Updates the layout of the classes, edges and labels in the request body in one"
                            + " step. Every part is optional, only the elements sent are updated."
                            + " Returns the ids new edge points are stored under.",
            tags = {"diagram", "layout"})
    @PutMapping
    public List<EdgePointIdDTO> updateDiagramLayout(
            @Parameter(description = "The name/url of the inquirer.")
                    @RequestHeader(value = "origin", required = false, defaultValue = "unknown")
                    String originURL,
            @Parameter(description = "The literal name of the dataset.") @PathVariable
                    String datasetName,
            @Parameter(
                            description =
                                    "The url encoded uri of the graph, or \"default\" to access the default graph.")
                    @PathVariable
                    String graphURI,
            @Parameter(description = "The UUID of the package or custom diagram being updated.")
                    @PathVariable
                    String diagramUUID,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                            required = true,
                            description = "The layout of the changed classes, edges and labels",
                            content =
                                    @Content(
                                            schema =
                                                    @Schema(
                                                            implementation =
                                                                    DiagramLayoutDTO.class)))
                    @RequestBody
                    DiagramLayoutDTO diagramLayoutDTO) {
        logger.info(
                "Received PUT request: \"/api/datasets/{{}}/graphs/{{}}/layout/{{}}\" from \"{}\".",
                datasetName,
                graphURI,
                diagramUUID,
                originURL);
        var newPointIds =
                updateDiagramLayoutUseCase.updateDiagramLayout(
                        graphIdentifier(datasetName, graphURI),
                        resolveDiagramUUID(diagramUUID),
                        diagramLayoutDTO);
        logger.info(
                "Sending response to PUT request: \"/api/datasets/{{}}/graphs/{{}}/layout/{{}}\" from \"{}\".",
                datasetName,
                graphURI,
                diagramUUID,
                originURL);
        return newPointIds;
    }

    @Operation(
            summary = "updates the layout of edges",
            description =
                    "Replaces the points of the edges in the request body with the full, ordered"
                            + " list of points sent for each. Returns the ids new edge points are"
                            + " stored under.",
            tags = {"diagram", "layout", "edge"})
    @PutMapping("/edges")
    public List<EdgePointIdDTO> updateEdgeLayouts(
            @Parameter(description = "The name/url of the inquirer.")
                    @RequestHeader(value = "origin", required = false, defaultValue = "unknown")
                    String originURL,
            @Parameter(description = "The literal name of the dataset.") @PathVariable
                    String datasetName,
            @Parameter(
                            description =
                                    "The url encoded uri of the graph, or \"default\" to access the default graph.")
                    @PathVariable
                    String graphURI,
            @Parameter(description = "The UUID of the package or custom diagram being updated.")
                    @PathVariable
                    String diagramUUID,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                            required = true,
                            description = "The layout of the changed edges",
                            content =
                                    @Content(
                                            array =
                                                    @ArraySchema(
                                                            schema =
                                                                    @Schema(
                                                                            implementation =
                                                                                    EdgeLayoutDTO
                                                                                            .class))))
                    @RequestBody
                    List<EdgeLayoutDTO> edgeLayoutDTOList) {
        logger.info(
                "Received PUT request: \"/api/datasets/{{}}/graphs/{{}}/layout/{{}}/edges\" from \"{}\".",
                datasetName,
                graphURI,
                diagramUUID,
                originURL);
        var newPointIds =
                updateEdgeLayoutUseCase.updateEdgeLayouts(
                        graphIdentifier(datasetName, graphURI),
                        resolveDiagramUUID(diagramUUID),
                        edgeLayoutDTOList);
        logger.info(
                "Sending response to PUT request: \"/api/datasets/{{}}/graphs/{{}}/layout/{{}}/edges\" from \"{}\".",
                datasetName,
                graphURI,
                diagramUUID,
                originURL);
        return newPointIds;
    }

    private GraphIdentifier graphIdentifier(String datasetName, String graphURI) {
        return new GraphIdentifier(datasetName, expandURIUseCase.expandUri(datasetName, graphURI));
    }

    private static UUID resolveDiagramUUID(String diagramUUID) {
        return !diagramUUID.equals("default") ? UUID.fromString(diagramUUID) : null;
    }
}

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

package org.rdfarchitect.api.controller.datasets.diagrams;

import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;
import org.rdfarchitect.api.controller.Response;
import org.rdfarchitect.api.dto.rendering.RenderingDataDTO;
import org.rdfarchitect.database.inmemory.diagrams.CustomDiagram;
import org.rdfarchitect.models.cim.rendering.RenderCIMCollectionUseCase;
import org.rdfarchitect.services.diagrams.DeleteCustomDiagramUseCase;
import org.rdfarchitect.services.diagrams.ReplaceCustomDiagramUseCase;
import org.rdfarchitect.services.rendering.DiagramToCIMCollectionConverterUseCase;
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
@RequestMapping("/api/datasets/{datasetName}/diagrams/{diagramId}")
@RequiredArgsConstructor
public class CustomDatasetDiagramsRESTController {

    private static final Logger logger = LoggerFactory.getLogger(CustomDatasetDiagramsRESTController.class);

    private final DiagramToCIMCollectionConverterUseCase converter;

    private final RenderCIMCollectionUseCase renderer;

    private final DeleteCustomDiagramUseCase deleteCustomDiagram;

    private final ReplaceCustomDiagramUseCase replaceCustomDiagram;

    @GetMapping
    public RenderingDataDTO getDiagramRenderingData(
              @Parameter(description = "The name/url of the inquirer.")
              @RequestHeader(value = HttpHeaders.ORIGIN, required = false, defaultValue = "unknown")
              String originURL,
              @Parameter(description = "The literal name of the dataset.")
              @PathVariable
              String datasetName,
              @Parameter(description = "The uuid of the diagram.")
              @PathVariable
              String diagramId) {
        logger.info("Received GET request: \"/api/datasets/{{}}/diagrams/{{}}\" from \"{}\"", datasetName, diagramId, originURL);

        var cimCollection = converter.convert(datasetName, diagramId);

        var result = renderer.renderGlobalUML(cimCollection, datasetName, UUID.fromString(diagramId));

        logger.info("Sending response to GET request: \"/api/datasets/{{}}/diagrams/{{}}\" from \"{}\"", datasetName, diagramId, originURL);
        return result;
    }

    @PutMapping
    public String replaceDiagram(
              @Parameter(description = "The name/url of the inquirer.")
              @RequestHeader(value = HttpHeaders.ORIGIN, required = false, defaultValue = "unknown")
              String originURL,
              @Parameter(description = "The literal name of the dataset.")
              @PathVariable
              String datasetName,
              @Parameter(description = "The uuid of the diagram.")
              @PathVariable
              String diagramId,
              @Parameter(description = "DTO for the diagram to be replaced.")
              @RequestBody
              CustomDiagram diagram) {
        logger.info("Received PUT request: \"/api/datasets/{{}}/diagrams/{{}}\" from \"{}\"", datasetName, diagramId, originURL);

        replaceCustomDiagram.replaceCustomDiagram(datasetName, diagramId, diagram);

        logger.info("Sending response to PUT request: \"/api/datasets/{{}}/diagrams/{{}}\" from \"{}\"", datasetName, diagramId, originURL);
        return Response.SUCCESS;
    }

    @DeleteMapping
    public String deleteDiagram(
              @Parameter(description = "The name/url of the inquirer.")
              @RequestHeader(value = HttpHeaders.ORIGIN, required = false, defaultValue = "unknown")
              String originURL,
              @Parameter(description = "The literal name of the dataset.")
              @PathVariable
              String datasetName,
              @Parameter(description = "The uuid of the diagram.")
              @PathVariable
              String diagramId) {
        logger.info("Received DELETE request: \"/api/datasets/{{}}/diagrams/{{}}\" from \"{}\"", datasetName, diagramId, originURL);

        deleteCustomDiagram.deleteCustomDiagram(datasetName, diagramId);

        logger.info("Sending response to DELETE request: \"/api/datasets/{{}}/diagrams/{{}}\" from \"{}\"", datasetName, diagramId, originURL);
        return Response.SUCCESS;
    }
}

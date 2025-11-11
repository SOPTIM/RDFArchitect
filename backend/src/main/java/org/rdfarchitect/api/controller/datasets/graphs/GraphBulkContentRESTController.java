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
import org.rdfarchitect.services.update.graph.GraphBulkImportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@CrossOrigin
@RestController
@RequestMapping("api/datasets/{datasetName}/graphs/content")
@RequiredArgsConstructor
public class GraphBulkContentRESTController {

    private static final Logger logger = LoggerFactory.getLogger(GraphBulkContentRESTController.class);

    private final GraphBulkImportService graphBulkImportService;

    @Operation(
              summary = "Replace/Insert multiple graphs",
              description = "Replace or insert one or more rdf graphs for the dataset. Accepts multiple files and/or zip archives containing several graph files.",
              tags = {"graph"},
              responses = {
                        @ApiResponse(
                                  responseCode = "200")
              }
    )
    @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<GraphBulkImportResponse> replaceGraphs(
              @Parameter(description = "The name/url of the inquirer.")
              @RequestHeader(value = "origin", required = false, defaultValue = "unknown")
              String originURL,
              @Parameter(description = "The literal name of the dataset.")
              @PathVariable
              String datasetName,
              @Parameter(description = "The files containing the graph data")
              @RequestParam("files")
              List<MultipartFile> files,
              @Parameter(description = "Optional graph URIs, one per file. Defaults to file names.")
              @RequestParam(value = "graphUris", required = false)
              List<String> graphUris) {
        logger.info("Received PUT request: \"/api/datasets/{{}}/graphs/content\" from \"{}\".", datasetName, originURL);

        var importedGraphUris = graphBulkImportService.importGraphs(datasetName, files, graphUris);

        logger.info("Sending response to PUT request: \"/api/datasets/{{}}/graphs/content\" to \"{}\".", datasetName, originURL);
        return ResponseEntity.ok(new GraphBulkImportResponse("success", importedGraphUris));
    }

    public record GraphBulkImportResponse(String message, List<String> importedGraphUris) {
    }
}

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
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.models.changes.triplechanges.TriplePackageChange;
import org.rdfarchitect.services.ExpandURIUseCase;
import org.rdfarchitect.services.compare.SchemaComparisonUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("api/datasets/{datasetName}/graphs/{graphURI}/compare")
public class SchemaComparisonRESTController {

    private static final Logger logger = LoggerFactory.getLogger(SchemaComparisonRESTController.class);

    private final ExpandURIUseCase expandURIUseCase;
    private final SchemaComparisonUseCase schemaComparisonUseCase;

    @Operation(
              summary = "compare schemas",
              description = "Compare a given graph with the specified graph from the dataset",
              tags = {"graph"},
              responses = {@ApiResponse(
                        responseCode = "200",
                        content = @Content(
                                  mediaType = "application/json",
                                  array = @ArraySchema(schema = @Schema(implementation = TriplePackageChange.class))
                        ))
              }
    )
    @PostMapping
    public List<TriplePackageChange> compareSchemas(
              @Parameter(description = "The name/url of the inquirer.")
              @RequestHeader(value = HttpHeaders.ORIGIN, required = false, defaultValue = "unknown")
              String originURL,
              @Parameter(description = "The literal name of the dataset.")
              @PathVariable
              String datasetName,
              @Parameter(description = "The url encoded uri of the graph, or \"default\" to access the default graph.")
              @PathVariable
              String graphURI,
              @Parameter(description = "The file containing the graph to be compared.")
              @RequestParam("file")
              MultipartFile file) {
        logger.info("Received POST request: \"/api/datasets/{{}}/graphs/{{}}/compare\" from \"{}\".", datasetName, graphURI, originURL);

        var extendedGraphURI = expandURIUseCase.expandUri(datasetName, graphURI);
        var changes = schemaComparisonUseCase.compareSchemas(new GraphIdentifier(datasetName, extendedGraphURI), file);

        logger.info("Sending response to POST request: \"/api/datasets/{{}}/graphs/{{}}/compare\" from \"{}\".", datasetName, graphURI, originURL);
        return changes;
    }

    @Operation(
              summary = "compare schemas",
              description = "Compare two graphs stored in the database",
              tags = {"graph"},
              responses = {
                        @ApiResponse(
                                  responseCode = "200",
                                  content = @Content(
                                            mediaType = "application/json",
                                            array = @ArraySchema(schema = @Schema(implementation = TriplePackageChange.class))
                                  )
                        )
              }
    )
    @GetMapping()
    public List<TriplePackageChange> compareDatasetSchemas(
              @Parameter(description = "The name/url of the inquirer.")
              @RequestHeader(value = HttpHeaders.ORIGIN, required = false, defaultValue = "unknown")
              String originURL,
              @Parameter(description = "The literal name of the base dataset.")
              @PathVariable
              String datasetName,
              @Parameter(description = "The url encoded uri of the base graph, or \"default\" to access the default graph.")
              @PathVariable
              String graphURI,
              @Parameter(description = "The literal name of the dataset to compare against.")
              @RequestParam("otherDataset")
              String otherDataset,
              @Parameter(description = "The url encoded uri of the graph to compare against.")
              @RequestParam("otherGraph")
              String otherGraph) {
        logger.info("Received GET request: \"/api/datasets/{{}/graphs/{{}/compare?otherDataset={{}}&otherGraph={{}}\" from \"{}\".", datasetName, graphURI, otherDataset,
                    otherGraph, originURL);

        var baseGraphURI = expandURIUseCase.expandUri(datasetName, graphURI);
        var otherGraphURI = expandURIUseCase.expandUri(otherDataset, otherGraph);

        var changes = schemaComparisonUseCase.compareSchemas(new GraphIdentifier(datasetName, baseGraphURI), new GraphIdentifier(otherDataset, otherGraphURI));

        logger.info("Sending response to GET request: \"/api/datasets/{{}/graphs/{{}/compare?otherDataset={{}}&otherGraph={{}}\" from \"{}\".", datasetName, graphURI,
                    otherDataset, otherGraph, originURL);
        return changes;
    }

}

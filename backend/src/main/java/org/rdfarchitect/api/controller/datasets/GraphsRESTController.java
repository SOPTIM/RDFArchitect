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

package org.rdfarchitect.api.controller.datasets;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.rdfarchitect.models.cim.search.data.dto.relations.uri.URI;
import org.rdfarchitect.services.select.ListGraphsUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("api/datasets/{datasetName}/graphs")
@RequiredArgsConstructor
public class GraphsRESTController {

    private static final Logger logger = LoggerFactory.getLogger(GraphsRESTController.class);

    private final ListGraphsUseCase listGraphsUseCase;

    @Operation(
              summary = "List graphs",
              description = "Lists all graphs in a specified datasets",
              tags = {"dataset"},
              responses = {
                        @ApiResponse(
                                  responseCode = "200"
                        )
              }
    )
    @GetMapping
    public List<URI> listGraphs(
              @Parameter(description = "The name/url of the inquirer.")
              @RequestHeader(value = HttpHeaders.ORIGIN, required = false, defaultValue = "unknown")
              String originURL,
              @Parameter(description = "The literal name of the dataset.")
              @PathVariable
              String datasetName) {
        logger.info("Received GET request: \"/api/datasets/{{}}/graphs\" from \"{}\".", datasetName, originURL);

        var result = listGraphsUseCase.listGraphs(datasetName);

        logger.info("Sending response to GET request: \"/api/datasets/{{}}/graphs\" to \"{}\".", datasetName, originURL);
        return result;
    }
}

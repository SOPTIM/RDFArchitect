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
import org.rdfarchitect.database.inmemory.diagrams.CustomDiagram;
import org.rdfarchitect.services.diagrams.GetCustomDiagramsUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/datasets/{datasetName}/diagrams")
@RequiredArgsConstructor
public class AllCustomDatasetDiagramsRESTController {

    private static final Logger logger = LoggerFactory.getLogger(AllCustomDatasetDiagramsRESTController.class);

    private final GetCustomDiagramsUseCase getCustomDiagramsUseCase;

    @GetMapping
    public List<CustomDiagram> getCustomDiagramList(
              @Parameter(description = "The name/url of the inquirer.")
              @RequestHeader(value = HttpHeaders.ORIGIN, required = false, defaultValue = "unknown")
              String originURL,
              @Parameter(description = "The literal name of the dataset.")
              @PathVariable
              String datasetName) {
        logger.info("Received GET request: \"/api/datasets/{{}}/diagrams\" from \"{}\"", datasetName, originURL);

        var result = getCustomDiagramsUseCase.getCustomDiagramsForDataset(datasetName);

        logger.info("Sending response to GET request: \"/api/datasets/{{}}/diagrams\" from \"{}\"", datasetName, originURL);
        return result;
    }

}

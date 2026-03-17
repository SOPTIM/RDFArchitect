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
import org.rdfarchitect.api.controller.Response;
import org.rdfarchitect.services.readonly.DisableEditingUseCase;
import org.rdfarchitect.services.readonly.EnableEditingUseCase;
import org.rdfarchitect.services.readonly.IsReadOnlyUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/datasets/{datasetName}/readonly")
@RequiredArgsConstructor
public class ReadOnlyRESTController {

    private static final Logger logger = LoggerFactory.getLogger(ReadOnlyRESTController.class);

    private final IsReadOnlyUseCase isReadOnlyUseCase;
    private final EnableEditingUseCase enableEditingUseCase;
    private final DisableEditingUseCase disableEditingUseCase;

    @Operation(
            summary = "is read only",
            description = "Check whether dataset is read-only",
            tags = {"dataset", "read-only"},
            responses = {
                    @ApiResponse(
                            responseCode = "200"
                    )
            }
    )

    @GetMapping
    public boolean isReadOnly(
            @Parameter(description = "The name/url of the inquirer.")
            @RequestHeader(value = HttpHeaders.ORIGIN, required = false, defaultValue = "unknown")
            String originURL,
            @Parameter(description = "The literal name of the dataset.")
            @PathVariable
            String datasetName) {
        logger.info("Received GET request: \"/api/datasets/{{}}/readonly\" from \"{}\".", datasetName, originURL);

        var isReadOnly = isReadOnlyUseCase.isReadOnly(datasetName);

        logger.info("Sending response to GET request: \"/api/datasets/{{}}/readonly\" to \"{}\".", datasetName, originURL);
        return isReadOnly;
    }

    @Operation(
            summary = "enable editing",
            description = "Enables editing for specified dataset",
            tags = {"dataset", "read-only"},
            responses = {
                    @ApiResponse(
                            responseCode = "200"
                    )
            }
    )
    @PutMapping
    public String enableEditing(
            @Parameter(description = "The name/url of the inquirer.")
            @RequestHeader(value = HttpHeaders.ORIGIN, required = false, defaultValue = "unknown")
            String originURL,
            @Parameter(description = "The literal name of the dataset.")
            @PathVariable
            String datasetName) {
        logger.info("Received PUT request: \"/api/datasets/{{}}/readonly\" from \"{}\".", datasetName, originURL);

        enableEditingUseCase.enableEditing(datasetName);

        logger.info("Sending response to PUT request: \"/api/datasets/{{}}/readonly\" to \"{}\".", datasetName, originURL);
        return Response.SUCCESS;
    }

    @Operation(
              summary = "disable editing",
              description = "Disables editing for specified dataset",
              tags = {"dataset", "read-only"},
              responses = {
                        @ApiResponse(
                                  responseCode = "200"
                        )
              }
    )
    @DeleteMapping
    public String disableEditing(
              @Parameter(description = "The name/url of the inquirer.")
              @RequestHeader(value = HttpHeaders.ORIGIN, required = false, defaultValue = "unknown")
              String originURL,
              @Parameter(description = "The literal name of the dataset.")
              @PathVariable
              String datasetName) {
        logger.info("Received DELETE request: \"/api/datasets/{{}}/readonly\" from \"{}\".", datasetName, originURL);

        disableEditingUseCase.disableEditing(datasetName);

        logger.info("Sending response to DELETE request: \"/api/datasets/{{}}/readonly\" to \"{}\".", datasetName, originURL);
        return Response.SUCCESS;
    }
}

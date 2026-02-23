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

package org.rdfarchitect.api.controller.snapshots;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.rdfarchitect.services.snapshot.CreateSnapshotUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("api/snapshots")
@RequiredArgsConstructor
public class AllSnapshotsRESTController {

    private static final Logger logger = LoggerFactory.getLogger(AllSnapshotsRESTController.class);

    private final CreateSnapshotUseCase createSnapshotUseCase;

    @Operation(
            summary = "snapshot dataset",
            description = "Creates a snapshot for a dataset and persists it in the database",
            tags = {"snapshot", "dataset"},
            responses = {
                    @ApiResponse(
                            responseCode = "200")
            }
    )
    @PostMapping
    public String createSnapshot(
            @Parameter(description = "The name/url of the inquirer.")
            @RequestHeader(value = HttpHeaders.ORIGIN, required = false, defaultValue = "unknown")
            String originURL,
            @Parameter(description = "The literal name of the dataset.")
            @RequestBody
            String datasetName) {
        logger.info("Received POST request: \"/api/snapshots\" from \"{}\".", originURL);

        var base64Token = createSnapshotUseCase.createSnapshot(datasetName);

        logger.info("Sending response to POST request: \"/api/snapshots\" from \"{}\".", originURL);
        return base64Token;
    }
}

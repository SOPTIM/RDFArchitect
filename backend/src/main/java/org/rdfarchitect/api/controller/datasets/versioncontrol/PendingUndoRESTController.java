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

package org.rdfarchitect.api.controller.datasets.versioncontrol;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import lombok.RequiredArgsConstructor;

import org.rdfarchitect.api.dto.ChangeLogEntryDTO;
import org.rdfarchitect.services.ChangeLogUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/datasets/{datasetName}/undo/pending")
@RequiredArgsConstructor
public class PendingUndoRESTController {

    private static final Logger logger = LoggerFactory.getLogger(PendingUndoRESTController.class);

    private final ChangeLogUseCase changelogUseCase;

    @Operation(
            summary = "peek at the next undo",
            description =
                    "Get the change that the next undo would take back, so that the editor can ask"
                            + " before an undo that makes something disappear. Returns nothing when"
                            + " there is no history left.",
            tags = {"workspace"},
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ChangeLogEntryDTO.class)))
            })
    @GetMapping
    public ChangeLogEntryDTO getPendingUndo(
            @Parameter(description = "The name/url of the inquirer.")
                    @RequestHeader(
                            value = HttpHeaders.ORIGIN,
                            required = false,
                            defaultValue = "unknown")
                    String originURL,
            @Parameter(description = "The literal name of the dataset.") @PathVariable
                    String datasetName) {
        logger.info(
                "Received GET request: \"/api/datasets/{{}}/undo/pending\" from \"{}\".",
                datasetName,
                originURL);

        var pending = changelogUseCase.pendingUndo(datasetName);

        logger.info(
                "Sending response to GET request: \"/api/datasets/{{}}/undo/pending\" to \"{}\".",
                datasetName,
                originURL);
        return pending;
    }
}

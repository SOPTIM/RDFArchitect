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

import lombok.RequiredArgsConstructor;

import org.rdfarchitect.services.versioncontrol.CanUndoUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/datasets/{datasetName}/canUndo")
@RequiredArgsConstructor
public class CanUndoRESTController {

    private static final Logger logger = LoggerFactory.getLogger(CanUndoRESTController.class);

    private final CanUndoUseCase canUndoUseCase;

    @Operation(
            summary = "can undo",
            description = "Check whether an undo operation is possible.",
            tags = {"workspace"})
    @PostMapping
    public boolean canUndo(
            @Parameter(description = "The name/url of the inquirer.")
                    @RequestHeader(
                            value = HttpHeaders.ORIGIN,
                            required = false,
                            defaultValue = "unknown")
                    String originURL,
            @Parameter(description = "The literal name of the dataset.") @PathVariable
                    String datasetName) {
        logger.info(
                "Received POST request: \"/api/datasets/{{}}/canUndo\" from \"{}\".",
                datasetName,
                originURL);

        boolean canUndo = canUndoUseCase.canUndo(datasetName);

        logger.info(
                "Sending response to POST request: \"/api/datasets/{{}}/canUndo\" to \"{}\".",
                datasetName,
                originURL);
        return canUndo;
    }
}

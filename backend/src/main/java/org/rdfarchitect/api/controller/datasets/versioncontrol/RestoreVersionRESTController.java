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
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import lombok.RequiredArgsConstructor;

import org.rdfarchitect.api.dto.HistoryStepDTO;
import org.rdfarchitect.models.changelog.RevertScope;
import org.rdfarchitect.services.versioncontrol.RestoreVersionUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("api/datasets/{datasetName}/restore")
@RequiredArgsConstructor
public class RestoreVersionRESTController {

    private static final Logger logger =
            LoggerFactory.getLogger(RestoreVersionRESTController.class);

    private final RestoreVersionUseCase restoreVersionUseCase;

    @Operation(
            summary = "restore a version",
            description =
                    "Puts the workspace back the way the given version left it, recorded as a new "
                            + "change. The scope can hold the restore to certain graphs; leaving "
                            + "it out restores the whole workspace.",
            tags = {"workspace"},
            responses = {@ApiResponse(responseCode = "200")})
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public HistoryStepDTO restoreVersion(
            @Parameter(description = "The name/url of the inquirer.")
                    @RequestHeader(
                            value = HttpHeaders.ORIGIN,
                            required = false,
                            defaultValue = "unknown")
                    String originURL,
            @Parameter(description = "The literal name of the dataset.") @PathVariable
                    String datasetName,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                            required = true,
                            description = "The ID of the version to restore.")
                    @RequestBody
                    RestoreVersionDTO dto) {

        logger.info(
                "Received POST request: \"/api/datasets/{{}}/restore\" from \"{}\".",
                datasetName,
                originURL);

        var step =
                restoreVersionUseCase.restoreVersion(
                        datasetName, UUID.fromString(dto.versionId), dto.scope());

        logger.info(
                "Sending response to POST request: \"/api/datasets/{{}}/restore\" to \"{}\".",
                datasetName,
                originURL);
        return step;
    }

    /**
     * @param versionId the change to restore to
     * @param graphUris the graphs to put back, or empty for the whole workspace
     */
    public record RestoreVersionDTO(String versionId, List<String> graphUris) {

        RevertScope scope() {
            return new RevertScope(graphUris == null ? Set.of() : Set.copyOf(graphUris));
        }
    }
}

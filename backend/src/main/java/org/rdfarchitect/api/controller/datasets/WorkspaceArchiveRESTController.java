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
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import lombok.RequiredArgsConstructor;

import org.rdfarchitect.services.archive.WorkspaceArchiveFormat;
import org.rdfarchitect.services.archive.exporting.ExportWorkspaceUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("api/datasets/{datasetName}/archive")
@RequiredArgsConstructor
public class WorkspaceArchiveRESTController {

    private static final Logger logger =
            LoggerFactory.getLogger(WorkspaceArchiveRESTController.class);

    private final ExportWorkspaceUseCase exportWorkspaceUseCase;

    @Operation(
            summary = "export workspace",
            description =
                    "Exports the whole workspace as a workspace archive: every graph with its"
                            + " custom SHACL, layout and custom diagrams, and the workspace's"
                            + " prefixes, diagrams, layout and cross-profile colours.",
            tags = {"dataset"},
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        content = @Content(mediaType = "application/zip"))
            })
    @GetMapping
    public ResponseEntity<byte[]> exportWorkspace(
            @Parameter(description = "The name/url of the inquirer.")
                    @RequestHeader(
                            value = HttpHeaders.ORIGIN,
                            required = false,
                            defaultValue = "unknown")
                    String originURL,
            @Parameter(description = "The literal name of the dataset.") @PathVariable
                    String datasetName) {
        logger.info(
                "Received GET request: \"/api/datasets/{{}}/archive\" from \"{}\".",
                datasetName,
                originURL);

        var archive = exportWorkspaceUseCase.exportWorkspace(datasetName);
        var headers = new HttpHeaders();
        headers.setAccessControlExposeHeaders(List.of(HttpHeaders.CONTENT_DISPOSITION));
        var response =
                ResponseEntity.ok()
                        .headers(headers)
                        .header(
                                HttpHeaders.CONTENT_DISPOSITION,
                                WorkspaceArchiveFormat.fileNameFor(datasetName))
                        .contentType(MediaType.parseMediaType("application/zip"))
                        .body(archive);

        logger.info(
                "Sending response to GET request: \"/api/datasets/{{}}/archive\" to \"{}\".",
                datasetName,
                originURL);
        return response;
    }
}

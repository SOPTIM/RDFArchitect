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

import org.rdfarchitect.api.controller.datasets.ImportJobRESTController.ImportJobCreatedResponse;
import org.rdfarchitect.services.archive.WorkspaceArchiveFormat;
import org.rdfarchitect.services.archive.exporting.ExportWorkspaceUseCase;
import org.rdfarchitect.services.update.graph.ImportJobUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** Exports a whole workspace as one workspace file and imports such a file as a new workspace. */
@RestController
@RequestMapping("api/datasets/{datasetName}")
@RequiredArgsConstructor
public class WorkspaceFileRESTController {

    private static final Logger logger = LoggerFactory.getLogger(WorkspaceFileRESTController.class);

    private final ExportWorkspaceUseCase exportWorkspaceUseCase;
    private final ImportJobUseCase importJobUseCase;

    @Operation(
            summary = "export workspace",
            description =
                    "Exports the whole workspace as a workspace file: every graph with its"
                            + " custom SHACL, layout and custom diagrams, and the workspace's"
                            + " prefixes, diagrams, layout and cross-profile colours.",
            tags = {"dataset"},
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        content = @Content(mediaType = "application/zip"))
            })
    @GetMapping("/export")
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
                "Received GET request: \"/api/datasets/{{}}/export\" from \"{}\".",
                datasetName,
                originURL);

        var workspaceFile = exportWorkspaceUseCase.exportWorkspace(datasetName);
        var headers = new HttpHeaders();
        headers.setAccessControlExposeHeaders(List.of(HttpHeaders.CONTENT_DISPOSITION));
        var response =
                ResponseEntity.ok()
                        .headers(headers)
                        .header(
                                HttpHeaders.CONTENT_DISPOSITION,
                                WorkspaceArchiveFormat.fileNameFor(datasetName))
                        .contentType(MediaType.parseMediaType("application/zip"))
                        .body(workspaceFile);

        logger.info(
                "Sending response to GET request: \"/api/datasets/{{}}/export\" to \"{}\".",
                datasetName,
                originURL);
        return response;
    }

    @Operation(
            summary = "Start importing a workspace file",
            description =
                    "Starts restoring a workspace file, as written by the workspace export, as a new workspace of the given name. Returns the id of the job to poll for progress at /api/datasets/{datasetName}/imports/{jobId}. The file is checked completely first: if anything in it is invalid, nothing is imported and the job's status lists every problem.",
            tags = {"dataset"},
            responses = {
                @ApiResponse(responseCode = "202", description = "Import started"),
                @ApiResponse(
                        responseCode = "409",
                        description = "An import is already running for this session")
            })
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportJobCreatedResponse> startWorkspaceImport(
            @Parameter(description = "The name/url of the inquirer.")
                    @RequestHeader(
                            value = HttpHeaders.ORIGIN,
                            required = false,
                            defaultValue = "unknown")
                    String originURL,
            @Parameter(description = "The name of the workspace to create.") @PathVariable
                    String datasetName,
            @Parameter(description = "The workspace file") @RequestParam("file")
                    MultipartFile file) {
        logger.info(
                "Received POST request: \"/api/datasets/{{}}/import\" from \"{}\".",
                datasetName,
                originURL);

        var jobId = importJobUseCase.startWorkspaceImport(datasetName, file);

        logger.info(
                "Sending response to POST request: \"/api/datasets/{{}}/import\" to \"{}\": job {}.",
                datasetName,
                originURL,
                jobId);
        return ResponseEntity.accepted().body(new ImportJobCreatedResponse(jobId));
    }
}

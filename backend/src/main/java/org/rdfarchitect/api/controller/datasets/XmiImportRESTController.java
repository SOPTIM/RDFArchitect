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

import org.rdfarchitect.api.controller.datasets.ImportJobRESTController.ImportJobCreatedResponse;
import org.rdfarchitect.services.update.graph.ImportJobUseCase;
import org.rdfarchitect.services.update.graph.ImportSelectionDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * Starts the two jobs of an Enterprise Architect XMI import: reading the exports, then building the
 * packages chosen from them. Both are followed through {@link ImportJobRESTController}.
 */
@RestController
@RequestMapping("api/workspaces/{workspaceName}/imports")
@RequiredArgsConstructor
public class XmiImportRESTController {

    private static final Logger logger = LoggerFactory.getLogger(XmiImportRESTController.class);

    private final ImportJobUseCase importJobUseCase;

    @Operation(
            summary = "Start reading Enterprise Architect XMI exports",
            description =
                    "Starts a background read of one or more Enterprise Architect XMI exports and returns the id of the job to poll for progress. The job reports the packages the exports hold, each of which can become a schema, and stores nothing; the workspace does not have to exist yet.",
            tags = {"dataset"},
            responses = {
                @ApiResponse(responseCode = "202", description = "Read started"),
                @ApiResponse(
                        responseCode = "409",
                        description = "An import is already running for this session")
            })
    @PostMapping(path = "/xmi", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportJobCreatedResponse> startXmiScan(
            @Parameter(description = "The name/url of the inquirer.")
                    @RequestHeader(
                            value = HttpHeaders.ORIGIN,
                            required = false,
                            defaultValue = "unknown")
                    String originURL,
            @Parameter(description = "The literal name of the workspace.") @PathVariable
                    String workspaceName,
            @Parameter(description = "The Enterprise Architect XMI exports to read")
                    @RequestParam("files")
                    List<MultipartFile> files) {
        logger.info(
                "Received POST request: \"/api/workspaces/{{}}/imports/xmi\" from \"{}\".",
                workspaceName,
                originURL);

        var jobId = importJobUseCase.startXmiScan(workspaceName, files);

        logger.info(
                "Sending response to POST request: \"/api/workspaces/{{}}/imports/xmi\" to \"{}\": job {}.",
                workspaceName,
                originURL,
                jobId);
        return ResponseEntity.accepted().body(new ImportJobCreatedResponse(jobId));
    }

    @Operation(
            summary = "Import the packages chosen from Enterprise Architect XMI exports",
            description =
                    "Starts a background import of the chosen packages, each becoming one schema of the workspace, and returns the id of the job to poll for progress. The exports are the ones the given read job holds, so they are not uploaded again; a read can be chosen from once.",
            tags = {"dataset"},
            responses = {
                @ApiResponse(responseCode = "202", description = "Import started"),
                @ApiResponse(responseCode = "404", description = "No such import job"),
                @ApiResponse(
                        responseCode = "409",
                        description =
                                "An import is already running for this session, the read has not finished yet, or the exports of that read are gone")
            })
    @PutMapping("/{jobId}/packages")
    public ResponseEntity<ImportJobCreatedResponse> importSelectedPackages(
            @Parameter(description = "The name/url of the inquirer.")
                    @RequestHeader(
                            value = HttpHeaders.ORIGIN,
                            required = false,
                            defaultValue = "unknown")
                    String originURL,
            @Parameter(description = "The literal name of the workspace.") @PathVariable
                    String workspaceName,
            @Parameter(description = "The id of the job that read the exports.") @PathVariable
                    UUID jobId,
            @Parameter(description = "What to import out of the export.") @RequestBody
                    ImportSelectionDTO selection) {
        logger.info(
                "Received PUT request: \"/api/workspaces/{{}}/imports/{{}}/packages\" from \"{}\": {} package(s).",
                workspaceName,
                jobId,
                originURL,
                selection.packages().size());

        var importJobId =
                importJobUseCase.startPackageImport(
                        workspaceName, jobId, selection.packages(), selection.diagramIds());

        logger.info(
                "Sending response to PUT request: \"/api/workspaces/{{}}/imports/{{}}/packages\" to \"{}\": job {}.",
                workspaceName,
                jobId,
                originURL,
                importJobId.orElse(null));
        return importJobId
                .map(id -> ResponseEntity.accepted().body(new ImportJobCreatedResponse(id)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }
}

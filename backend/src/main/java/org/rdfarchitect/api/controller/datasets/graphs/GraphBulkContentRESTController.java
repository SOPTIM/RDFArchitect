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

package org.rdfarchitect.api.controller.datasets.graphs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import lombok.RequiredArgsConstructor;

import org.rdfarchitect.services.update.graph.ImportJobUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("api/datasets/{datasetName}/graphs/content/imports")
@RequiredArgsConstructor
public class GraphBulkContentRESTController {

    private static final Logger logger =
            LoggerFactory.getLogger(GraphBulkContentRESTController.class);

    private final ImportJobUseCase importJobUseCase;

    /** Response to starting an import, identifying the job to poll for its progress. */
    public record ImportJobCreatedResponse(UUID jobId) {}

    @Operation(
            summary = "Start importing multiple graphs",
            description =
                    "Starts a background import of one or more rdf graphs into the dataset. Accepts multiple files and/or zip archives containing several graph files, and returns the id of the job to poll for progress.",
            tags = {"graph"},
            responses = {
                @ApiResponse(responseCode = "202", description = "Import started"),
                @ApiResponse(
                        responseCode = "409",
                        description = "An import is already running for this session")
            })
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportJobCreatedResponse> startImport(
            @Parameter(description = "The name/url of the inquirer.")
                    @RequestHeader(
                            value = HttpHeaders.ORIGIN,
                            required = false,
                            defaultValue = "unknown")
                    String originURL,
            @Parameter(description = "The literal name of the dataset.") @PathVariable
                    String datasetName,
            @Parameter(description = "The files containing the graph data") @RequestParam("files")
                    List<MultipartFile> files,
            @Parameter(description = "Optional graph URIs, one per file. Defaults to file names.")
                    @RequestParam(value = "graphUris", required = false)
                    List<String> graphUris) {
        logger.info(
                "Received POST request: \"/api/datasets/{{}}/graphs/content/imports\" from \"{}\".",
                datasetName,
                originURL);

        var jobId = importJobUseCase.startImport(datasetName, files, graphUris);

        logger.info(
                "Sending response to POST request: \"/api/datasets/{{}}/graphs/content/imports\" to \"{}\".",
                datasetName,
                originURL);
        return ResponseEntity.accepted().body(new ImportJobCreatedResponse(jobId));
    }

    @Operation(
            summary = "Get the progress of an import",
            description =
                    "Returns the progress of a running import and the result of a finished one, including the graphs that were imported, the files that failed and any warnings.",
            tags = {"graph"},
            responses = {
                @ApiResponse(responseCode = "200"),
                @ApiResponse(responseCode = "404", description = "No such import job")
            })
    @GetMapping("/{jobId}")
    public ResponseEntity<ImportJobUseCase.ImportJobStatus> getImportStatus(
            @Parameter(description = "The literal name of the dataset.") @PathVariable
                    String datasetName,
            @Parameter(description = "The id of the import job.") @PathVariable UUID jobId) {
        return importJobUseCase
                .getStatus(jobId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    @Operation(
            summary = "Cancel an import",
            description =
                    "Asks a running import to stop after the file it is currently importing. The graphs imported up to that point are kept.",
            tags = {"graph"},
            responses = {
                @ApiResponse(responseCode = "204", description = "Cancellation requested"),
                @ApiResponse(responseCode = "404", description = "No such import job")
            })
    @DeleteMapping("/{jobId}")
    public ResponseEntity<Void> cancelImport(
            @Parameter(description = "The name/url of the inquirer.")
                    @RequestHeader(
                            value = HttpHeaders.ORIGIN,
                            required = false,
                            defaultValue = "unknown")
                    String originURL,
            @Parameter(description = "The literal name of the dataset.") @PathVariable
                    String datasetName,
            @Parameter(description = "The id of the import job.") @PathVariable UUID jobId) {
        logger.info(
                "Received DELETE request: \"/api/datasets/{{}}/graphs/content/imports/{{}}\" from \"{}\".",
                datasetName,
                jobId,
                originURL);

        return importJobUseCase.cancel(jobId)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }
}

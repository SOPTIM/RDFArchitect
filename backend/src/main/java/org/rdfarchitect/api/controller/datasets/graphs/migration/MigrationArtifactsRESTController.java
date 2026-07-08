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

package org.rdfarchitect.api.controller.datasets.graphs.migration;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import lombok.RequiredArgsConstructor;

import org.apache.jena.riot.RDFFormat;
import org.rdfarchitect.context.MigrationSessionStore;
import org.rdfarchitect.services.schemamigration.artifacts.GenerateMigrationReportUseCase;
import org.rdfarchitect.services.schemamigration.artifacts.GenerateMigrationScriptUseCase;
import org.rdfarchitect.services.shacl.SHACLExportUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@RestController
@RequiredArgsConstructor
public class MigrationArtifactsRESTController {

    private static final Logger logger =
            LoggerFactory.getLogger(MigrationArtifactsRESTController.class);

    private final GenerateMigrationScriptUseCase generateMigrationScriptUseCase;
    private final SHACLExportUseCase shaclExportUseCase;
    private final MigrationSessionStore migrationSessionStore;
    private final GenerateMigrationReportUseCase generateMigrationReportUseCase;

    @Operation(
            summary = "generate migration script",
            description =
                    "Generates a migration script based on the previously computed migration actions and the shacl shapes of the new schema.",
            tags = {"migration"},
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        content = @Content(mediaType = "application/zip"))
            })
    @GetMapping("/api/migrations/export")
    public ResponseEntity<byte[]> generateMigrationScript(
            @Parameter(description = "The name/url of the inquirer.")
                    @RequestHeader(value = "origin", required = false, defaultValue = "unknown")
                    String originURL)
            throws IOException {
        logger.info("Received GET request: \"/api/migrations/export\" from \"{}\".", originURL);

        var updatedSchema = migrationSessionStore.getContext().getUpdatedSchema();
        var script = generateMigrationScriptUseCase.generateMigrationScript();
        var shacl = shaclExportUseCase.exportGeneratedSHACLGraph(updatedSchema, RDFFormat.TURTLE);

        var body = new ByteArrayOutputStream();
        try (var zipOut = new ZipOutputStream(body)) {
            zipOut.putNextEntry(new ZipEntry("migration.sparql"));
            zipOut.write(script.getBytes(StandardCharsets.UTF_8));
            zipOut.closeEntry();

            zipOut.putNextEntry(new ZipEntry("shacl.ttl"));
            shacl.writeTo(zipOut);
            zipOut.closeEntry();
        }
        var headers = new HttpHeaders();
        headers.setAccessControlExposeHeaders(List.of("Content-disposition"));
        var response =
                ResponseEntity.ok()
                        .headers(headers)
                        .header(HttpHeaders.CONTENT_DISPOSITION, "migration-package.zip")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .body(body.toByteArray());

        logger.info(
                "Sending response to GET request: \"/api/migrations/export\" from \"{}\".",
                originURL);

        return response;
    }

    @Operation(
            summary = "generate migration script",
            description =
                    "Generates a migration script based on the previously computed migration actions and the shacl shapes of the new schema.",
            tags = {"migration"},
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        content = @Content(mediaType = "application/zip"))
            })
    @GetMapping("/api/migrations/report")
    public ResponseEntity<byte[]> generateMigrationReport(
            @Parameter(description = "The name/url of the inquirer.")
                    @RequestHeader(value = "origin", required = false, defaultValue = "unknown")
                    String originURL,
            @Parameter(
                            description =
                                    "Type of the migration report to generate. Can be either SUMMARY or DETAILED.")
                    @RequestParam(value = "reportType", defaultValue = "SUMMARY")
                    MigrationReportType reportType) {
        logger.info("Received GET request: \"/api/migrations/report\" from \"{}\".", originURL);

        var report =
                switch (reportType) {
                    case SUMMARY -> generateMigrationReportUseCase.generateSummaryMigrationReport();
                    case DETAILED ->
                            generateMigrationReportUseCase.generateDetailedMigrationReport();
                };

        var body = report.getBytes(StandardCharsets.UTF_8);
        var headers = new HttpHeaders();
        headers.setAccessControlExposeHeaders(List.of("Content-disposition"));
        var response =
                ResponseEntity.ok()
                        .headers(headers)
                        .header(HttpHeaders.CONTENT_DISPOSITION, "migration_report.md")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .body(body);

        logger.info(
                "Sending response to GET request: \"/api/migrations/report\" from \"{}\".",
                originURL);

        return response;
    }

    public enum MigrationReportType {
        SUMMARY,
        DETAILED
    }
}

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
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import lombok.RequiredArgsConstructor;

import org.rdfarchitect.api.controller.DownloadHeaders;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.services.select.ExportGraphAsciiDocUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/workspaces/{workspaceName}/schemas/{schemaName}/asciidocexport/{fileEnding}")
@RequiredArgsConstructor
public class AsciiDocExportRESTController {

    private static final Logger logger =
            LoggerFactory.getLogger(AsciiDocExportRESTController.class);

    private static final MediaType ASCIIDOC = new MediaType("text", "asciidoc");

    private final ExportGraphAsciiDocUseCase exportGraphAsciiDocUseCase;

    @Operation(
            summary = "export graph as asciidoc",
            description = "Export the rdf-schema graph as asciidoc",
            tags = {"graph"},
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        content = {
                            @Content(mediaType = "text/asciidoc"),
                        })
            })
    @GetMapping
    public ResponseEntity<byte[]> getAsciiDocExport(
            @Parameter(description = "The name/url of the inquirer.")
                    @RequestHeader(
                            value = HttpHeaders.ORIGIN,
                            required = false,
                            defaultValue = "unknown")
                    String originURL,
            @Parameter(description = "The literal name of the dataset.") @PathVariable
                    String workspaceName,
            @Parameter(description = "The name of the schema.") @PathVariable String schemaName,
            @Parameter(description = "The file ending of the diagram files.") @PathVariable
                    String fileEnding,
            @Parameter(
                            description =
                                    "Whether the package diagram is shown in the document instead of only being linked.")
                    @RequestParam(defaultValue = "false")
                    boolean embedDiagrams) {
        logger.info(
                "Received GET request: \"/api/workspaces/{{}}/schemas/{{}}/asciidocexport\" from \"{}\".",
                workspaceName,
                schemaName,
                originURL);

        var output =
                exportGraphAsciiDocUseCase.exportGraphAsAsciiDoc(
                        new GraphIdentifier(workspaceName, schemaName), fileEnding, embedDiagrams);

        var fileName = schemaName;
        fileName += ".adoc";

        var headers = DownloadHeaders.attachment(fileName);
        headers.setContentType(ASCIIDOC);
        var body = ResponseEntity.ok().headers(headers).body(output);

        logger.info(
                "Sending response to GET request: \"/api/workspaces/{{}}/schemas/{{}}/asciidocexport\" to \"{}\".",
                workspaceName,
                schemaName,
                originURL);
        return body;
    }
}

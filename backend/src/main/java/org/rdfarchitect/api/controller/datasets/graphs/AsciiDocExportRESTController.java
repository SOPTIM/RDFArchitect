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

import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.models.cim.data.dto.relations.uri.URI;
import org.rdfarchitect.services.ExpandURIUseCase;
import org.rdfarchitect.services.select.ExportGraphAsciiDocUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("api/datasets/{datasetName}/graphs/{graphURI}/asciidocexport/{fileEnding}")
@RequiredArgsConstructor
public class AsciiDocExportRESTController {

    private static final Logger logger =
            LoggerFactory.getLogger(AsciiDocExportRESTController.class);

    private static final MediaType ASCIIDOC = new MediaType("text", "asciidoc");

    private final ExpandURIUseCase expandURIUseCase;
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
                    String datasetName,
            @Parameter(
                            description =
                                    "The url encoded uri of the graph, or \"default\" to access the default graph.")
                    @PathVariable
                    String graphURI,
            @Parameter(description = "The file ending of the diagram files.") @PathVariable
                    String fileEnding,
            @Parameter(
                            description =
                                    "Whether the package diagram is shown in the document instead of only being linked.")
                    @RequestParam(defaultValue = "false")
                    boolean embedDiagrams) {
        logger.info(
                "Received GET request: \"/api/datasets/{{}}/graphs/{{}}/asciidocexport\" from \"{}\".",
                datasetName,
                graphURI,
                originURL);

        var extendedGraphURI = expandURIUseCase.expandUri(datasetName, graphURI);

        var output =
                exportGraphAsciiDocUseCase.exportGraphAsAsciiDoc(
                        new GraphIdentifier(datasetName, extendedGraphURI),
                        fileEnding,
                        embedDiagrams);

        var fileName = "default";
        if (!extendedGraphURI.equals("default")) {
            fileName = new URI(extendedGraphURI).getSuffix();
        }
        fileName += ".adoc";

        var headers = new HttpHeaders();
        var contentDisposition = ContentDisposition.attachment().filename(fileName).build();
        headers.setContentDisposition(contentDisposition);
        headers.setContentType(ASCIIDOC);
        // without this the browser hides the header from the frontend, which would then have to
        // fall back to a generic file name
        headers.setAccessControlExposeHeaders(List.of(HttpHeaders.CONTENT_DISPOSITION));
        var body = ResponseEntity.ok().headers(headers).body(output);

        logger.info(
                "Sending response to GET request: \"/api/datasets/{{}}/graphs/{{}}/asciidocexport\" to \"{}\".",
                datasetName,
                graphURI,
                originURL);
        return body;
    }
}

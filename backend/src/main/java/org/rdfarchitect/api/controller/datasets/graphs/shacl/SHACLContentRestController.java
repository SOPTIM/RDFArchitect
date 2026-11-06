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

package org.rdfarchitect.api.controller.datasets.graphs.shacl;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import lombok.RequiredArgsConstructor;

import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.services.shacl.SHACLExportUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/workspaces/{workspaceName}/schemas/{schemaName}/shacl/combined")
@RequiredArgsConstructor
public class SHACLContentRestController {

    private static final Logger logger = LoggerFactory.getLogger(SHACLContentRestController.class);

    private final SHACLExportUseCase shaclExportUseCase;

    @Operation(
            summary = "export shacl",
            description =
                    "Export the combined rdf-shacl graph of the generated and custom shapes for a graph.",
            tags = {"shacl"},
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        content = {
                            @Content(mediaType = "text/turtle"),
                            @Content(mediaType = "application/rdf+xml"),
                            @Content(mediaType = "application/rdf+json"),
                            @Content(mediaType = "application/n-triples")
                        })
            })
    @GetMapping("/file")
    public ResponseEntity<byte[]> getCombinedSHACLAsFile(
            @Parameter(description = "The requested Datatype.", hidden = true)
                    @RequestHeader(value = HttpHeaders.ACCEPT, required = false)
                    String acceptHeader,
            @Parameter(description = "The name/url of the inquirer.")
                    @RequestHeader(
                            value = HttpHeaders.ORIGIN,
                            required = false,
                            defaultValue = "unknown")
                    String originURL,
            @Parameter(description = "The literal name of the dataset.") @PathVariable
                    String workspaceName,
            @Parameter(description = "The name of the schema.") @PathVariable String schemaName) {
        logger.info(
                "Received GET request: \"/api/workspaces/{{}}/schemas/{{}}/shacl/file\" from \"{}\".",
                workspaceName,
                schemaName,
                originURL);

        var format = SHACLFileResponse.rdfFormat(acceptHeader);

        // fetch data
        var outStream =
                shaclExportUseCase.exportCombinedSHACLGraph(
                        new GraphIdentifier(workspaceName, schemaName), format);
        var body = SHACLFileResponse.of(schemaName, format, outStream);

        logger.info(
                "Sending response to GET request: \"/api/workspaces/{{}}/schemas/{{}}/shacl/file\" to \"{}\".",
                workspaceName,
                schemaName,
                originURL);
        return body;
    }
}

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

package org.rdfarchitect.api.controller.datasets.shacl.export;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import lombok.RequiredArgsConstructor;

import org.rdfarchitect.api.controller.datasets.graphs.shacl.SHACLFileResponse;
import org.rdfarchitect.api.controller.datasets.shacl.SchemaParameter;
import org.rdfarchitect.services.shacl.SHACLExportUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Exporting a chosen set of a workspace's constraints.
 *
 * <p>The older endpoints offer three fixed combinations — generated, all enabled documents, or
 * both. Since a workspace holds several documents, which of them belongs in an export is a question
 * only the user can answer, so this one takes the answer.
 */
@RestController
@RequestMapping("api/workspaces/{workspaceName}/shacl/export")
@RequiredArgsConstructor
public class SHACLSelectionExportRESTController {

    private static final Logger logger =
            LoggerFactory.getLogger(SHACLSelectionExportRESTController.class);

    private final SHACLExportUseCase shaclExportUseCase;

    @Operation(
            summary = "export chosen constraints",
            description =
                    "Exports the named constraints documents as one file, optionally merged with the "
                            + "shapes generated from schemas. A document is included because it "
                            + "was asked for, whether or not it is enabled for validation. A single "
                            + "document exported as Turtle on its own is returned as its stored text, "
                            + "and a single document is named after itself; anything else after the "
                            + "workspace.",
            tags = {"shacl"},
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        content = {@Content(mediaType = "text/turtle")}),
                @ApiResponse(responseCode = "400", description = "A schema name is not valid."),
                @ApiResponse(
                        responseCode = "404",
                        description =
                                "A requested document or schema does not exist in this workspace."),
                @ApiResponse(
                        responseCode = "406",
                        description = "The Accept header names no supported RDF syntax.")
            })
    @GetMapping("/file")
    public ResponseEntity<byte[]> exportSelection(
            @Parameter(description = "The requested Datatype.", hidden = true)
                    @RequestHeader(value = HttpHeaders.ACCEPT, required = false)
                    String acceptHeader,
            @Parameter(description = "The name/url of the inquirer.")
                    @RequestHeader(
                            value = HttpHeaders.ORIGIN,
                            required = false,
                            defaultValue = "unknown")
                    String originURL,
            @Parameter(description = "The name of the workspace.") @PathVariable
                    String workspaceName,
            @Parameter(description = "The constraints documents to include.")
                    @RequestParam(name = "documentId", required = false)
                    List<UUID> documentIds,
            @Parameter(description = "Names of the schemas whose generated shapes to merge in.")
                    @RequestParam(name = "generatedFor", required = false)
                    List<String> generatedFor) {
        logger.info(
                "Received GET request: \"/api/workspaces/{{}}/shacl/export/file\" from \"{}\".",
                workspaceName,
                originURL);

        var format = SHACLFileResponse.rdfFormat(acceptHeader);
        // An empty documentId= arrives as null, and names nothing.
        var documents =
                documentIds == null
                        ? List.<UUID>of()
                        : documentIds.stream().filter(Objects::nonNull).toList();
        var schemas = SchemaParameter.names(generatedFor);
        var outStream =
                shaclExportUseCase.exportSelectedSHACLGraph(
                        workspaceName, format, documents, schemas);

        return SHACLFileResponse.named(
                shaclExportUseCase.selectionFileStem(workspaceName, documents, schemas),
                format,
                outStream);
    }
}

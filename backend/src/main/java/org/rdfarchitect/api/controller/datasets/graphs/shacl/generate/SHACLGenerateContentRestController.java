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

package org.rdfarchitect.api.controller.datasets.graphs.shacl.generate;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import lombok.RequiredArgsConstructor;

import org.apache.jena.riot.RDFFormat;
import org.apache.jena.riot.system.PrefixEntry;
import org.rdfarchitect.api.controller.DownloadHeaders;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.models.cim.rdf.resources.RDFA;
import org.rdfarchitect.services.shacl.SHACLExportUseCase;
import org.rdfarchitect.services.shacl.SHACLGenerateUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayOutputStream;
import java.util.AbstractMap;
import java.util.Map;

@RestController
@RequestMapping("api/workspaces/{workspaceName}/schemas/{schemaName}/shacl/generate")
@RequiredArgsConstructor
public class SHACLGenerateContentRestController {

    private static final Logger logger =
            LoggerFactory.getLogger(SHACLGenerateContentRestController.class);

    private final SHACLExportUseCase shaclExportUseCase;

    private final SHACLGenerateUseCase shaclGenerateUseCase;

    @Operation(
            summary = "generate shacl",
            description = "generate shacl for the whole graph and return it as a Turtle string.",
            tags = {"shacl"})
    @GetMapping("/string")
    public String getGeneratedSHACLAsString(
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
                "Received GET request: \"/api/workspaces/{{}}/schemas/{{}}/shacl/generate/string\" from \"{}\".",
                workspaceName,
                schemaName,
                originURL);

        var result =
                shaclGenerateUseCase.exportGeneratedSHACLGraph(
                        new GraphIdentifier(workspaceName, schemaName),
                        PrefixEntry.create(RDFA.NS_PREFIX_SHACL, RDFA.NS_URI_SHACL));

        logger.info(
                "Sending response to GET request: \"/api/workspaces/{{}}/schemas/{{}}/shacl/generate/string\" to \"{}\".",
                workspaceName,
                schemaName,
                originURL);
        return result;
    }

    @Operation(
            summary = "export shacl",
            description = "Export the rdf-shacl graph as a file",
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
    public ResponseEntity<byte[]> getGeneratedSHACLAsFile(
            @Parameter(description = "The requested Datatype.", hidden = true)
                    @RequestHeader("Accept")
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
                "Received GET request: \"/api/workspaces/{{}}/schemas/{{}}/shacl/generate/file\" from \"{}\".",
                workspaceName,
                schemaName,
                originURL);

        var format = getRdfFormat(acceptHeader);

        // fetch data
        var outStream =
                shaclExportUseCase.exportGeneratedSHACLGraph(
                        new GraphIdentifier(workspaceName, schemaName), format);
        var body = buildResponseEntity(schemaName, format, outStream);

        logger.info(
                "Sending response to GET request: \"/api/workspaces/{{}}/schemas/{{}}/shacl/generate/file\" to \"{}\".",
                workspaceName,
                schemaName,
                originURL);
        return body;
    }

    private ResponseEntity<byte[]> buildResponseEntity(
            String schemaName, RDFFormat format, ByteArrayOutputStream outStream) {
        // add suggested file name to response
        var fileName = schemaName + "-shacl";
        fileName += "." + format.getLang().getFileExtensions().getFirst();

        return ResponseEntity.ok()
                .headers(DownloadHeaders.attachment(fileName))
                .body(outStream.toByteArray());
    }

    private final Map<String, RDFFormat> supportedFormats =
            Map.ofEntries(
                    new AbstractMap.SimpleEntry<>("text/turtle", RDFFormat.TURTLE),
                    new AbstractMap.SimpleEntry<>("application/rdf+xml", RDFFormat.RDFXML),
                    new AbstractMap.SimpleEntry<>("application/rdf+json", RDFFormat.RDFJSON),
                    new AbstractMap.SimpleEntry<>("application/n-triples", RDFFormat.NTRIPLES));

    private RDFFormat getRdfFormat(String acceptHeader) {
        for (var entry : supportedFormats.entrySet()) {
            if (acceptHeader.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        throw new IllegalArgumentException("unsupported Media Type");
    }
}

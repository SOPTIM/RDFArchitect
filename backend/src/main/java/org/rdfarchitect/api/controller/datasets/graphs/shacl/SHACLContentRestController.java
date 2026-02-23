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
import org.apache.jena.riot.RDFFormat;
import org.rdfarchitect.cim.data.dto.relations.uri.URI;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.services.ExpandURIUseCase;
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

import java.io.ByteArrayOutputStream;
import java.util.AbstractMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("api/datasets/{datasetName}/graphs/{graphURI}/shacl/combined")
@RequiredArgsConstructor
public class SHACLContentRestController {

    private static final Logger logger = LoggerFactory.getLogger(SHACLContentRestController.class);

    private final ExpandURIUseCase expandURIUseCase;

    private final SHACLExportUseCase shaclExportUseCase;

    @Operation(
            summary = "export shacl",
            description = "Export the combined rdf-shacl graph of the generated and custom shapes for a graph.",
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
            }
    )
    @GetMapping("/file")
    public ResponseEntity<byte[]> getCombinedSHACLAsFile(
            @Parameter(description = "The requested Datatype.", hidden = true)
            @RequestHeader("Accept")
            String acceptHeader,
            @Parameter(description = "The name/url of the inquirer.")
            @RequestHeader(value = HttpHeaders.ORIGIN, required = false, defaultValue = "unknown")
            String originURL,
            @Parameter(description = "The literal name of the dataset.")
            @PathVariable
            String datasetName,
            @Parameter(description = "The url encoded uri of the graph, or \"default\" to access the default graph.")
            @PathVariable
            String graphURI) {
        logger.info("Received GET request: \"/api/datasets/{{}}/graphs/{{}}/shacl/file\" from \"{}\".", datasetName, graphURI, originURL);

        var extendedGraphURI = expandURIUseCase.expandUri(datasetName, graphURI);
        var format = getRdfFormat(acceptHeader);

        //fetch data
        var outStream = shaclExportUseCase.exportCombinedSHACLGraph(new GraphIdentifier(datasetName, extendedGraphURI), format);
        var body = buildResponseEntity(extendedGraphURI, format, outStream);

        logger.info("Sending response to GET request: \"/api/datasets/{{}}/graphs/{{}}/shacl/file\" to \"{}\".", datasetName, graphURI, originURL);
        return body;
    }

    private ResponseEntity<byte[]> buildResponseEntity(String extendedGraphURI, RDFFormat format, ByteArrayOutputStream outStream) {
        //add suggested file name to response
        var fileName = "shacl";
        if (!extendedGraphURI.equals("default")) {
            fileName = new URI(extendedGraphURI + "-shacl").getSuffix();
        }
        fileName += "." + format.getLang().getFileExtensions().getFirst();

        var headers = new HttpHeaders();
        headers.setAccessControlExposeHeaders(List.of("Content-Disposition"));
        return ResponseEntity.ok()
                .headers(headers)
                .header(HttpHeaders.CONTENT_DISPOSITION, fileName)
                .body(outStream.toByteArray());
    }

    private final Map<String, RDFFormat> supportedFormats = Map.ofEntries(
            new AbstractMap.SimpleEntry<>("text/turtle", RDFFormat.TURTLE),
            new AbstractMap.SimpleEntry<>("application/rdf+xml", RDFFormat.RDFXML),
            new AbstractMap.SimpleEntry<>("application/rdf+json", RDFFormat.RDFJSON),
            new AbstractMap.SimpleEntry<>("application/n-triples", RDFFormat.NTRIPLES)
    );

    private RDFFormat getRdfFormat(String acceptHeader) {
        for (var entry : supportedFormats.entrySet()) {
            if (acceptHeader.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        throw new IllegalArgumentException("unsupported Media Type");
    }
}

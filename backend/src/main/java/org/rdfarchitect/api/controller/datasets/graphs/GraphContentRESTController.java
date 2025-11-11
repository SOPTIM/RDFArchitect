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
import org.apache.jena.riot.RDFFormat;
import org.rdfarchitect.cim.data.dto.relations.uri.URI;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.services.ExpandURIUseCase;
import org.rdfarchitect.services.select.GetSchemaUseCase;
import org.rdfarchitect.services.update.graph.DeleteGraphUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.AbstractMap;
import java.util.List;
import java.util.Map;

@CrossOrigin
@RestController
@RequestMapping("api/datasets/{datasetName}/graphs/{graphURI}/content")
@RequiredArgsConstructor
public class GraphContentRESTController {

    private static final Logger logger = LoggerFactory.getLogger(GraphContentRESTController.class);

    private final ExpandURIUseCase expandURIUseCase;
    private final GetSchemaUseCase getSchemaUseCase;
    private final DeleteGraphUseCase deleteGraphUseCase;

    @Operation(
            summary = "export graph",
            description = "Export the rdf-schema graph",
            tags = {"graph"},
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
    @GetMapping
    public ResponseEntity<byte[]> getSchema(
            @Parameter(description = "The requested Datatype.", hidden = true)
            @RequestHeader("Accept")
            String acceptHeader,
            @Parameter(description = "The name/url of the inquirer.")
            @RequestHeader(value = "origin", required = false, defaultValue = "unknown")
            String originURL,
            @Parameter(description = "The literal name of the dataset.")
            @PathVariable
            String datasetName,
            @Parameter(description = "The url encoded uri of the graph, or \"default\" to access the default graph.")
            @PathVariable
            String graphURI) {
        logger.info("Received GET request: \"/api/datasets/{{}}/graphs/{{}}/content\" from \"{}\".", datasetName, graphURI, originURL);

        var extendedGraphURI = expandURIUseCase.expandUri(datasetName, graphURI);
        var format = getRdfFormat(acceptHeader);

        //fetch data
        var outStream = getSchemaUseCase.getSchema(new GraphIdentifier(datasetName, extendedGraphURI), format);

        //add suggested file name to response
        var fileName = "default";
        if (!extendedGraphURI.equals("default")) {
            fileName = new URI(extendedGraphURI).getSuffix();
        }
        fileName += "." + format.getLang().getFileExtensions().get(0);

        var headers = new HttpHeaders();
        headers.setAccessControlExposeHeaders(List.of("Content-Disposition"));
        var body = ResponseEntity.ok()
                .headers(headers)
                .header(HttpHeaders.CONTENT_DISPOSITION, fileName)
                .body(outStream.toByteArray());
        logger.info("Sending response to GET request: \"/api/datasets/{{}}/graphs/{{}}/content\" to \"{}\".", datasetName, graphURI, originURL);
        return body;
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

    @Operation(
            summary = "delete graph",
            description = "Delete an rdf-schema graph",
            tags = {"graph"},
            responses = {
                    @ApiResponse(
                            responseCode = "200")
            }
    )
    @DeleteMapping
    public String deleteGraph(
            @Parameter(description = "The name/url of the inquirer.") @RequestHeader(value = "origin", required = false, defaultValue = "unknown")
            String originURL,
            @Parameter(description = "The literal name of the dataset.") @PathVariable
            String datasetName,
            @Parameter(description = "The url encoded uri of the graph, or \"default\" to access the default graph.") @PathVariable
            String graphURI) {
        logger.info("Received DELETE request: \"/api/datasets/{{}}/graphs/{{}}/content\" from \"{}\".", datasetName, graphURI, originURL);

        deleteGraphUseCase.deleteGraph(new GraphIdentifier(datasetName, graphURI));

        logger.info("Sending response to DELETE request: \"/api/datasets/{{}}/graphs/{{}}/content\" to \"{}\".", datasetName, graphURI, originURL);
        return "success";
    }

}

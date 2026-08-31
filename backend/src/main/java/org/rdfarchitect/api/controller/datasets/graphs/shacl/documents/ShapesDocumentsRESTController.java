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

package org.rdfarchitect.api.controller.datasets.graphs.shacl.documents;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import lombok.RequiredArgsConstructor;

import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFLanguages;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.exception.database.DataAccessException;
import org.rdfarchitect.services.ExpandURIUseCase;
import org.rdfarchitect.services.shacl.SHACLDocumentUseCase;
import org.rdfarchitect.shacl.dto.ShapesDocumentInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * The sets of SHACL shapes a graph holds.
 *
 * <p>A graph can hold any number of them — typically the official constraints files of a profile
 * plus whatever was authored here. They are not ranked: SHACL is conjunctive, so every enabled
 * document applies and none overrides another.
 */
@RestController
@RequestMapping("api/datasets/{datasetName}/graphs/{graphURI}/shacl/documents")
@RequiredArgsConstructor
public class ShapesDocumentsRESTController {

    private static final Logger logger =
            LoggerFactory.getLogger(ShapesDocumentsRESTController.class);

    private final ExpandURIUseCase expandURIUseCase;

    private final SHACLDocumentUseCase shaclDocumentUseCase;

    @Operation(
            summary = "list constraints documents",
            description =
                    "Lists the sets of SHACL shapes stored for a graph, in order, without their content.",
            tags = {"shacl"},
            responses = {@ApiResponse(responseCode = "200")})
    @GetMapping
    public List<ShapesDocumentInfo> listShapesDocuments(
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
                    String graphURI) {
        logger.info(
                "Received GET request: \"/api/datasets/{{}}/graphs/{{}}/shacl/documents\" from \"{}\".",
                datasetName,
                graphURI,
                originURL);

        return shaclDocumentUseCase.listShapesDocuments(graphIdentifier(datasetName, graphURI));
    }

    @Operation(
            summary = "add a constraints document from a file",
            description =
                    "Uploads a set of SHACL shapes. TTL, RDF/XML and N-Triples are accepted; anything "
                            + "other than Turtle is converted to Turtle on import.",
            tags = {"shacl"},
            responses = {@ApiResponse(responseCode = "200")})
    @PostMapping(path = "/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ShapesDocumentInfo createShapesDocumentFromFile(
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
            @Parameter(description = "The file containing the SHACL shapes.") @RequestParam("file")
                    MultipartFile file,
            @Parameter(description = "Display name; defaults to the file name.")
                    @RequestParam(value = "name", required = false)
                    String name) {
        logger.info(
                "Received POST request: \"/api/datasets/{{}}/graphs/{{}}/shacl/documents/file\" from \"{}\".",
                datasetName,
                graphURI,
                originURL);

        var fileName = file.getOriginalFilename();
        var lang = RDFLanguages.filenameToLang(fileName, Lang.TURTLE);
        return shaclDocumentUseCase.createShapesDocument(
                graphIdentifier(datasetName, graphURI),
                name != null && !name.isBlank() ? name : fileName,
                fileName,
                readFile(file),
                lang);
    }

    @Operation(
            summary = "add a constraints document from Turtle",
            description = "Creates a set of SHACL shapes from a Turtle string.",
            tags = {"shacl"},
            responses = {@ApiResponse(responseCode = "200")})
    // Raw text, not JSON: Spring reads a String @RequestBody verbatim, so a JSON-quoted
    // body would reach Jena with its surrounding quotes and fail to parse.
    @PostMapping(consumes = MediaType.TEXT_PLAIN_VALUE)
    public ShapesDocumentInfo createShapesDocument(
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
            @Parameter(description = "Display name of the new document.") @RequestParam("name")
                    String name,
            @Parameter(description = "The SHACL shapes in Turtle syntax.") @RequestBody
                    String turtle) {
        logger.info(
                "Received POST request: \"/api/datasets/{{}}/graphs/{{}}/shacl/documents\" from \"{}\".",
                datasetName,
                graphURI,
                originURL);

        return shaclDocumentUseCase.createShapesDocument(
                graphIdentifier(datasetName, graphURI), name, null, turtle, Lang.TURTLE);
    }

    private GraphIdentifier graphIdentifier(String datasetName, String graphURI) {
        return new GraphIdentifier(datasetName, expandURIUseCase.expandUri(datasetName, graphURI));
    }

    private static String readFile(MultipartFile file) {
        try {
            return new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new DataAccessException(
                    "Unable to read constraints file " + file.getOriginalFilename(), e);
        }
    }
}

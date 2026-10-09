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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.apache.jena.riot.Lang;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.sparql.graph.GraphFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.api.controller.datasets.graphs.shacl.custom.SHACLCustomShapeRESTController;
import org.rdfarchitect.api.controller.datasets.graphs.shacl.documents.ShapesDocumentRESTController;
import org.rdfarchitect.api.controller.datasets.graphs.shacl.documents.ShapesDocumentsRESTController;
import org.rdfarchitect.api.controller.datasets.graphs.shacl.export.SHACLSelectionExportRESTController;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphContext;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.database.inmemory.GraphWithContextTransactional;
import org.rdfarchitect.exception.handlers.GenericExceptionHandler;
import org.rdfarchitect.services.ExpandURIUseCase;
import org.rdfarchitect.services.shacl.SHACLStoringService;
import org.rdfarchitect.shacl.dto.ShapesDocumentInfo;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * The shapes-document endpoints answered end to end: real service, real controller advice.
 *
 * <p>Mocked services cannot show what status a failure reaches the client with, and that is what
 * the workbench branches on — a taken name, an unknown document and unreadable Turtle each get a
 * different message.
 */
class ShapesDocumentsApiTest {

    private static final String GRAPH_URI = "http://example.org/EQ";

    private static final String BASE =
            "/api/datasets/cgmes/graphs/http%3A%2F%2Fexample.org%2FEQ/shacl";

    private static final String DOCUMENTS = BASE + "/documents";

    private static final String SHAPES =
            """
            @prefix sh: <http://www.w3.org/ns/shacl#> .
            @prefix ex: <http://example.org/> .

            # kept as written
            ex:LineShape a sh:NodeShape ; sh:targetClass ex:Line .
            """;

    private SHACLStoringService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        var context = new GraphWithContextTransactional(GraphFactory.createDefaultGraph());
        var databasePort = mock(DatabasePort.class);
        when(databasePort.getGraphWithContext(any(GraphIdentifier.class))).thenReturn(context);
        when(databasePort.getPrefixMapping(any())).thenReturn(PrefixMapping.Factory.create());
        service = new SHACLStoringService(databasePort);

        var expandURIUseCase = mock(ExpandURIUseCase.class);
        // MockMvc encodes the already-encoded path once more, so the controller receives it
        // encoded once — which is what the real ExpandURIUseCase decodes.
        when(expandURIUseCase.expandUri(any(), any()))
                .thenAnswer(
                        call ->
                                URLDecoder.decode(
                                        call.<String>getArgument(1), StandardCharsets.UTF_8));

        mockMvc =
                MockMvcBuilders.standaloneSetup(
                                new ShapesDocumentsRESTController(expandURIUseCase, service),
                                new ShapesDocumentRESTController(expandURIUseCase, service),
                                new SHACLSelectionExportRESTController(expandURIUseCase, service),
                                new SHACLCustomShapeRESTController(
                                        expandURIUseCase, service, service))
                        .setControllerAdvice(new GenericExceptionHandler())
                        .build();
    }

    private ShapesDocumentInfo givenDocument(String name, String turtle) {
        return service.createShapesDocument(
                new GraphIdentifier("cgmes", GRAPH_URI), name, name, turtle, Lang.TURTLE);
    }

    private String textOf(UUID documentId) {
        return service.getShapesDocumentText(new GraphIdentifier("cgmes", GRAPH_URI), documentId);
    }

    private UUID onlyUploadedDocument() {
        return service.listShapesDocuments(new GraphIdentifier("cgmes", GRAPH_URI)).stream()
                .filter(document -> !document.isDefault())
                .map(ShapesDocumentInfo::getId)
                .findFirst()
                .orElseThrow();
    }

    // -------------------------------------------------------------------------
    // Upload
    // -------------------------------------------------------------------------

    @Test
    void jsonLdIsRefusedBeforeItIsParsed() throws Exception {
        // A remote @context would make the server fetch whatever URL the file names.
        var file =
                new MockMultipartFile(
                        "file",
                        "shapes.jsonld",
                        "application/ld+json",
                        "{\"@context\": \"http://127.0.0.1:1/ctx\"}"
                                .getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart(DOCUMENTS + "/file").file(file))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(
                        result ->
                                assertThat(result.getResponse().getContentAsString())
                                        .contains("Turtle"));

        assertThat(service.listShapesDocuments(new GraphIdentifier("cgmes", GRAPH_URI))).hasSize(1);
    }

    @Test
    void aFileOfUnknownKindIsRefusedEvenIfItHappensToBeTurtle() throws Exception {
        var file =
                new MockMultipartFile(
                        "file",
                        "shapes.bin",
                        MediaType.APPLICATION_OCTET_STREAM_VALUE,
                        SHAPES.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart(DOCUMENTS + "/file").file(file))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void theContentTypeNamesTheSyntaxWhenTheFileNameDoesNot() throws Exception {
        var file =
                new MockMultipartFile(
                        "file", "shapes", "text/turtle", SHAPES.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart(DOCUMENTS + "/file").file(file)).andExpect(status().isOk());

        assertThat(textOf(onlyUploadedDocument())).isEqualTo(SHAPES);
    }

    @Test
    void aTurtleByteOrderMarkIsNotPartOfTheDocument() throws Exception {
        var bytes = ("﻿" + SHAPES).getBytes(StandardCharsets.UTF_8);
        var file = new MockMultipartFile("file", "shapes.ttl", "text/turtle", bytes);

        mockMvc.perform(multipart(DOCUMENTS + "/file").file(file)).andExpect(status().isOk());

        assertThat(textOf(onlyUploadedDocument())).isEqualTo(SHAPES);
    }

    @Test
    void rdfXmlIsReadInTheEncodingItDeclares() throws Exception {
        var xml =
                """
                <?xml version="1.0" encoding="ISO-8859-1"?>
                <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
                         xmlns:sh="http://www.w3.org/ns/shacl#">
                  <sh:NodeShape rdf:about="http://example.org/LineShape">
                    <sh:message>Leitungslänge fehlt</sh:message>
                  </sh:NodeShape>
                </rdf:RDF>
                """;
        var file =
                new MockMultipartFile(
                        "file",
                        "shapes.rdf",
                        "application/rdf+xml",
                        xml.getBytes(StandardCharsets.ISO_8859_1));

        mockMvc.perform(multipart(DOCUMENTS + "/file").file(file)).andExpect(status().isOk());

        assertThat(textOf(onlyUploadedDocument())).contains("Leitungslänge fehlt");
    }

    @Test
    void anRdfXmlByteOrderMarkIsTolerated() throws Exception {
        var xml =
                "﻿<rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">"
                        + "<rdf:Description rdf:about=\"http://example.org/LineShape\">"
                        + "<rdf:type rdf:resource=\"http://www.w3.org/ns/shacl#NodeShape\"/>"
                        + "</rdf:Description></rdf:RDF>";
        var file =
                new MockMultipartFile(
                        "file",
                        "shapes.rdf",
                        "application/rdf+xml",
                        xml.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart(DOCUMENTS + "/file").file(file)).andExpect(status().isOk());

        assertThat(textOf(onlyUploadedDocument())).contains("LineShape");
    }

    @Test
    void unreadableRdfXmlIsTheClientsMistake() throws Exception {
        var file =
                new MockMultipartFile(
                        "file",
                        "shapes.rdf",
                        "application/rdf+xml",
                        "<rdf:RDF".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart(DOCUMENTS + "/file").file(file))
                .andExpect(status().isBadRequest());
    }

    // -------------------------------------------------------------------------
    // Plain-text bodies
    // -------------------------------------------------------------------------

    @Test
    void aTurtleBodyMayBeLabelledAsTurtle() throws Exception {
        mockMvc.perform(
                        post(DOCUMENTS)
                                .param("name", "mine.ttl")
                                .contentType("text/turtle")
                                .content(SHAPES))
                .andExpect(status().isOk());
    }

    @Test
    void aDocumentsTextMayBeReplacedWithABodyLabelledAsTurtle() throws Exception {
        var document = givenDocument("mine.ttl", SHAPES);

        mockMvc.perform(
                        put(DOCUMENTS + "/" + document.getId())
                                .contentType("text/turtle")
                                .content(SHAPES))
                .andExpect(status().isOk());
    }

    // -------------------------------------------------------------------------
    // One document
    // -------------------------------------------------------------------------

    @Test
    void anUnknownDocumentIsNotFound() throws Exception {
        var unknown = DOCUMENTS + "/" + UUID.randomUUID();

        mockMvc.perform(get(unknown)).andExpect(status().isNotFound());
        mockMvc.perform(put(unknown).contentType(MediaType.TEXT_PLAIN).content(SHAPES))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch(unknown).param("enabled", "false")).andExpect(status().isNotFound());
        mockMvc.perform(delete(unknown)).andExpect(status().isNotFound());
    }

    @Test
    void unreadableTurtleIsRefusedWithWhereItWentWrong() throws Exception {
        var document = givenDocument("mine.ttl", SHAPES);

        mockMvc.perform(
                        put(DOCUMENTS + "/" + document.getId())
                                .contentType(MediaType.TEXT_PLAIN)
                                .content("@prefix ex: <http://example.org/> .\nex:a ex:b"))
                .andExpect(status().isBadRequest())
                .andExpect(
                        result ->
                                assertThat(result.getResponse().getContentAsString())
                                        .contains("line"));
    }

    @Test
    void renamingOntoATakenNameIsAConflict() throws Exception {
        givenDocument("eq.ttl", SHAPES);
        var other = givenDocument("tp.ttl", SHAPES);

        mockMvc.perform(patch(DOCUMENTS + "/" + other.getId()).param("name", "eq.ttl"))
                .andExpect(status().isConflict());
    }

    @Test
    void theDefaultDocumentCannotBeDeleted() throws Exception {
        mockMvc.perform(delete(DOCUMENTS + "/" + GraphContext.DEFAULT_SHAPES_DOCUMENT_ID))
                .andExpect(status().isConflict());
    }

    @Test
    void deletingAShapeTheDefaultDocumentDoesNotHoldIsNotFound() throws Exception {
        // The shape exists, in another document — which this deprecated endpoint never writes.
        givenDocument("eq.ttl", SHAPES);

        mockMvc.perform(delete(BASE + "/custom/http%3A%2F%2Fexample.org%2FLineShape"))
                .andExpect(status().isNotFound());
    }

    // -------------------------------------------------------------------------
    // Selection export
    // -------------------------------------------------------------------------

    @Test
    void oneDocumentExportedAsTurtleIsItsOwnText() throws Exception {
        var document = givenDocument("eq.ttl", SHAPES);

        mockMvc.perform(
                        get(BASE + "/export/file")
                                .param("documentId", document.getId().toString())
                                .header(HttpHeaders.ACCEPT, "text/turtle"))
                .andExpect(status().isOk())
                .andExpect(
                        result ->
                                assertThat(
                                                result.getResponse()
                                                        .getContentAsString(StandardCharsets.UTF_8))
                                        .isEqualTo(SHAPES));
    }

    @Test
    void severalDocumentsAreMergedRatherThanConcatenated() throws Exception {
        var first = givenDocument("eq.ttl", SHAPES);
        var second =
                givenDocument(
                        "tp.ttl",
                        "@prefix ex: <http://example.org/> .\nex:TerminalShape ex:p ex:o .\n");

        mockMvc.perform(
                        get(BASE + "/export/file")
                                .param("documentId", first.getId().toString())
                                .param("documentId", second.getId().toString())
                                .header(HttpHeaders.ACCEPT, "text/turtle"))
                .andExpect(status().isOk())
                .andExpect(
                        result ->
                                assertThat(result.getResponse().getContentAsString())
                                        .contains("LineShape")
                                        .contains("TerminalShape")
                                        .doesNotContain("# kept as written"));
    }

    @Test
    void anExportWithoutAnAcceptHeaderIsTurtle() throws Exception {
        var document = givenDocument("eq.ttl", SHAPES);

        mockMvc.perform(get(BASE + "/export/file").param("documentId", document.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(
                        result ->
                                assertThat(result.getResponse().getHeader("Content-Disposition"))
                                        .endsWith(".ttl"));
    }

    @Test
    void anExportInAnUnsupportedSyntaxIsNotAcceptable() throws Exception {
        var document = givenDocument("eq.ttl", SHAPES);

        mockMvc.perform(
                        get(BASE + "/export/file")
                                .param("documentId", document.getId().toString())
                                .header(HttpHeaders.ACCEPT, "application/ld+json"))
                .andExpect(status().isNotAcceptable());
    }

    @Test
    void anExportNamingAnUnknownDocumentIsNotFound() throws Exception {
        var document = givenDocument("eq.ttl", SHAPES);

        mockMvc.perform(
                        get(BASE + "/export/file")
                                .param("documentId", document.getId().toString())
                                .param("documentId", UUID.randomUUID().toString())
                                .header(HttpHeaders.ACCEPT, "text/turtle"))
                .andExpect(status().isNotFound());
    }
}

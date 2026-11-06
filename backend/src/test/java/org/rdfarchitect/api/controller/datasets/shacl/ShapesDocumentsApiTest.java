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

package org.rdfarchitect.api.controller.datasets.shacl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.sparql.graph.GraphFactory;
import org.apache.jena.vocabulary.RDF;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.api.controller.datasets.shacl.documents.ShapesDocumentRESTController;
import org.rdfarchitect.api.controller.datasets.shacl.documents.ShapesDocumentsRESTController;
import org.rdfarchitect.api.controller.datasets.shacl.export.SHACLSelectionExportRESTController;
import org.rdfarchitect.config.SchemaConfig;
import org.rdfarchitect.context.SessionContext;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseAdapter;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseImpl;
import org.rdfarchitect.exception.handlers.GenericExceptionHandler;
import org.rdfarchitect.services.ExpandURIUseCase;
import org.rdfarchitect.services.shacl.SHACLDocumentUseCase;
import org.rdfarchitect.services.shacl.SHACLStoringService;
import org.rdfarchitect.services.shacl.ShapesTurtleParser;
import org.rdfarchitect.shacl.dto.ShapesDocumentInfo;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * The shapes-document endpoints answered end to end: real service, real controller advice.
 *
 * <p>Mocked services cannot show what status a failure reaches the client with, and that is what
 * the workbench branches on — a taken name, an unknown document and unreadable Turtle each get a
 * different message.
 */
class ShapesDocumentsApiTest {

    private static final String GRAPH_URI = "http://example.org/EQ";

    private static final String BASE = "/api/datasets/cgmes/shacl";

    private static final String DOCUMENTS = BASE + "/documents";

    private static final String SHAPES =
            """
            @prefix sh: <http://www.w3.org/ns/shacl#> .
            @prefix ex: <http://example.org/> .

            # kept as written
            ex:LineShape a sh:NodeShape ; sh:targetClass ex:Line .
            """;

    private SHACLStoringService service;
    private DatabasePort databasePort;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        databasePort = new InMemoryDatabaseAdapter(new InMemoryDatabaseImpl(new SchemaConfig()));
        SessionContext.setSessionId(getClass().getSimpleName());
        databasePort.createWorkspaceIfAbsent("cgmes");
        try (var transaction = databasePort.beginTransaction("cgmes", ReadWrite.WRITE)) {
            transaction.createGraph(GRAPH_URI, GraphFactory.createDefaultGraph());
            transaction.commit("Created the graph");
        }
        databasePort.enableEditing("cgmes");
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
                                new SHACLSelectionExportRESTController(expandURIUseCase, service))
                        .setControllerAdvice(new GenericExceptionHandler())
                        .build();
    }

    @AfterEach
    void tearDown() {
        SessionContext.clear();
    }

    private ShapesDocumentInfo givenDocument(String name, String turtle) {
        return service.createShapesDocument(
                "cgmes", List.of(GRAPH_URI), name, name, turtle, Lang.TURTLE);
    }

    private String textOf(UUID documentId) {
        return service.getShapesDocumentText("cgmes", documentId);
    }

    private UUID onlyUploadedDocument() {
        return service.listShapesDocuments("cgmes").stream()
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

        assertThat(service.listShapesDocuments("cgmes")).isEmpty();
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
    void aShaclFileNameCountsAsTurtle() throws Exception {
        var file =
                new MockMultipartFile(
                        "file",
                        "shapes.shacl",
                        MediaType.APPLICATION_OCTET_STREAM_VALUE,
                        SHAPES.getBytes(StandardCharsets.UTF_8));

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
    void textNestedTooDeeplyToReadIsTheClientsMistake() throws Exception {
        var depth = 200_000;
        var turtle =
                "@prefix ex: <http://example.org/> .\nex:a ex:p "
                        + "[ ex:p ".repeat(depth)
                        + "1"
                        + " ]".repeat(depth)
                        + " .\n";

        mockMvc.perform(
                        post(DOCUMENTS)
                                .param("name", "deep.ttl")
                                .contentType(MediaType.TEXT_PLAIN)
                                .content(turtle))
                .andExpect(status().isBadRequest())
                .andExpect(
                        result ->
                                assertThat(result.getResponse().getContentAsString())
                                        .contains(ShapesTurtleParser.TOO_DEEP));

        assertThat(service.listShapesDocuments("cgmes")).isEmpty();
    }

    @Test
    void renamingOntoATakenNameIsAConflict() throws Exception {
        givenDocument("eq.ttl", SHAPES);
        var other = givenDocument("tp.ttl", SHAPES);

        mockMvc.perform(patch(DOCUMENTS + "/" + other.getId()).param("name", "eq.ttl"))
                .andExpect(status().isConflict());
    }

    /** The status the API documents for each write to a read-only workspace. */
    @Test
    void everyWriteToAReadOnlyWorkspaceIsAConflict() throws Exception {
        var document = givenDocument("eq.ttl", SHAPES);
        databasePort.disableEditing("cgmes");

        mockMvc.perform(
                        post(DOCUMENTS)
                                .param("name", "new.ttl")
                                .contentType(MediaType.TEXT_PLAIN)
                                .content(SHAPES))
                .andExpect(status().isConflict());
        mockMvc.perform(
                        put(DOCUMENTS + "/" + document.getId())
                                .contentType(MediaType.TEXT_PLAIN)
                                .content(SHAPES + "# edited\n"))
                .andExpect(status().isConflict());
        mockMvc.perform(patch(DOCUMENTS + "/" + document.getId()).param("enabled", "false"))
                .andExpect(status().isConflict());
        mockMvc.perform(delete(DOCUMENTS + "/" + document.getId()))
                .andExpect(status().isConflict());
    }

    @Test
    void writingOverTextThatChangedSinceItWasReadIsAPreconditionFailure() throws Exception {
        var document = givenDocument("eq.ttl", SHAPES);
        var revisionRead = SHACLDocumentUseCase.revisionOf(SHAPES);
        service.replaceShapesDocumentText("cgmes", document.getId(), SHAPES + "# later\n");

        mockMvc.perform(
                        put(DOCUMENTS + "/" + document.getId())
                                .param("expectedRevision", revisionRead)
                                .contentType(MediaType.TEXT_PLAIN)
                                .content(SHAPES + "# mine\n"))
                .andExpect(status().isPreconditionFailed());

        assertThat(textOf(document.getId())).isEqualTo(SHAPES + "# later\n");
    }

    @Test
    void aWorkspaceStartsWithoutAnyDocument() throws Exception {
        mockMvc.perform(get(DOCUMENTS)).andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    // -------------------------------------------------------------------------
    // Selection export
    // -------------------------------------------------------------------------

    @Test
    void anEmptyDocumentIdAmongOthersNamesNothingAndIsNoServerFault() throws Exception {
        var document = givenDocument("eq.ttl", SHAPES);

        mockMvc.perform(
                        get(BASE + "/export/file")
                                .param("documentId", document.getId().toString(), "")
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
    void aDocumentOfOnlyCommentsExportedAloneIsItsOwnText() throws Exception {
        var document = givenDocument("notes.ttl", "# notes that matter\n");

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
                                        .isEqualTo("# notes that matter\n"));
    }

    @Test
    void theContentTypeIsTheFormatTheBytesAreIn() throws Exception {
        var document = givenDocument("eq.ttl", SHAPES);

        mockMvc.perform(
                        get(BASE + "/export/file")
                                .param("documentId", document.getId().toString())
                                .header(HttpHeaders.ACCEPT, "application/rdf+xml, text/turtle"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/turtle"));
        mockMvc.perform(
                        get(BASE + "/export/file")
                                .param("documentId", document.getId().toString())
                                .header(HttpHeaders.ACCEPT, "application/rdf+xml"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/rdf+xml"));
        mockMvc.perform(get(BASE + "/export/file").param("documentId", document.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/turtle"));
    }

    @Test
    void aSyntaxErrorIsReportedOnTheLineTheTextHasItOn() throws Exception {
        mockMvc.perform(
                        post(DOCUMENTS)
                                .param("name", "bad.ttl")
                                .contentType(MediaType.TEXT_PLAIN)
                                .content("\n\n\n@prefix ex: <http://e/> .\nex:a ex:b .oops\n"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("[line: 5,")));
    }

    /** Merging must not fold two documents' lists together where they happen to look alike. */
    @Test
    void longListsFromTwoDocumentsStayTwoListsInAMergedExport() throws Exception {
        var shared =
                IntStream.range(0, 40).mapToObj(i -> "ex:v" + i).collect(Collectors.joining(" "));
        var prefixes =
                "@prefix sh: <http://www.w3.org/ns/shacl#> .\n@prefix ex: <http://example.org/> .\n";
        var first =
                givenDocument(
                        "a.ttl",
                        prefixes + "ex:A sh:path ex:p ; sh:in ( " + shared + " ex:a ) .\n");
        var second =
                givenDocument(
                        "b.ttl",
                        prefixes + "ex:B sh:path ex:q ; sh:in ( " + shared + " ex:b ) .\n");

        var exported =
                mockMvc.perform(
                                get(BASE + "/export/file")
                                        .param("documentId", first.getId().toString())
                                        .param("documentId", second.getId().toString())
                                        .header(HttpHeaders.ACCEPT, "text/turtle"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(StandardCharsets.UTF_8);

        var merged = RDFParser.fromString(exported, Lang.TURTLE).toGraph();
        assertThat(merged.stream(Node.ANY, RDF.rest.asNode(), Node.ANY).toList())
                .extracting(Triple::getSubject)
                .doesNotHaveDuplicates();
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

    @Test
    void creatingFromTextNamesTheSchemasItAppliesTo() throws Exception {
        mockMvc.perform(
                        post(DOCUMENTS)
                                .param("name", "eq.ttl")
                                .param("schema", GRAPH_URI)
                                .contentType(MediaType.TEXT_PLAIN)
                                .content(SHAPES))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schemas[0]").value(GRAPH_URI));
    }

    @Test
    void creatingWithoutASchemaAppliesToNone() throws Exception {
        mockMvc.perform(
                        post(DOCUMENTS)
                                .param("name", "eq.ttl")
                                .contentType(MediaType.TEXT_PLAIN)
                                .content(SHAPES))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schemas").isEmpty());
    }

    @Test
    void aSchemaThatDoesNotExistIsNotFound() throws Exception {
        mockMvc.perform(
                        post(DOCUMENTS)
                                .param("name", "eq.ttl")
                                .param("schema", "http://example.org/nowhere")
                                .contentType(MediaType.TEXT_PLAIN)
                                .content(SHAPES))
                .andExpect(status().isNotFound());
    }

    @Test
    void anEmptySchemaParameterClearsWhatADocumentAppliesTo() throws Exception {
        var document = givenDocument("eq.ttl", SHAPES);

        mockMvc.perform(patch(DOCUMENTS + "/" + document.getId()).param("schema", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schemas").isEmpty());
    }

    @Test
    void anUpdateWithNoSchemaParameterLeavesThemAlone() throws Exception {
        var document = givenDocument("eq.ttl", SHAPES);

        mockMvc.perform(patch(DOCUMENTS + "/" + document.getId()).param("enabled", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schemas[0]").value(GRAPH_URI));
    }

    @Test
    void anExportIsNamedAfterTheWorkspace() throws Exception {
        var document = givenDocument("eq.ttl", SHAPES);

        mockMvc.perform(
                        get(BASE + "/export/file")
                                .param("documentId", document.getId().toString())
                                .header(HttpHeaders.ACCEPT, "text/turtle"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "cgmes-shacl.ttl"));
    }
}

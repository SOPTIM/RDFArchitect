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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.apache.jena.riot.Lang;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.services.ExpandURIUseCase;
import org.rdfarchitect.services.shacl.SHACLDocumentUseCase;
import org.rdfarchitect.shacl.dto.ShapesDocumentInfo;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * An imported constraints file keeps its own name.
 *
 * <p>The name is how a modeller refers to an official file, and importing used to land everything
 * in the graph's default document — so a CGMES release's carefully named constraints all became
 * "custom.ttl", each one overwriting the last.
 */
class ShapesDocumentsFileUploadTest {

    private static final String URL =
            "/api/datasets/cgmes/graphs/http%3A%2F%2Fexample.org%2FDL/shacl/documents/file";

    private static final String GRAPH_URI = "http://example.org/DL";

    private static final String FILE_NAME = "61970-600-2_DiagramLayout-AP-Con-Simple-SHACL.ttl";

    private static final String SHAPES =
            """
            @prefix sh: <http://www.w3.org/ns/shacl#> .
            @prefix ex: <http://example.org/> .

            # a comment the file is expected to keep
            ex:Shape a sh:NodeShape .
            """;

    private SHACLDocumentUseCase documents;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        var expandURIUseCase = mock(ExpandURIUseCase.class);
        documents = mock(SHACLDocumentUseCase.class);
        when(expandURIUseCase.expandUri(any(), any())).thenReturn(GRAPH_URI);
        when(documents.createShapesDocument(any(), any(), any(), any(), any()))
                .thenReturn(ShapesDocumentInfo.builder().id(UUID.randomUUID()).build());

        mockMvc =
                MockMvcBuilders.standaloneSetup(
                                new ShapesDocumentsRESTController(expandURIUseCase, documents))
                        .build();
    }

    private static MockMultipartFile file() {
        return new MockMultipartFile(
                "file", FILE_NAME, "text/turtle", SHAPES.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void theDocumentIsNamedAfterTheFile() throws Exception {
        mockMvc.perform(multipart(URL).file(file())).andExpect(status().isOk());

        verify(documents)
                .createShapesDocument(
                        new GraphIdentifier("cgmes", GRAPH_URI),
                        FILE_NAME,
                        FILE_NAME,
                        SHAPES,
                        Lang.TURTLE);
    }

    @Test
    void anExplicitNameWins() throws Exception {
        // What the importer sends when the file's own name is already taken in this graph.
        mockMvc.perform(multipart(URL).file(file()).param("name", FILE_NAME + " (2)"))
                .andExpect(status().isOk());

        var name = ArgumentCaptor.forClass(String.class);
        verify(documents).createShapesDocument(any(), name.capture(), any(), any(), any());
        assertThat(name.getValue()).isEqualTo(FILE_NAME + " (2)");
    }

    @Test
    void theFilesTextArrivesUnchanged() throws Exception {
        mockMvc.perform(multipart(URL).file(file())).andExpect(status().isOk());

        var text = ArgumentCaptor.forClass(String.class);
        verify(documents).createShapesDocument(any(), any(), any(), text.capture(), any());
        // Comments and ordering are what an official file is expected to give back byte for byte.
        assertThat(text.getValue()).isEqualTo(SHAPES);
    }
}

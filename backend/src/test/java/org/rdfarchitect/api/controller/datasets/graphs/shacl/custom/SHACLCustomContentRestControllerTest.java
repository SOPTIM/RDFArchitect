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

package org.rdfarchitect.api.controller.datasets.graphs.shacl.custom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.NodeFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.services.ExpandURIUseCase;
import org.rdfarchitect.services.shacl.SHACLExportUseCase;
import org.rdfarchitect.services.shacl.SHACLInsertUseCase;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Content-negotiation contract of the custom SHACL endpoints.
 *
 * <p>{@code replaceGraphWithGraphString} takes a plain {@code String} request body, which Spring
 * reads verbatim via {@code StringHttpMessageConverter}. It therefore has to be declared as {@code
 * text/plain}: while it accepted {@code application/json}, the generated frontend client applied
 * its default JSON serializer and the quoted payload reached Jena as {@code "@prefix ..."}, failing
 * with "Not a valid token for an RDF term". These tests pin both halves of that contract.
 */
class SHACLCustomContentRestControllerTest {

    private static final String URL =
            "/api/datasets/cgmes/graphs/http%3A%2F%2Fexample.org%2FEQ/shacl/custom/string";

    private static final String GRAPH_URI = "http://example.org/EQ";

    /** Deliberately carries prefixes and a comment — both must survive parsing. */
    private static final String TTL =
            """
            @prefix sh:   <http://www.w3.org/ns/shacl#> .
            @prefix ex:   <http://example.org/> .

            # the shape below constrains the length of a line segment
            ex:ACLineSegmentShape
                    a             sh:NodeShape ;
                    sh:targetClass ex:ACLineSegment .
            """;

    private SHACLInsertUseCase shaclInsertUseCase;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        var expandURIUseCase = mock(ExpandURIUseCase.class);
        shaclInsertUseCase = mock(SHACLInsertUseCase.class);
        var shaclExportUseCase = mock(SHACLExportUseCase.class);
        when(expandURIUseCase.expandUri(any(), any())).thenReturn(GRAPH_URI);

        var controller =
                new SHACLCustomContentRestController(
                        expandURIUseCase, shaclInsertUseCase, shaclExportUseCase);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void replaceGraphWithGraphString_plainTextBody_isParsedVerbatim() throws Exception {
        mockMvc.perform(put(URL).contentType(MediaType.TEXT_PLAIN).content(TTL))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(200));

        var graphCaptor = ArgumentCaptor.forClass(Graph.class);
        verify(shaclInsertUseCase)
                .replaceCustomSHACLGraph(
                        ArgumentCaptor.forClass(GraphIdentifier.class).capture(),
                        graphCaptor.capture());

        var stored = graphCaptor.getValue();
        assertThat(
                        stored.contains(
                                NodeFactory.createURI("http://example.org/ACLineSegmentShape"),
                                NodeFactory.createURI("http://www.w3.org/ns/shacl#targetClass"),
                                NodeFactory.createURI("http://example.org/ACLineSegment")))
                .isTrue();
    }

    @Test
    void replaceGraphWithGraphString_jsonBody_isRejectedInsteadOfReachingTheParser()
            throws Exception {
        // What the generated client used to send: the same Turtle, JSON-quoted.
        var quoted = "\"" + TTL.replace("\n", "\\n").replace("\"", "\\\"") + "\"";

        mockMvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON).content(quoted))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(415));

        verify(shaclInsertUseCase, never()).replaceCustomSHACLGraph(any(), any());
    }
}

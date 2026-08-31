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

package org.rdfarchitect.api.controller.datasets.graphs.shacl.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.services.ExpandURIUseCase;
import org.rdfarchitect.services.shacl.validation.ShapesValidationUseCase;
import org.rdfarchitect.shacl.dto.ShapesValidationReport;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

/**
 * Request contract of the validation endpoints.
 *
 * <p>The text variant takes a plain {@code String} request body, which Spring reads verbatim via
 * {@code StringHttpMessageConverter}, so it has to be declared {@code text/plain} — declaring it
 * {@code application/json} is what made the earlier SHACL endpoints receive quoted Turtle.
 */
class ShapesValidationRESTControllerTest {

    private static final String URL =
            "/api/datasets/cgmes/graphs/http%3A%2F%2Fexample.org%2FEQ/shacl/validate";

    private static final String GRAPH_URI = "http://example.org/EQ";

    /** Deliberately carries prefixes and a comment — both must reach the service unchanged. */
    private static final String TTL =
            """
            @prefix sh: <http://www.w3.org/ns/shacl#> .
            @prefix ex: <http://example.org/> .

            # the shape below constrains a line segment
            ex:ACLineSegmentShape
                a sh:NodeShape ;
                sh:targetClass ex:ACLineSegment .
            """;

    private ShapesValidationUseCase shapesValidationUseCase;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        var expandURIUseCase = mock(ExpandURIUseCase.class);
        shapesValidationUseCase = mock(ShapesValidationUseCase.class);
        when(expandURIUseCase.expandUri(any(), any())).thenReturn(GRAPH_URI);
        when(shapesValidationUseCase.validateShapes(any(), any())).thenReturn(emptyReport());
        when(shapesValidationUseCase.validateTurtle(any(), any(), any())).thenReturn(emptyReport());

        var controller =
                new ShapesValidationRESTController(expandURIUseCase, shapesValidationUseCase);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void validatingWithoutADocumentIdCoversEveryEnabledDocument() throws Exception {
        mockMvc.perform(post(URL))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(200));

        verify(shapesValidationUseCase)
                .validateShapes(new GraphIdentifier("cgmes", GRAPH_URI), null);
    }

    @Test
    void aDocumentIdSelectsASingleDocument() throws Exception {
        var documentId = UUID.randomUUID();

        mockMvc.perform(post(URL).param("documentId", documentId.toString()))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(200));

        verify(shapesValidationUseCase)
                .validateShapes(new GraphIdentifier("cgmes", GRAPH_URI), documentId);
    }

    @Test
    void unsavedTurtleReachesTheServiceVerbatim() throws Exception {
        mockMvc.perform(
                        post(URL + "/text")
                                .param("name", "draft.ttl")
                                .contentType(MediaType.TEXT_PLAIN)
                                .content(TTL))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(200));

        var body = ArgumentCaptor.forClass(String.class);
        verify(shapesValidationUseCase)
                .validateTurtle(
                        eq(new GraphIdentifier("cgmes", GRAPH_URI)),
                        eq("draft.ttl"),
                        body.capture());
        assertThat(body.getValue()).isEqualTo(TTL);
    }

    @Test
    void aJsonQuotedBodyIsRefusedRatherThanReachingTheParser() throws Exception {
        mockMvc.perform(
                        post(URL + "/text")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("\"" + TTL.replace("\n", "\\n") + "\""))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(415));

        verify(shapesValidationUseCase, never()).validateTurtle(any(), any(), any());
    }

    @Test
    void unsavedTurtleWithoutANameGetsADefault() throws Exception {
        mockMvc.perform(post(URL + "/text").contentType(MediaType.TEXT_PLAIN).content(TTL))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(200));

        verify(shapesValidationUseCase).validateTurtle(any(), eq("unsaved"), eq(TTL));
    }

    @Test
    void theReportIsSerialisedAsJson() throws Exception {
        mockMvc.perform(post(URL))
                .andExpect(
                        result -> {
                            assertThat(result.getResponse().getContentType())
                                    .contains(MediaType.APPLICATION_JSON_VALUE);
                            assertThat(result.getResponse().getContentAsString())
                                    .contains("\"valid\":true")
                                    .contains("\"documents\":[]");
                        });
    }

    private static ShapesValidationReport emptyReport() {
        return ShapesValidationReport.builder()
                .valid(true)
                .profiles(List.of())
                .documents(List.of())
                .build();
    }
}

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

package org.rdfarchitect.api.controller.datasets.graphs.shacl.form;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.rdfarchitect.exception.database.ResourceConflictException;
import org.rdfarchitect.exception.handlers.GenericExceptionHandler;
import org.rdfarchitect.services.shacl.form.ShapeFormUseCase;
import org.rdfarchitect.shacl.dto.NodeShapeModel;
import org.rdfarchitect.shacl.dto.ShapeEditRequest;
import org.rdfarchitect.shacl.dto.ShapeEditResult;
import org.rdfarchitect.shacl.dto.ShapesForm;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

/** The HTTP contract of the form view: raw Turtle in, shapes out, edited Turtle back. */
class ShapeFormRESTControllerTest {

    private static final String URL =
            "/api/datasets/cgmes/graphs/http%3A%2F%2Fexample.org%2FEQ/shacl/form";

    /** Deliberately carries a prefix and a comment — both must reach the parser unescaped. */
    private static final String TTL =
            """
            @prefix sh: <http://www.w3.org/ns/shacl#> .

            # a comment that must survive the round trip
            ex:Shape a sh:NodeShape .
            """;

    private ShapeFormUseCase shapeFormUseCase;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        shapeFormUseCase = mock(ShapeFormUseCase.class);
        when(shapeFormUseCase.parse(any()))
                .thenReturn(
                        ShapesForm.builder()
                                .shapes(
                                        List.of(
                                                NodeShapeModel.builder()
                                                        .iri("http://example.org/Shape")
                                                        .editable(true)
                                                        .build()))
                                .build());
        when(shapeFormUseCase.apply(any()))
                .thenReturn(ShapeEditResult.builder().turtle("edited").warnings(List.of()).build());

        mockMvc =
                MockMvcBuilders.standaloneSetup(new ShapeFormRESTController(shapeFormUseCase))
                        .build();
    }

    @Test
    void readingAFormTakesThePlainTextBodyVerbatim() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.TEXT_PLAIN).content(TTL))
                .andExpect(
                        result -> {
                            assertThat(result.getResponse().getStatus()).isEqualTo(200);
                            assertThat(result.getResponse().getContentAsString())
                                    .contains("\"editable\":true");
                        });

        var body = ArgumentCaptor.forClass(String.class);
        verify(shapeFormUseCase).parse(body.capture());
        assertThat(body.getValue()).isEqualTo(TTL);
    }

    @Test
    void aJsonQuotedBodyIsRefusedRatherThanReachingTheParser() throws Exception {
        mockMvc.perform(
                        post(URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("\"" + TTL.replace("\n", "\\n") + "\""))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(415));

        verify(shapeFormUseCase, never()).parse(any());
    }

    @Test
    void anEditCarriesTheDocumentAndTheShapeAsJson() throws Exception {
        mockMvc.perform(
                        post(URL + "/apply")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"turtle":"ex:S a sh:NodeShape .",
                                         "shape":{"iri":"http://example.org/S",
                                                  "targetClasses":["http://example.org/C"]}}"""))
                .andExpect(
                        result ->
                                assertThat(result.getResponse().getContentAsString())
                                        .contains("\"turtle\":\"edited\""));

        var request = ArgumentCaptor.forClass(ShapeEditRequest.class);
        verify(shapeFormUseCase).apply(request.capture());
        assertThat(request.getValue().getTurtle()).isEqualTo("ex:S a sh:NodeShape .");
        assertThat(request.getValue().getShape().getTargetClasses())
                .containsExactly("http://example.org/C");
    }

    @Test
    void aRefusedEditAnswersWithTheReasonTheFormShows() throws Exception {
        // The form puts this in front of the user instead of "the change could not be applied",
        // so the reason has to survive as far as the response body: it is the only part of a
        // refusal anybody can act on.
        var refused =
                MockMvcBuilders.standaloneSetup(new ShapeFormRESTController(shapeFormUseCase))
                        .setControllerAdvice(new GenericExceptionHandler())
                        .build();
        when(shapeFormUseCase.apply(any()))
                .thenThrow(
                        new ResourceConflictException(
                                "This shape is written as 2 separate statements."));

        refused.perform(
                        post(URL + "/apply")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"turtle\":\"x\",\"shape\":{\"iri\":\"urn:s\"}}"))
                .andExpect(
                        result -> {
                            assertThat(result.getResponse().getStatus()).isEqualTo(409);
                            assertThat(result.getResponse().getContentAsString())
                                    .contains("This shape is written as 2 separate statements.");
                        });
    }

    @Test
    void aRemovalNamesTheShapeInsteadOfCarryingOne() throws Exception {
        mockMvc.perform(
                        post(URL + "/apply")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"turtle\":\"x\",\"removeShapeIri\":\"http://example.org/S\"}"))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(200));

        var request = ArgumentCaptor.forClass(ShapeEditRequest.class);
        verify(shapeFormUseCase).apply(request.capture());
        assertThat(request.getValue().getRemoveShapeIri()).isEqualTo("http://example.org/S");
        assertThat(request.getValue().getShape()).isNull();
    }
}

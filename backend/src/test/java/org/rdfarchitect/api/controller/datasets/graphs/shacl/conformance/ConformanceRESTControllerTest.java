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

package org.rdfarchitect.api.controller.datasets.graphs.shacl.conformance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.services.ExpandURIUseCase;
import org.rdfarchitect.services.shacl.conformance.ConformanceUseCase;
import org.rdfarchitect.shacl.dto.ConformanceFinding;
import org.rdfarchitect.shacl.dto.ConformanceReport;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

/** The HTTP contract of the conformance report. */
class ConformanceRESTControllerTest {

    private static final String URL =
            "/api/datasets/cgmes/graphs/http%3A%2F%2Fexample.org%2FEQ/shacl/conformance";

    private static final String GRAPH_URI = "http://example.org/EQ";

    private ConformanceUseCase conformanceUseCase;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        var expandURIUseCase = mock(ExpandURIUseCase.class);
        conformanceUseCase = mock(ConformanceUseCase.class);
        when(expandURIUseCase.expandUri(any(), any())).thenReturn(GRAPH_URI);
        when(conformanceUseCase.compare(any(), any()))
                .thenReturn(
                        ConformanceReport.builder()
                                .documentName("official.ttl")
                                .conforms(false)
                                .compared(1343)
                                .agreeing(1341)
                                .contradictedCount(2)
                                .findings(
                                        List.of(
                                                ConformanceFinding.builder()
                                                        .kind(ConformanceFinding.Kind.CONTRADICTED)
                                                        .targetClass("http://ex.org/Season")
                                                        .path("http://ex.org/Season.endDate")
                                                        .message("A value cannot be both.")
                                                        .build()))
                                .build());

        mockMvc =
                MockMvcBuilders.standaloneSetup(
                                new ConformanceRESTController(expandURIUseCase, conformanceUseCase))
                        .build();
    }

    @Test
    void theReportIsServedAsJsonForTheExpandedGraph() throws Exception {
        var documentId = UUID.randomUUID();

        mockMvc.perform(get(URL).param("documentId", documentId.toString()))
                .andExpect(
                        result -> {
                            assertThat(result.getResponse().getContentType())
                                    .contains(MediaType.APPLICATION_JSON_VALUE);
                            assertThat(result.getResponse().getContentAsString())
                                    .contains("\"conforms\":false")
                                    .contains("\"kind\":\"CONTRADICTED\"")
                                    .contains("\"agreeing\":1341");
                        });

        verify(conformanceUseCase)
                .compare(eq(new GraphIdentifier("cgmes", GRAPH_URI)), eq(documentId));
    }

    @Test
    void comparingWithoutSayingAgainstWhatIsRefused() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(400));
    }
}

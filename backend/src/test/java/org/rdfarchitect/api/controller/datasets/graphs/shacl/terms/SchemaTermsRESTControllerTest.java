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

package org.rdfarchitect.api.controller.datasets.graphs.shacl.terms;

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
import org.rdfarchitect.services.shacl.terms.SchemaTermsUseCase;
import org.rdfarchitect.shacl.dto.SchemaTerm;
import org.rdfarchitect.shacl.dto.SchemaTermDetail;
import org.rdfarchitect.shacl.dto.SchemaTerms;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

/** The HTTP contract of the schema-terms endpoints an editor completes and hovers against. */
class SchemaTermsRESTControllerTest {

    private static final String URL =
            "/api/datasets/cgmes/graphs/http%3A%2F%2Fexample.org%2FEQ/shacl/terms";

    private static final String GRAPH_URI = "http://example.org/EQ";

    private static final String IRI = "http://iec.ch/TC57/CIM100#ACLineSegment";

    private SchemaTermsUseCase schemaTermsUseCase;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        var expandURIUseCase = mock(ExpandURIUseCase.class);
        schemaTermsUseCase = mock(SchemaTermsUseCase.class);
        when(expandURIUseCase.expandUri(any(), any())).thenReturn(GRAPH_URI);
        when(schemaTermsUseCase.listTerms(any()))
                .thenReturn(
                        SchemaTerms.builder()
                                .profiles(List.of("http://example.org/EQ/1.0"))
                                .terms(
                                        List.of(
                                                SchemaTerm.builder()
                                                        .kind(SchemaTerm.Kind.CLASS)
                                                        .iri(IRI)
                                                        .namespace("http://iec.ch/TC57/CIM100#")
                                                        .localName("ACLineSegment")
                                                        .build()))
                                .build());

        var controller = new SchemaTermsRESTController(expandURIUseCase, schemaTermsUseCase);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void theTermListIsServedAsJsonForTheExpandedGraph() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(
                        result -> {
                            assertThat(result.getResponse().getContentType())
                                    .contains(MediaType.APPLICATION_JSON_VALUE);
                            assertThat(result.getResponse().getContentAsString())
                                    .contains("\"localName\":\"ACLineSegment\"")
                                    .contains("\"kind\":\"CLASS\"");
                        });

        verify(schemaTermsUseCase).listTerms(eq(new GraphIdentifier("cgmes", GRAPH_URI)));
    }

    @Test
    void describingATermPassesItsIriThrough() throws Exception {
        when(schemaTermsUseCase.detailOf(any(), eq(IRI)))
                .thenReturn(
                        SchemaTermDetail.builder()
                                .kind(SchemaTerm.Kind.CLASS)
                                .iri(IRI)
                                .comment("A wire.")
                                .build());

        mockMvc.perform(get(URL + "/detail").param("iri", IRI))
                .andExpect(
                        result ->
                                assertThat(result.getResponse().getContentAsString())
                                        .contains("\"comment\":\"A wire.\""));

        verify(schemaTermsUseCase).detailOf(eq(new GraphIdentifier("cgmes", GRAPH_URI)), eq(IRI));
    }

    @Test
    void anUndeclaredTermIsNotFoundRatherThanAnEmptyBody() throws Exception {
        // An editor asks about whatever is under the cursor, so "no such term" is a normal answer
        // and has to be distinguishable from a term that exists but says nothing.
        when(schemaTermsUseCase.detailOf(any(), any())).thenReturn(null);

        mockMvc.perform(get(URL + "/detail").param("iri", "http://example.org/Nonsense"))
                .andExpect(result -> assertThat(result.getResolvedException()).isNotNull());
    }

    @Test
    void describingATermWithoutSayingWhichIsRefused() throws Exception {
        mockMvc.perform(get(URL + "/detail"))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(400));
    }
}

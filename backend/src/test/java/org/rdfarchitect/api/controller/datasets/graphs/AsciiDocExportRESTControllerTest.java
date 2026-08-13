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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.services.ExpandURIUseCase;
import org.rdfarchitect.services.select.ExportGraphAsciiDocUseCase;
import org.springframework.http.HttpHeaders;

import java.nio.charset.StandardCharsets;

class AsciiDocExportRESTControllerTest {

    private ExpandURIUseCase expandURIUseCase;
    private ExportGraphAsciiDocUseCase exportGraphAsciiDocUseCase;
    private AsciiDocExportRESTController controller;

    @BeforeEach
    void setUp() {
        expandURIUseCase = mock(ExpandURIUseCase.class);
        exportGraphAsciiDocUseCase = mock(ExportGraphAsciiDocUseCase.class);
        controller = new AsciiDocExportRESTController(expandURIUseCase, exportGraphAsciiDocUseCase);
    }

    private void stubExport(String graphURI, String expandedGraphURI) {
        when(expandURIUseCase.expandUri("dataset", graphURI)).thenReturn(expandedGraphURI);
        when(exportGraphAsciiDocUseCase.exportGraphAsAsciiDoc(
                        new GraphIdentifier("dataset", expandedGraphURI), "png", false))
                .thenReturn("== Concrete Classes".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void getAsciiDocExport_namesTheFileAfterTheGraph() {
        stubExport("EQ", "http://example.com/EQ");

        var response =
                controller.getAsciiDocExport(HttpHeaders.ORIGIN, "dataset", "EQ", "png", false);

        assertThat(response.getHeaders().getContentDisposition().getFilename())
                .isEqualTo("EQ.adoc");
    }

    @Test
    void getAsciiDocExport_exposesTheContentDispositionToTheBrowser() {
        stubExport("EQ", "http://example.com/EQ");

        var response =
                controller.getAsciiDocExport(HttpHeaders.ORIGIN, "dataset", "EQ", "png", false);

        assertThat(response.getHeaders().getAccessControlExposeHeaders())
                .containsExactly(HttpHeaders.CONTENT_DISPOSITION);
    }

    @Test
    void getAsciiDocExport_defaultGraph_fallsBackToDefaultFileName() {
        stubExport("default", "default");

        var response =
                controller.getAsciiDocExport(
                        HttpHeaders.ORIGIN, "dataset", "default", "png", false);

        assertThat(response.getHeaders().getContentDisposition().getFilename())
                .isEqualTo("default.adoc");
    }
}

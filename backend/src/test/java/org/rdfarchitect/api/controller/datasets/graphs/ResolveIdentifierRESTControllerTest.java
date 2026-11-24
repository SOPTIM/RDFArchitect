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
import org.rdfarchitect.services.select.ResolveIdentifierUseCase;
import org.springframework.http.HttpHeaders;

import java.util.UUID;

class ResolveIdentifierRESTControllerTest {

    private ExpandURIUseCase expandURIUseCase;
    private ResolveIdentifierUseCase resolveIdentifierUseCase;
    private ResolveIdentifierRESTController controller;

    @BeforeEach
    void setUp() {
        expandURIUseCase = mock(ExpandURIUseCase.class);
        resolveIdentifierUseCase = mock(ResolveIdentifierUseCase.class);
        controller =
                new ResolveIdentifierRESTController(expandURIUseCase, resolveIdentifierUseCase);
    }

    @Test
    void resolveIri_prefixedIri_isExpandedWhileTheSchemaNameIsNot() {
        var uuid = UUID.randomUUID();
        when(expandURIUseCase.expandUri("ws", "cim:Breaker"))
                .thenReturn("http://iec.ch/TC57/CIM100#Breaker");
        when(resolveIdentifierUseCase.resolveIRI(
                        new GraphIdentifier("ws", "cim:EQ"), "http://iec.ch/TC57/CIM100#Breaker"))
                .thenReturn(uuid);

        var result = controller.resolveIri(HttpHeaders.ORIGIN, "ws", "cim:EQ", "cim:Breaker");

        assertThat(result).isEqualTo(uuid.toString());
    }
}

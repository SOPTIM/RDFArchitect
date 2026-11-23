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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.rdfarchitect.api.dto.packages.PackageDTO;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.services.ExpandURIUseCase;
import org.rdfarchitect.services.select.ListExternalPackagesUseCase;
import org.rdfarchitect.services.select.ListInternalPackagesUseCase;
import org.rdfarchitect.services.update.packages.AddPackageUseCase;
import org.springframework.http.HttpHeaders;

class AllPackagesRESTControllerTest {

    private ExpandURIUseCase expandURIUseCase;
    private AddPackageUseCase addPackageUseCase;
    private AllPackagesRESTController controller;

    @BeforeEach
    void setUp() {
        expandURIUseCase = mock(ExpandURIUseCase.class);
        addPackageUseCase = mock(AddPackageUseCase.class);
        controller =
                new AllPackagesRESTController(
                        expandURIUseCase,
                        addPackageUseCase,
                        mock(ListInternalPackagesUseCase.class),
                        mock(ListExternalPackagesUseCase.class));
    }

    @Test
    void addPackage_prefixedNamespace_isExpanded() {
        when(expandURIUseCase.expandUri("ws", "cim:")).thenReturn("http://iec.ch/TC57/CIM100#");
        var dto = PackageDTO.builder().label("Core").prefix("cim:").build();

        controller.addPackage(HttpHeaders.ORIGIN, "ws", "cim:EQ", dto);

        var captor = ArgumentCaptor.forClass(PackageDTO.class);
        verify(addPackageUseCase).addPackage(any(GraphIdentifier.class), captor.capture());
        assertThat(captor.getValue().getPrefix()).isEqualTo("http://iec.ch/TC57/CIM100#");
    }

    @Test
    void addPackage_schemaName_isUsedAsItIs() {
        when(expandURIUseCase.expandUri("ws", "cim:")).thenReturn("http://iec.ch/TC57/CIM100#");
        var dto = PackageDTO.builder().label("Core").prefix("cim:").build();

        controller.addPackage(HttpHeaders.ORIGIN, "ws", "cim:EQ", dto);

        verify(addPackageUseCase).addPackage(new GraphIdentifier("ws", "cim:EQ"), dto);
    }
}

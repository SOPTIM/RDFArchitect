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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.api.controller.Response;
import org.rdfarchitect.api.controller.datasets.graphs.packages.PackageRESTController;
import org.rdfarchitect.api.dto.packages.PackageDTO;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.services.update.packages.DeletePackageUseCase;
import org.rdfarchitect.services.update.packages.GetPackageUseCase;
import org.rdfarchitect.services.update.packages.ReplacePackageUseCase;
import org.springframework.http.HttpHeaders;

import java.util.UUID;

class PackageRESTControllerTest {

    private ReplacePackageUseCase replacePackageUseCase;
    private DeletePackageUseCase deletePackageUseCase;
    private GetPackageUseCase getPackageUseCase;
    private PackageRESTController controller;

    @BeforeEach
    void setUp() {
        replacePackageUseCase = mock(ReplacePackageUseCase.class);
        deletePackageUseCase = mock(DeletePackageUseCase.class);
        getPackageUseCase = mock(GetPackageUseCase.class);
        controller =
                new PackageRESTController(
                        replacePackageUseCase, deletePackageUseCase, getPackageUseCase);
    }

    @Test
    void deletePackage_invokesUseCaseWithGraphIdentifier() {
        UUID packageUuid = UUID.randomUUID();

        var response =
                controller.deletePackage(HttpHeaders.ORIGIN, "dataset", "graph", packageUuid);

        assertThat(response).isEqualTo(Response.SUCCESS);
        verify(deletePackageUseCase)
                .deletePackage(new GraphIdentifier("dataset", "graph"), packageUuid);
    }

    @Test
    void replacePackage_passesPayloadToUseCase() {
        var packageUUID = UUID.randomUUID();
        var dto = PackageDTO.builder().label("pkg").build();

        var response =
                controller.replacePackage(
                        HttpHeaders.ORIGIN, "dataset", "graph", packageUUID.toString(), dto);

        assertThat(response).isEqualTo(Response.SUCCESS);
        verify(replacePackageUseCase).replacePackage(new GraphIdentifier("dataset", "graph"), dto);
    }

    @Test
    void getPackage_returnsPackageDTOFromUseCase() {
        var packageUUID = UUID.randomUUID();
        var expectedDTO =
                PackageDTO.builder()
                        .uuid(packageUUID)
                        .label("myPackage")
                        .prefix("http://example.org#")
                        .build();
        when(getPackageUseCase.getPackage(new GraphIdentifier("dataset", "graph"), packageUUID))
                .thenReturn(expectedDTO);

        var result = controller.getPackage(HttpHeaders.ORIGIN, "dataset", "graph", packageUUID);

        assertThat(result).isEqualTo(expectedDTO);
        verify(getPackageUseCase).getPackage(new GraphIdentifier("dataset", "graph"), packageUUID);
    }
}

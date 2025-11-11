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

package org.rdfarchitect.api.controller.datasets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.services.select.ListDatasetsUseCase;
import org.rdfarchitect.services.update.dataset.DeleteDatasetUseCase;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DatasetRESTControllerTest {

    private ListDatasetsUseCase listDatasetsUseCase;
    private DeleteDatasetUseCase deleteDatasetUseCase;
    private DatasetRESTController controller;

    @BeforeEach
    void setUp() {
        listDatasetsUseCase = mock(ListDatasetsUseCase.class);
        deleteDatasetUseCase = mock(DeleteDatasetUseCase.class);
        controller = new DatasetRESTController(listDatasetsUseCase, deleteDatasetUseCase);
    }

    @Test
    void listDatasets_returnsValueFromUseCase() {
        when(listDatasetsUseCase.listDatasets()).thenReturn(List.of("dataset-a", "dataset-b"));

        var result = controller.listDatasets("origin");

        assertThat(result).containsExactly("dataset-a", "dataset-b");
        verify(listDatasetsUseCase).listDatasets();
    }

    @Test
    void deleteDataset_invokesUseCaseAndReturnsSuccess() {
        var response = controller.deleteDataset("origin", "dataset-a");

        assertThat(response).isEqualTo("success");
        verify(deleteDatasetUseCase).deleteDataset("dataset-a");
    }
}

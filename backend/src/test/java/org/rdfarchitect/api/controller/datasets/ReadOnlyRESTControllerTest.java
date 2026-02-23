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
import org.rdfarchitect.api.controller.Response;
import org.rdfarchitect.services.readonly.DisableEditingUseCase;
import org.rdfarchitect.services.readonly.EnableEditingUseCase;
import org.rdfarchitect.services.readonly.IsReadOnlyUseCase;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReadOnlyRESTControllerTest {

    private IsReadOnlyUseCase isReadOnlyUseCase;
    private EnableEditingUseCase enableEditingUseCase;
    private DisableEditingUseCase disableEditingUseCase;
    private ReadOnlyRESTController controller;

    @BeforeEach
    void setUp() {
        isReadOnlyUseCase = mock(IsReadOnlyUseCase.class);
        enableEditingUseCase = mock(EnableEditingUseCase.class);
        disableEditingUseCase = mock(DisableEditingUseCase.class);
        controller = new ReadOnlyRESTController(isReadOnlyUseCase, enableEditingUseCase, disableEditingUseCase);
    }

    @Test
    void isReadOnly_returnsFlagFromUseCase() {
        when(isReadOnlyUseCase.isReadOnly("dataset")).thenReturn(true);

        assertThat(controller.isReadOnly("origin", "dataset")).isTrue();
        verify(isReadOnlyUseCase).isReadOnly("dataset");
    }

    @Test
    void enableEditing_invokesUseCase() {
        var response = controller.enableEditing("origin", "dataset");

        assertThat(response).isEqualTo(Response.SUCCESS);
        verify(enableEditingUseCase).enableEditing("dataset");
    }

    @Test
    void disableEditing_invokesUseCase() {
        var response = controller.disableEditing("origin", "dataset");

        assertThat(response).isEqualTo(Response.SUCCESS);
        verify(disableEditingUseCase).disableEditing("dataset");
    }
}

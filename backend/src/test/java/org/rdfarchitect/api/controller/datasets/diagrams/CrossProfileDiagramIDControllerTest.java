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

package org.rdfarchitect.api.controller.datasets.diagrams;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.database.DatabasePort;
import org.springframework.http.HttpHeaders;

import java.util.UUID;

class CrossProfileDiagramIDControllerTest {

    private DatabasePort databasePort;
    private CrossProfileDiagramIDController controller;

    @BeforeEach
    void setUp() {
        databasePort = mock(DatabasePort.class);
        controller = new CrossProfileDiagramIDController(databasePort);
    }

    @Test
    void getCrossProfileData_returnsUUIDStringFromDatabasePort() {
        var uuid = UUID.randomUUID();
        when(databasePort.getCrossProfileDiagramUUID("my-dataset")).thenReturn(uuid);

        var result = controller.getCrossProfileRenderingData(HttpHeaders.ORIGIN, "my-dataset");

        assertThat(result).isEqualTo(uuid.toString());
        verify(databasePort).getCrossProfileDiagramUUID("my-dataset");
    }
}

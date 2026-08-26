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
import org.rdfarchitect.database.inmemory.diagrams.CrossProfileDiagramInfo;
import org.springframework.http.HttpHeaders;

import java.util.UUID;

class CrossProfileDiagramIDRESTControllerTest {

    private DatabasePort databasePort;
    private CrossProfileDiagramIDRESTController controller;

    @BeforeEach
    void setUp() {
        databasePort = mock(DatabasePort.class);
        controller = new CrossProfileDiagramIDRESTController(databasePort);
    }

    @Test
    void getCrossProfileDiagramId_validDataset_returnsUUIDString() {
        var uuid = UUID.randomUUID();
        var crossProfileDiagramInfo = mock(CrossProfileDiagramInfo.class);
        when(crossProfileDiagramInfo.getCrossProfileDiagramUUID()).thenReturn(uuid);
        when(databasePort.getCrossProfileDiagramInfo("my-dataset"))
                .thenReturn(crossProfileDiagramInfo);

        var result = controller.getCrossProfileDiagramId(HttpHeaders.ORIGIN, "my-dataset");

        assertThat(result).isEqualTo(uuid.toString());
        verify(databasePort).getCrossProfileDiagramInfo("my-dataset");
        verify(crossProfileDiagramInfo).getCrossProfileDiagramUUID();
    }
}

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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.services.update.graph.ImportJobUseCase;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

class GraphBulkContentRESTControllerTest {

    private ImportJobUseCase importJobUseCase;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        importJobUseCase = mock(ImportJobUseCase.class);
        when(importJobUseCase.startImport(any(), anyList(), any())).thenReturn(UUID.randomUUID());
        mockMvc =
                MockMvcBuilders.standaloneSetup(
                                new GraphBulkContentRESTController(importJobUseCase))
                        .build();
    }

    @Test
    void startImport_singleSchemaNameWithComma_isNotSplit() throws Exception {
        mockMvc.perform(
                        multipart("/api/workspaces/ws/schemas/content/imports")
                                .file(file("EQ, v3.ttl"))
                                .queryParam("schemaNames", "EQ, v3"))
                .andExpect(status().isAccepted());

        verify(importJobUseCase).startImport(eq("ws"), anyList(), eq(List.of("EQ, v3")));
    }

    @Test
    void startImport_severalSchemaNames_keepTheirOrder() throws Exception {
        mockMvc.perform(
                        multipart("/api/workspaces/ws/schemas/content/imports")
                                .file(file("a.ttl"))
                                .file(file("b.ttl"))
                                .queryParam("schemaNames", "a, 1", "b"))
                .andExpect(status().isAccepted());

        verify(importJobUseCase).startImport(eq("ws"), anyList(), eq(List.of("a, 1", "b")));
    }

    @Test
    void startImport_withoutSchemaNames_passesNull() throws Exception {
        mockMvc.perform(multipart("/api/workspaces/ws/schemas/content/imports").file(file("a.ttl")))
                .andExpect(status().isAccepted());

        verify(importJobUseCase).startImport(eq("ws"), anyList(), isNull());
    }

    private static MockMultipartFile file(String name) {
        return new MockMultipartFile("files", name, "text/turtle", new byte[0]);
    }
}

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

package org.rdfarchitect.services.update;

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.mem.GraphMem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.cim.data.dto.CIMPrefixPair;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.services.update.dataset.UpdateDatasetService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UpdateDatasetServiceTest {

    private UpdateDatasetService service;
    private DatabasePort databasePort;

    @BeforeEach
    void setUp() {
        databasePort = mock(DatabasePort.class);
        service = new UpdateDatasetService(databasePort);
    }

    @Test
    void deleteDataset_validDatasetName_callsDeleteOnDatabasePort() {
        service.deleteDataset("test-dataset");

        verify(databasePort).deleteDataset("test-dataset");
    }

    @Test
    void replaceNamespaces_validNamespaces_callsSetPrefixMapping() {
        var namespaces = List.of(
                  mockCIMPrefixPair("ex:", "http://example.org/"),
                  mockCIMPrefixPair("foaf:", "http://xmlns.com/foaf/0.1/")
                                );

        service.replaceNamespaces("test-dataset", namespaces);

        verify(databasePort).setPrefixMapping(eq("test-dataset"), any());
    }

    @Test
    void replaceNamespaces_duplicatePrefix_throwsException() {
        var namespaces = List.of(
                  mockCIMPrefixPair("ex:", "http://example.org/"),
                  mockCIMPrefixPair("ex:", "http://example.com/")
                                );

        assertThrows(IllegalArgumentException.class, () ->
                               service.replaceNamespaces("test-dataset", namespaces)
                    );
    }

    private CIMPrefixPair mockCIMPrefixPair(String substitutedPrefix, String prefix) {
        var pair = mock(CIMPrefixPair.class);
        when(pair.getSubstitutedPrefix()).thenReturn(substitutedPrefix);
        when(pair.getPrefix()).thenReturn(prefix);
        return pair;
    }
}

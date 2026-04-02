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

package org.rdfarchitect.database.inmemory;

import org.apache.jena.shared.PrefixMapping;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.cim.rdf.resources.CIM;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.models.cim.rdf.resources.CIMS;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InMemoryDatabaseAdapterTest {

    private InMemoryDatabase database;
    private InMemoryDatabaseAdapter adapter;

    @BeforeEach
    void setUp() {
        database = mock(InMemoryDatabase.class);
        adapter = new InMemoryDatabaseAdapter(database);
    }

    @Test
    void createEmptyGraph_newDataset_addsDefaultPrefixMapping() {
        var graphIdentifier = new GraphIdentifier("new-dataset", "http://example.com/graph");
        when(database.listDatasets()).thenReturn(List.of("existing-dataset"));

        adapter.createEmptyGraph(graphIdentifier);

        verify(database).create(eq(graphIdentifier), any());
        verify(database).setPrefixMapping(eq("new-dataset"), argThat(prefixMapping ->
                prefixMapping.getNsPrefixMap().entrySet().containsAll(PrefixMapping.Standard.getNsPrefixMap().entrySet())
                        && CIM.namespace.equals(prefixMapping.getNsPrefixURI("cim"))
                        && CIMS.namespace.equals(prefixMapping.getNsPrefixURI("cims"))));
    }

    @Test
    void createEmptyGraph_existingDataset_doesNotAddStandardPrefixMapping() {
        var graphIdentifier = new GraphIdentifier("existing-dataset", "http://example.com/graph");
        when(database.listDatasets()).thenReturn(List.of("existing-dataset"));

        adapter.createEmptyGraph(graphIdentifier);

        verify(database).create(eq(graphIdentifier), any());
        verify(database, never()).setPrefixMapping(anyString(), any(PrefixMapping.class));
    }
}

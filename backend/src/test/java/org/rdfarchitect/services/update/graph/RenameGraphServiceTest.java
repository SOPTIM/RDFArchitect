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

package org.rdfarchitect.services.update.graph;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.shared.impl.PrefixMappingImpl;
import org.apache.jena.sparql.graph.PrefixMappingReadOnly;
import org.apache.jena.vocabulary.DCAT;
import org.apache.jena.vocabulary.OWL2;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphContext;
import org.rdfarchitect.database.GraphIdentifier;

class RenameGraphServiceTest {

    private static final String DATASET = "ds";
    private static final String OLD_URI = "http://graph#old";
    private static final String NEW_URI = "http://graph#new";
    private static final String ONTOLOGY_IRI = "http://graph#old-ontology";

    private DatabasePort databasePort;
    private GraphContext graphContext;
    private Model model;
    private RenameGraphService renameGraphService;

    @BeforeEach
    void setUp() {
        databasePort = mock(DatabasePort.class);
        graphContext = mock(GraphContext.class);
        model = ModelFactory.createDefaultModel();

        when(databasePort.getGraphWithContext(any())).thenReturn(graphContext);
        when(graphContext.begin(ReadWrite.WRITE)).thenReturn(graphContext);
        when(graphContext.getRdfGraph()).thenReturn(model.getGraph());
        when(databasePort.getPrefixMapping(DATASET))
                .thenReturn(new PrefixMappingReadOnly(new PrefixMappingImpl()));

        renameGraphService = new RenameGraphService(databasePort);
    }

    private void givenProfileHeader(String keyword) {
        var ontology = model.createResource(ONTOLOGY_IRI);
        ontology.addProperty(
                model.createProperty(org.apache.jena.vocabulary.RDF.type.getURI()),
                model.createResource(OWL2.Ontology.getURI()));
        if (keyword != null) {
            ontology.addProperty(model.createProperty(DCAT.keyword.getURI()), keyword);
        }
    }

    private String keywordInModel() {
        var it = model.listObjectsOfProperty(model.createProperty(DCAT.keyword.getURI()));
        return it.hasNext() ? it.next().asLiteral().getString() : null;
    }

    @Test
    void renameGraph_withoutKeyword_leavesProfileHeaderUntouched() {
        givenProfileHeader("old label");

        renameGraphService.renameGraph(new GraphIdentifier(DATASET, OLD_URI), NEW_URI, null);

        verify(databasePort).renameGraph(new GraphIdentifier(DATASET, OLD_URI), NEW_URI);
        verify(databasePort, never()).getGraphWithContext(any());
        assertThat(keywordInModel()).isEqualTo("old label");
    }

    @Test
    void renameGraph_withKeyword_replacesExistingKeyword() {
        givenProfileHeader("old label");

        renameGraphService.renameGraph(new GraphIdentifier(DATASET, OLD_URI), NEW_URI, "new label");

        verify(databasePort).renameGraph(new GraphIdentifier(DATASET, OLD_URI), NEW_URI);
        verify(databasePort).getGraphWithContext(new GraphIdentifier(DATASET, NEW_URI));
        verify(graphContext).commit("Renamed schema to " + NEW_URI);
        assertThat(keywordInModel()).isEqualTo("new label");
    }

    @Test
    void renameGraph_withKeywordAndNoKeywordEntry_addsKeyword() {
        givenProfileHeader(null);

        renameGraphService.renameGraph(new GraphIdentifier(DATASET, OLD_URI), NEW_URI, "new label");

        assertThat(keywordInModel()).isEqualTo("new label");
    }

    @Test
    void renameGraph_withKeywordAndNoProfileHeader_doesNotCommit() {
        renameGraphService.renameGraph(new GraphIdentifier(DATASET, OLD_URI), NEW_URI, "new label");

        verify(databasePort).renameGraph(new GraphIdentifier(DATASET, OLD_URI), NEW_URI);
        verify(graphContext, never()).commit(any(String.class));
        assertThat(keywordInModel()).isNull();
    }

    @Test
    void renameGraph_keywordUpdateFails_rollsBackRename() {
        givenProfileHeader("old label");
        when(graphContext.begin(ReadWrite.WRITE)).thenThrow(new IllegalStateException("boom"));

        assertThatThrownBy(
                        () ->
                                renameGraphService.renameGraph(
                                        new GraphIdentifier(DATASET, OLD_URI),
                                        NEW_URI,
                                        "new label"))
                .isInstanceOf(IllegalStateException.class);

        verify(databasePort).renameGraph(new GraphIdentifier(DATASET, OLD_URI), NEW_URI);
        verify(databasePort).renameGraph(new GraphIdentifier(DATASET, NEW_URI), OLD_URI);
    }
}

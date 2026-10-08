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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.vocabulary.DCAT;
import org.apache.jena.vocabulary.OWL2;
import org.apache.jena.vocabulary.RDF;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;

class RenameGraphServiceTest {

    private static final String DATASET = "ds";
    private static final String OLD_URI = "http://graph#old";
    private static final String NEW_URI = "http://graph#new";
    private static final String ONTOLOGY_IRI = "http://graph#old-ontology";
    private static final String DCTERMS_TITLE = "http://purl.org/dc/terms/title";

    private DatabasePort databasePort;
    private Model model;
    private RenameGraphService renameGraphService;

    @BeforeEach
    void setUp() {
        databasePort = mock(DatabasePort.class);
        model = ModelFactory.createDefaultModel();

        renameGraphService = new RenameGraphService(databasePort);
    }

    private void givenProfileHeader() {
        var ontology = model.createResource(ONTOLOGY_IRI);
        ontology.addProperty(
                model.createProperty(RDF.type.getURI()),
                model.createResource(OWL2.Ontology.getURI()));
        ontology.addProperty(model.createProperty(DCAT.keyword.getURI()), "EQ");
        ontology.addProperty(model.createProperty(DCTERMS_TITLE), "Core Equipment Vocabulary");
    }

    private String literalOf(String propertyIri) {
        var it = model.listObjectsOfProperty(model.createProperty(propertyIri));
        return it.hasNext() ? it.next().asLiteral().getString() : null;
    }

    @Test
    void renameGraph_renamesTheGraph() {
        renameGraphService.renameGraph(new GraphIdentifier(DATASET, OLD_URI), NEW_URI);

        verify(databasePort).renameGraph(new GraphIdentifier(DATASET, OLD_URI), NEW_URI);
    }

    /**
     * A schema is named on screen by what its profile header says, and that header is edited in the
     * ontology editor. Renaming the graph it sits in is a different thing and must not reach into
     * it — least of all overwrite the keyword, which is the short badge code.
     */
    @Test
    void renameGraph_leavesTheProfileHeaderUntouched() {
        givenProfileHeader();

        renameGraphService.renameGraph(new GraphIdentifier(DATASET, OLD_URI), NEW_URI);

        verify(databasePort, never()).getGraphWithContext(any());
        verify(databasePort, never()).getPrefixMapping(any());
        assertThat(literalOf(DCTERMS_TITLE)).isEqualTo("Core Equipment Vocabulary");
        assertThat(literalOf(DCAT.keyword.getURI())).isEqualTo("EQ");
    }
}

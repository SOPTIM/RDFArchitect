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

import de.soptim.opencgmes.cimxml.graph.CimProfile;

import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.shared.impl.PrefixMappingImpl;
import org.apache.jena.sparql.graph.PrefixMappingReadOnly;
import org.apache.jena.vocabulary.DCAT;
import org.apache.jena.vocabulary.OWL2;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphContext;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.rdf.graph.GraphUtils;

import java.io.StringReader;

class RenameGraphServiceTest {

    private static final String DATASET = "ds";
    private static final String OLD_URI = "http://graph#old";
    private static final String NEW_URI = "http://graph#new";
    private static final String ONTOLOGY_IRI = "http://graph#old-ontology";
    private static final String DCTERMS_TITLE = "http://purl.org/dc/terms/title";

    /**
     * A CGMES 2.4.15 profile, which has no ontology object: it names itself on the package reached
     * through {@code cims:belongsToCategory}. The fixed version properties are what make cimxml
     * accept the graph as a profile at all.
     */
    private static final String LEGACY_PROFILE =
            """
            @prefix cim:  <http://iec.ch/TC57/2013/CIM-schema-cim16#> .
            @prefix cims: <http://iec.ch/TC57/1999/rdf-schema-extensions-19990926#> .
            @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
            @prefix xsd:  <http://www.w3.org/2001/XMLSchema#> .

            <http://example.org/legacy#Package_LegacyProfile>
                a          cims:ClassCategory ;
                rdfs:label "LegacyProfile"@en .

            <http://example.org/legacy#LegacyVersion>
                cims:belongsToCategory <http://example.org/legacy#Package_LegacyProfile> .

            <http://example.org/legacy#LegacyVersion.shortName>
                rdfs:domain  <http://example.org/legacy#LegacyVersion> ;
                cims:isFixed "LGC"^^xsd:string .

            <http://example.org/legacy#LegacyVersion.entsoeURI>
                rdfs:domain  <http://example.org/legacy#LegacyVersion> ;
                cims:isFixed "http://example.org/LegacyProfile/1"^^xsd:string .
            """;

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

    private void givenOntologyHeader(String title) {
        var ontology = model.createResource(ONTOLOGY_IRI);
        ontology.addProperty(
                model.createProperty(RDF.type.getURI()),
                model.createResource(OWL2.Ontology.getURI()));
        ontology.addProperty(model.createProperty(DCAT.keyword.getURI()), "EQ");
        if (title != null) {
            ontology.addProperty(model.createProperty(DCTERMS_TITLE), title);
        }
    }

    private void givenLegacyProfile() {
        RDFParser.create()
                .source(new StringReader(LEGACY_PROFILE))
                .lang(Lang.TURTLE)
                .parse(model.getGraph());
    }

    private String literalOf(String propertyIri) {
        var it = model.listObjectsOfProperty(model.createProperty(propertyIri));
        return it.hasNext() ? it.next().asLiteral().getString() : null;
    }

    /** The name as the schema list reads it back, which is the only thing the user sees. */
    private String nameAsListed() {
        var readable = GraphUtils.deepCopy(model.getGraph());
        readable.getPrefixMapping().setNsPrefixes(model.getGraph().getPrefixMapping());
        return CimProfile.wrap(readable).getLabel();
    }

    @Test
    void renameGraph_withoutName_leavesTheProfileUntouched() {
        givenOntologyHeader("Core Equipment Vocabulary");

        renameGraphService.renameGraph(new GraphIdentifier(DATASET, OLD_URI), NEW_URI, null);

        verify(databasePort).renameGraph(new GraphIdentifier(DATASET, OLD_URI), NEW_URI);
        verify(databasePort, never()).getGraphWithContext(any());
        assertThat(literalOf(DCTERMS_TITLE)).isEqualTo("Core Equipment Vocabulary");
    }

    @Test
    void renameGraph_writesTheNameToTheOntologyTitle() {
        givenOntologyHeader("Core Equipment Vocabulary");

        renameGraphService.renameGraph(new GraphIdentifier(DATASET, OLD_URI), NEW_URI, "Our Grid");

        verify(databasePort).getGraphWithContext(new GraphIdentifier(DATASET, NEW_URI));
        verify(graphContext).commit("Renamed schema to Our Grid");
        assertThat(literalOf(DCTERMS_TITLE)).isEqualTo("Our Grid");
    }

    @Test
    void renameGraph_leavesTheKeywordAloneSoTheBadgeStaysShort() {
        givenOntologyHeader("Core Equipment Vocabulary");

        renameGraphService.renameGraph(new GraphIdentifier(DATASET, OLD_URI), NEW_URI, "Our Grid");

        assertThat(literalOf(DCAT.keyword.getURI())).isEqualTo("EQ");
    }

    @Test
    void renameGraph_addsATitleToAProfileThatHasNone() {
        givenOntologyHeader(null);

        renameGraphService.renameGraph(new GraphIdentifier(DATASET, OLD_URI), NEW_URI, "Our Grid");

        assertThat(literalOf(DCTERMS_TITLE)).isEqualTo("Our Grid");
    }

    @Test
    void renameGraph_namesALegacyProfileOnItsPackage() {
        givenLegacyProfile();

        renameGraphService.renameGraph(new GraphIdentifier(DATASET, OLD_URI), NEW_URI, "Our Grid");

        verify(graphContext).commit("Renamed schema to Our Grid");
        assertThat(nameAsListed()).isEqualTo("Our Grid");
    }

    @Test
    void renameGraph_keepsTheLanguageTagALegacyLabelCarried() {
        givenLegacyProfile();

        renameGraphService.renameGraph(new GraphIdentifier(DATASET, OLD_URI), NEW_URI, "Our Grid");

        var labels =
                model.listObjectsOfProperty(model.createProperty(RDFS.label.getURI())).toList();
        assertThat(labels).hasSize(1);
        assertThat(labels.getFirst().asLiteral().getLanguage()).isEqualTo("en");
    }

    @Test
    void renameGraph_withNameAndNoProfile_doesNotCommit() {
        renameGraphService.renameGraph(new GraphIdentifier(DATASET, OLD_URI), NEW_URI, "Our Grid");

        verify(databasePort).renameGraph(new GraphIdentifier(DATASET, OLD_URI), NEW_URI);
        verify(graphContext, never()).commit(any(String.class));
        assertThat(literalOf(DCTERMS_TITLE)).isNull();
    }

    @Test
    void renameGraph_nameUpdateFails_rollsBackRename() {
        givenOntologyHeader("Core Equipment Vocabulary");
        when(graphContext.begin(ReadWrite.WRITE)).thenThrow(new IllegalStateException("boom"));

        assertThatThrownBy(
                        () ->
                                renameGraphService.renameGraph(
                                        new GraphIdentifier(DATASET, OLD_URI), NEW_URI, "Our Grid"))
                .isInstanceOf(IllegalStateException.class);

        verify(databasePort).renameGraph(new GraphIdentifier(DATASET, OLD_URI), NEW_URI);
        verify(databasePort).renameGraph(new GraphIdentifier(DATASET, NEW_URI), OLD_URI);
    }
}

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

package org.rdfarchitect.services.update.ontology;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.shared.impl.PrefixMappingImpl;
import org.apache.jena.vocabulary.DCAT;
import org.apache.jena.vocabulary.DCTerms;
import org.apache.jena.vocabulary.OWL2;
import org.apache.jena.vocabulary.XSD;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.api.dto.ontology.OntologyDTO;
import org.rdfarchitect.api.dto.ontology.OntologyEntry;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphContext;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.models.cim.ontology.OntologyFactory;
import org.rdfarchitect.services.ExpandURIService;

import java.util.List;

class UpdateOntologyServiceTest {

    private static final String DATASET = "ds";
    private static final String GRAPH_URI = "http://example.org/graph";
    private static final String NAMESPACE = "http://example.org/profile#";
    private static final String ONTOLOGY_IRI = NAMESPACE + "Ontology";

    private DatabasePort databasePort;
    private Model model;
    private UpdateOntologyService updateOntologyService;

    @BeforeEach
    void setUp() {
        databasePort = mock(DatabasePort.class);
        var graphContext = mock(GraphContext.class);
        model = ModelFactory.createDefaultModel();

        when(databasePort.getGraphWithContext(any())).thenReturn(graphContext);
        when(graphContext.begin(ReadWrite.WRITE)).thenReturn(graphContext);
        when(graphContext.getRdfGraph()).thenReturn(model.getGraph());
        when(databasePort.getPrefixMapping(DATASET)).thenReturn(prefixMapping());

        updateOntologyService =
                new UpdateOntologyService(databasePort, new ExpandURIService(databasePort));
    }

    private PrefixMapping prefixMapping() {
        var prefixMapping = new PrefixMappingImpl();
        prefixMapping.setNsPrefix("dct", DCTerms.getURI());
        prefixMapping.setNsPrefix("dcat", DCAT.getURI());
        prefixMapping.setNsPrefix("owl", OWL2.getURI());
        prefixMapping.setNsPrefix("xsd", XSD.getURI());
        prefixMapping.setNsPrefix("ex", NAMESPACE);
        return prefixMapping;
    }

    private OntologyDTO ontologyWith(OntologyEntry... entries) {
        return new OntologyDTO().setNamespace(NAMESPACE).setEntries(List.of(entries));
    }

    private OntologyEntry literalEntry(String iri, String value, String datatypeIri) {
        return new OntologyEntry()
                .setIri(iri)
                .setIriEntry(false)
                .setValue(value)
                .setDatatypeIri(datatypeIri);
    }

    private String datatypeOf(String propertyIri) {
        var statement =
                model.getProperty(
                        model.createResource(ONTOLOGY_IRI), model.createProperty(propertyIri));
        return statement == null ? null : statement.getObject().asLiteral().getDatatypeURI();
    }

    private String objectIriOf(String propertyIri) {
        var statement =
                model.getProperty(
                        model.createResource(ONTOLOGY_IRI), model.createProperty(propertyIri));
        return statement == null ? null : statement.getObject().asResource().getURI();
    }

    @Test
    void createOntology_entryWithDatatype_keepsDatatypeInsteadOfEntryIri() {
        var ontologyDTO =
                ontologyWith(
                        literalEntry(
                                DCTerms.issued.getURI(),
                                "2026-09-04T11:11:43",
                                XSD.dateTime.getURI()));

        updateOntologyService.createOntology(new GraphIdentifier(DATASET, GRAPH_URI), ontologyDTO);

        assertThat(datatypeOf(DCTerms.issued.getURI())).isEqualTo(XSD.dateTime.getURI());
    }

    @Test
    void createOntology_prefixedDatatype_expandsDatatypeIri() {
        var ontologyDTO = ontologyWith(literalEntry("dct:modified", "2026-09-04", "xsd:date"));

        updateOntologyService.createOntology(new GraphIdentifier(DATASET, GRAPH_URI), ontologyDTO);

        assertThat(datatypeOf(DCTerms.modified.getURI())).isEqualTo(XSD.date.getURI());
    }

    @Test
    void createOntology_prefixedIriEntryValue_expandsValue() {
        var ontologyDTO =
                ontologyWith(
                        new OntologyEntry()
                                .setIri("owl:priorVersion")
                                .setIriEntry(true)
                                .setValue("ex:PreviousVersion"));

        updateOntologyService.createOntology(new GraphIdentifier(DATASET, GRAPH_URI), ontologyDTO);

        assertThat(objectIriOf(OWL2.priorVersion.getURI()))
                .isEqualTo(NAMESPACE + "PreviousVersion");
    }

    @Test
    void replaceOntology_afterReadingBackPlainLiterals_keepsStringDatatype() {
        var graphIdentifier = new GraphIdentifier(DATASET, GRAPH_URI);
        updateOntologyService.createOntology(
                graphIdentifier,
                ontologyWith(
                        literalEntry(DCTerms.title.getURI(), "PowSyBl Open RAO", null),
                        literalEntry(DCAT.keyword.getURI(), "RAO", null)));

        // reading the ontology back reports xsd:string for plain literals - saving that
        // round-tripped DTO again must not turn the datatype into the entry IRI
        var roundTripped = OntologyFactory.createOntologyDTO(model);
        updateOntologyService.replaceOntology(graphIdentifier, roundTripped);

        assertThat(datatypeOf(DCTerms.title.getURI())).isEqualTo(XSD.xstring.getURI());
        assertThat(datatypeOf(DCAT.keyword.getURI())).isEqualTo(XSD.xstring.getURI());
    }
}

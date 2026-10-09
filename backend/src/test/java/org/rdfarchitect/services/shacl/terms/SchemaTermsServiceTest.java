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

package org.rdfarchitect.services.shacl.terms;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.sparql.graph.GraphFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.config.SchemaConfig;
import org.rdfarchitect.context.SessionContext;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseAdapter;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseImpl;
import org.rdfarchitect.services.shacl.validation.SchemaIndexCache;
import org.rdfarchitect.shacl.dto.SchemaTerm;

import java.util.UUID;

/**
 * The terms a constraints editor completes and explains, read out of the workspace's schema.
 *
 * <p>The schema is a small CIM-shaped vocabulary rather than a real profile, so the assertions are
 * about this service — what it collects, what it says about a term, where it says the term can be
 * opened — rather than about CGMES. It carries {@code cims:multiplicity} because that is the one
 * piece of the answer only the CIM loader produces.
 */
class SchemaTermsServiceTest {

    private static final String DATASET = "cgmes";
    private static final GraphIdentifier GRAPH = new GraphIdentifier(DATASET, "http://ex.org/EQ");

    private static final String SCHEMA =
            """
            @prefix rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
            @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
            @prefix owl:  <http://www.w3.org/2002/07/owl#> .
            @prefix cims: <http://iec.ch/TC57/1999/rdf-schema-extensions-19990926#> .
            @prefix cim:  <http://iec.ch/TC57/CIM100#> .

            <http://ex.org/EQ> a owl:Ontology ;
                owl:versionIRI <http://ex.org/EQ/1.0> .

            cim:Wires a cims:ClassCategory ;
                rdfs:label "Wires" .

            cim:ACLineSegment a rdfs:Class ;
                rdfs:label "ACLineSegment" ;
                rdfs:comment "A wire or combination of wires." ;
                cims:belongsToCategory cim:Wires .

            cim:Terminal a rdfs:Class ;
                rdfs:label "A terminal" .

            cim:ACLineSegment.length a rdf:Property ;
                rdfs:label "length" ;
                rdfs:comment "Segment length for calculating line section capabilities." ;
                rdfs:domain cim:ACLineSegment ;
                rdfs:range cim:Length ;
                cims:multiplicity cims:M:1..1 .

            cim:UnitSymbol a rdfs:Class ;
                cims:stereotype "enumeration" .

            cim:UnitSymbol.W a cim:UnitSymbol ;
                rdfs:label "W" .
            """;

    private final InMemoryDatabaseImpl database = new InMemoryDatabaseImpl(new SchemaConfig());
    private final InMemoryDatabaseAdapter databasePort = new InMemoryDatabaseAdapter(database);

    private SchemaTermsService service;

    @BeforeEach
    void setUp() {
        SessionContext.setSessionId(UUID.randomUUID().toString());
        databasePort.createDataset(DATASET);
        var schema = GraphFactory.createDefaultGraph();
        RDFParser.fromString(SCHEMA, Lang.TURTLE).parse(schema);
        databasePort.createGraph(GRAPH, schema);
        service = new SchemaTermsService(databasePort, new SchemaIndexCache(databasePort));
    }

    @AfterEach
    void tearDown() {
        database.listDatasets().forEach(database::deleteDataset);
        SessionContext.clear();
    }

    // -------------------------------------------------------------------------
    // Listing
    // -------------------------------------------------------------------------

    @Test
    void listsTheClassesAndPropertiesTheSchemaDeclares() {
        var terms = service.listTerms(GRAPH);

        assertThat(terms.getProfiles()).contains("http://ex.org/EQ/1.0");
        assertThat(terms.getTerms())
                .filteredOn(term -> term.getKind() == SchemaTerm.Kind.CLASS)
                .extracting(SchemaTerm::getIri)
                .contains(
                        "http://iec.ch/TC57/CIM100#ACLineSegment",
                        "http://iec.ch/TC57/CIM100#Terminal");
        assertThat(terms.getTerms())
                .filteredOn(term -> term.getKind() == SchemaTerm.Kind.PROPERTY)
                .extracting(SchemaTerm::getIri)
                .contains("http://iec.ch/TC57/CIM100#ACLineSegment.length");
    }

    @Test
    void splitsEachTermIntoTheNamespaceAndNameAnEditorInserts() {
        var term = termFor("http://iec.ch/TC57/CIM100#ACLineSegment");

        assertThat(term.getNamespace()).isEqualTo("http://iec.ch/TC57/CIM100#");
        assertThat(term.getLocalName()).isEqualTo("ACLineSegment");
    }

    @Test
    void carriesALabelOnlyWhenItSaysMoreThanTheNameDoes() {
        // Most CIM labels repeat the local name, and a workspace has thousands of terms.
        assertThat(termFor("http://iec.ch/TC57/CIM100#ACLineSegment").getLabel()).isNull();
        assertThat(termFor("http://iec.ch/TC57/CIM100#Terminal").getLabel())
                .isEqualTo("A terminal");
    }

    @Test
    void ordersTermsSoTheSameSchemaAlwaysAnswersTheSame() {
        var first = service.listTerms(GRAPH).getTerms();
        var second = service.listTerms(GRAPH).getTerms();

        assertThat(first).isEqualTo(second);
        assertThat(first).isSortedAccordingTo((a, b) -> a.getIri().compareTo(b.getIri()));
    }

    private SchemaTerm termFor(String iri) {
        return service.listTerms(GRAPH).getTerms().stream()
                .filter(term -> term.getIri().equals(iri))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Term not listed: " + iri));
    }

    // -------------------------------------------------------------------------
    // Detail
    // -------------------------------------------------------------------------

    @Test
    void describesAClassWithItsComment() {
        var detail = service.detailOf(GRAPH, "http://iec.ch/TC57/CIM100#ACLineSegment");

        assertThat(detail.getKind()).isEqualTo(SchemaTerm.Kind.CLASS);
        assertThat(detail.getComment()).isEqualTo("A wire or combination of wires.");
        assertThat(detail.getProfiles()).containsExactly("http://ex.org/EQ/1.0");
        assertThat(detail.getDomains()).isEmpty();
        assertThat(detail.getRanges()).isEmpty();
    }

    @Test
    void describesAPropertyWithItsDomainRangeAndMultiplicity() {
        var detail = service.detailOf(GRAPH, "http://iec.ch/TC57/CIM100#ACLineSegment.length");

        assertThat(detail.getKind()).isEqualTo(SchemaTerm.Kind.PROPERTY);
        assertThat(detail.getComment()).startsWith("Segment length");
        assertThat(detail.getDomains()).containsExactly("http://iec.ch/TC57/CIM100#ACLineSegment");
        assertThat(detail.getRanges()).containsExactly("http://iec.ch/TC57/CIM100#Length");
        assertThat(detail.getMultiplicity()).isEqualTo("1..1");
        assertThat(detail.getMinCount()).isEqualTo(1);
        assertThat(detail.getMaxCount()).isEqualTo(1);
    }

    @Test
    void aTermNoProfileDeclaresHasNoDetail() {
        assertThat(service.detailOf(GRAPH, "http://iec.ch/TC57/CIM100#Nonsense")).isNull();
    }

    // -------------------------------------------------------------------------
    // Following a term to its class
    // -------------------------------------------------------------------------

    @Test
    void aClassCanBeOpenedWhereTheWorkspaceHoldsIt() {
        var detail = service.detailOf(GRAPH, "http://iec.ch/TC57/CIM100#ACLineSegment");

        assertThat(detail.getGraphUri()).isEqualTo("http://ex.org/EQ");
        assertThat(detail.getClassUUID()).isNotNull();
    }

    @Test
    void aClassCarriesThePackageItsDiagramBelongsTo() {
        // Opening the class editor alone leaves the diagram wherever the user last was, so the
        // package is what lets the caller put the class on screen highlighted.
        var detail = service.detailOf(GRAPH, "http://iec.ch/TC57/CIM100#ACLineSegment");

        assertThat(detail.getPackageUUID()).isNotNull();
    }

    @Test
    void aClassInNoPackageNamesNone() {
        var detail = service.detailOf(GRAPH, "http://iec.ch/TC57/CIM100#Terminal");

        assertThat(detail.getClassUUID()).isNotNull();
        assertThat(detail.getPackageUUID()).isNull();
    }

    @Test
    void aPropertyLeadsToTheClassItIsOn() {
        // A property is edited as a row of its class, so its domain is the useful destination.
        var onClass = service.detailOf(GRAPH, "http://iec.ch/TC57/CIM100#ACLineSegment");
        var onProperty = service.detailOf(GRAPH, "http://iec.ch/TC57/CIM100#ACLineSegment.length");

        assertThat(onProperty.getClassUUID()).isEqualTo(onClass.getClassUUID());
        assertThat(onProperty.getGraphUri()).isEqualTo(onClass.getGraphUri());
        assertThat(onProperty.getPackageUUID()).isEqualTo(onClass.getPackageUUID());
    }

    @Test
    void anEnumMemberIsDescribedButNotFollowable() {
        // The index knows a term is an enum member but not which class enumerates it, so following
        // one would mean guessing from its name. Describing it still works.
        var detail = service.detailOf(GRAPH, "http://iec.ch/TC57/CIM100#UnitSymbol.W");

        assertThat(detail).isNotNull();
        assertThat(detail.getKind()).isEqualTo(SchemaTerm.Kind.ENUM_MEMBER);
        assertThat(detail.getClassUUID()).isNull();
        assertThat(detail.getGraphUri()).isNull();
    }
}

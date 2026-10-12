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

package org.rdfarchitect.services.select;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.jena.query.ReadWrite;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.sparql.graph.GraphFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.api.dto.GraphDTO;
import org.rdfarchitect.config.SchemaConfig;
import org.rdfarchitect.context.SessionContext;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseAdapter;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseImpl;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.StringReader;
import java.util.List;
import java.util.UUID;

@SpringBootTest
class QueryDatasetServiceTest {

    private static final String DATASET = "profile-metadata-ds";

    /** A CGMES 3.0 profile: everything it says about itself sits on its ontology object. */
    private static final String CIM_17_PROFILE =
            """
            @prefix cim:     <http://iec.ch/TC57/CIM100#> .
            @prefix cims:    <http://iec.ch/TC57/1999/rdf-schema-extensions-19990926#> .
            @prefix dcat:    <http://www.w3.org/ns/dcat#> .
            @prefix dcterms: <http://purl.org/dc/terms/> .
            @prefix owl:     <http://www.w3.org/2002/07/owl#> .
            @prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#> .

            <http://example.org/current#Ontology>
                a                   owl:Ontology ;
                dcat:keyword        "TST" ;
                owl:versionIRI      <http://example.org/TestProfile/3.0> ;
                owl:versionInfo     "3.0.0"@en ;
                dcterms:title       "Test Vocabulary"@en ;
                dcterms:description "A vocabulary that names itself."@en .

            <http://example.org/current#Package_TestProfile>
                a          cims:ClassCategory ;
                rdfs:label "TestProfile"@en .
            """;

    /** A CGMES 2.4.15 profile: no ontology object, the name lives on the profile's package. */
    private static final String CIM_16_PROFILE =
            """
            @prefix cim:  <http://iec.ch/TC57/2013/CIM-schema-cim16#> .
            @prefix cims: <http://iec.ch/TC57/1999/rdf-schema-extensions-19990926#> .
            @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
            @prefix xsd:  <http://www.w3.org/2001/XMLSchema#> .

            <http://example.org/legacy#Package_LegacyProfile>
                a            cims:ClassCategory ;
                rdfs:label   "LegacyProfile"@en ;
                rdfs:comment "A profile that names itself on its package." .

            <http://example.org/legacy#LegacyVersion>
                a                      rdfs:Class ;
                rdfs:label             "LegacyVersion"@en ;
                cims:belongsToCategory <http://example.org/legacy#Package_LegacyProfile> .

            <http://example.org/legacy#LegacyVersion.shortName>
                rdfs:domain    <http://example.org/legacy#LegacyVersion> ;
                cims:isFixed   "LGC"^^xsd:string .

            <http://example.org/legacy#LegacyVersion.entsoeURI>
                rdfs:domain    <http://example.org/legacy#LegacyVersion> ;
                cims:isFixed   "http://example.org/LegacyProfile/1"^^xsd:string .

            <http://example.org/legacy#LegacyVersion.date>
                rdfs:domain    <http://example.org/legacy#LegacyVersion> ;
                cims:isFixed   "2014-08-07"^^xsd:date .
            """;

    /** Plain RDFS, with no cim namespace at all. */
    private static final String NOT_A_PROFILE =
            """
            @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .

            <http://example.org/plain#Thing> a rdfs:Class ; rdfs:label "Thing"@en .
            """;

    /**
     * The same legacy profile, but spelling the CIM namespace {@code cim16} rather than {@code
     * cim}. Nothing in a profile requires the prefix to be called {@code cim}, and a CIM profile is
     * recognised by what that prefix is bound to.
     */
    private static final String CIM_16_PROFILE_UNDER_ANOTHER_PREFIX =
            CIM_16_PROFILE
                    .replace("@prefix cim:  <", "@prefix cim16: <")
                    .replace("legacy#", "other#");

    /**
     * A legacy profile whose version class is not typed. Uuids are assigned to typed resources
     * only, so this profile names a class the class editor has no id to navigate to.
     */
    private static final String CIM_16_PROFILE_WITH_UNTYPED_VERSION_CLASS =
            """
            @prefix cim:  <http://iec.ch/TC57/2013/CIM-schema-cim16#> .
            @prefix cims: <http://iec.ch/TC57/1999/rdf-schema-extensions-19990926#> .
            @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
            @prefix xsd:  <http://www.w3.org/2001/XMLSchema#> .

            <http://example.org/legacy#Package_LegacyProfile>
                a          cims:ClassCategory ;
                rdfs:label "LegacyProfile"@en .

            <http://example.org/legacy#LegacyVersion>
                rdfs:label             "LegacyVersion"@en ;
                cims:belongsToCategory <http://example.org/legacy#Package_LegacyProfile> .

            <http://example.org/legacy#LegacyVersion.shortName>
                rdfs:domain  <http://example.org/legacy#LegacyVersion> ;
                cims:isFixed "LGC"^^xsd:string .

            <http://example.org/legacy#LegacyVersion.entsoeURI>
                rdfs:domain  <http://example.org/legacy#LegacyVersion> ;
                cims:isFixed "http://example.org/LegacyProfile/1"^^xsd:string .
            """;

    /** A legacy profile holding two "...Version" classes, as a merged or extended one can. */
    private static final String CIM_16_PROFILE_WITH_TWO_VERSION_CLASSES =
            CIM_16_PROFILE
                    + """
            <http://example.org/legacy#ZetaVersion>
                a                      rdfs:Class ;
                rdfs:label             "ZetaVersion"@en ;
                cims:belongsToCategory <http://example.org/legacy#Package_LegacyProfile> .

            <http://example.org/legacy#ZetaVersion.shortName>
                rdfs:domain    <http://example.org/legacy#ZetaVersion> ;
                cims:isFixed   "ZTA"^^xsd:string .

            <http://example.org/legacy#ZetaVersion.entsoeURI>
                rdfs:domain    <http://example.org/legacy#ZetaVersion> ;
                cims:isFixed   "http://example.org/ZetaProfile/1"^^xsd:string .
            """;

    private QueryDatasetService service;
    private DatabasePort databasePort;

    @BeforeEach
    void setUp() {
        SessionContext.setSessionId(UUID.randomUUID().toString());
        databasePort = new InMemoryDatabaseAdapter(new InMemoryDatabaseImpl(new SchemaConfig()));
        service = new QueryDatasetService(databasePort);
    }

    @Test
    void listGraphs_readsTheMetadataOfACurrentProfile() {
        createGraph("http://example.org/graphs/current", CIM_17_PROFILE);

        var graph = onlyGraph();

        assertThat(graph.getUri().toString()).isEqualTo("http://example.org/graphs/current");
        assertThat(graph.getKeyword()).isEqualTo("TST");
        assertThat(graph.getLabel()).isEqualTo("Test Vocabulary");
        assertThat(graph.getDescription()).isEqualTo("A vocabulary that names itself.");
        assertThat(graph.getVersionInfo()).isEqualTo("3.0.0");
        assertThat(graph.getVersionIris()).containsExactly("http://example.org/TestProfile/3.0");
    }

    @Test
    void listGraphs_readsTheMetadataOfALegacyProfile() {
        createGraph("http://example.org/graphs/legacy", CIM_16_PROFILE);

        var graph = onlyGraph();

        assertThat(graph.getKeyword()).isEqualTo("LGC");
        assertThat(graph.getLabel()).isEqualTo("LegacyProfile");
        assertThat(graph.getDescription()).isEqualTo("A profile that names itself on its package.");
        assertThat(graph.getVersionIris()).containsExactly("http://example.org/LegacyProfile/1");
        // CGMES 2.4.15 has nowhere to write a version.
        assertThat(graph.getVersionInfo()).isNull();
    }

    /**
     * A CGMES 2.4.15 profile has no ontology object, so the ontology editor cannot reach its
     * keyword or version IRIs. They are fixed values on the profile's "...Version" class, and a
     * reader has to be sent there instead.
     */
    @Test
    void listGraphs_pointsALegacyProfileAtTheClassThatHoldsItsMetadata() {
        createGraph("http://example.org/graphs/legacy", CIM_16_PROFILE);

        var graph = onlyGraph();

        assertThat(graph.getProfileClassIri()).isEqualTo("http://example.org/legacy#LegacyVersion");
        // The class editor navigates by uuid, so a link needs that rather than the IRI.
        assertThat(graph.getProfileClassUuid()).isNotBlank();
    }

    @Test
    void listGraphs_pointsACurrentProfileAtNoClass() {
        createGraph("http://example.org/graphs/current", CIM_17_PROFILE);

        var graph = onlyGraph();

        assertThat(graph.getProfileClassIri()).isNull();
        assertThat(graph.getProfileClassUuid()).isNull();
    }

    @Test
    void listGraphs_reportsAGraphThatIsNoProfileByItsUriAlone() {
        createGraph("http://example.org/graphs/plain", NOT_A_PROFILE);

        var graph = onlyGraph();

        assertThat(graph.getUri().toString()).isEqualTo("http://example.org/graphs/plain");
        assertThat(graph.getKeyword()).isNull();
        assertThat(graph.getLabel()).isNull();
        assertThat(graph.getDescription()).isNull();
        assertThat(graph.getVersionIris()).isNull();
        assertThat(graph.getVersionInfo()).isNull();
        assertThat(graph.getProfileClassIri()).isNull();
    }

    /**
     * Both CIM generations bind the {@code cim} prefix, to different namespaces, and the dataset
     * can only remember one of them. Each profile has to be read against the namespace it declares
     * itself, or the generation that lost the dataset-wide prefix would report nothing at all.
     */
    @Test
    void listGraphs_readsBothProfileGenerationsInOneDataset() {
        createGraph("http://example.org/graphs/current", CIM_17_PROFILE);
        createGraph("http://example.org/graphs/legacy", CIM_16_PROFILE);

        var graphs = service.listGraphs(DATASET);

        assertThat(graphs).hasSize(2);
        assertThat(labelOf(graphs, "http://example.org/graphs/current"))
                .isEqualTo("Test Vocabulary");
        assertThat(labelOf(graphs, "http://example.org/graphs/legacy")).isEqualTo("LegacyProfile");
    }

    /**
     * Which of the two places a schema is renamed in is decided by whether it has an ontology
     * object, and that is not the same question as it having no version class: a profile that has
     * neither must not be handed an ontology object it would never read back.
     */
    @Test
    void listGraphs_saysWhetherAProfileNamesItselfOnAnOntologyObject() {
        createGraph("http://example.org/graphs/current", CIM_17_PROFILE);
        createGraph("http://example.org/graphs/legacy", CIM_16_PROFILE);
        createGraph("http://example.org/graphs/plain", NOT_A_PROFILE);

        var graphs = service.listGraphs(DATASET);

        assertThat(graphOf(graphs, "http://example.org/graphs/current").isOntologyHeader())
                .isTrue();
        assertThat(graphOf(graphs, "http://example.org/graphs/legacy").isOntologyHeader())
                .isFalse();
        assertThat(graphOf(graphs, "http://example.org/graphs/plain").isOntologyHeader()).isFalse();
    }

    /**
     * The uuid is assigned on commit, so a profile can well name a class the class editor cannot
     * yet navigate to. Reporting the IRI regardless is what keeps such a profile distinguishable
     * from one that is edited through its ontology object.
     */
    @Test
    void listGraphs_reportsTheProfileClassEvenWhereItHasNoUuidYet() {
        createGraph("http://example.org/graphs/legacy", CIM_16_PROFILE_WITH_UNTYPED_VERSION_CLASS);

        var graph = onlyGraph();

        assertThat(graph.getProfileClassIri()).isEqualTo("http://example.org/legacy#LegacyVersion");
        assertThat(graph.getProfileClassUuid()).isNull();
        assertThat(graph.isOntologyHeader()).isFalse();
    }

    /**
     * The dataset's prefixes fill in what a graph does not declare itself, prefix by prefix. A
     * graph that spells the CIM namespace under some other prefix still has no {@code cim} of its
     * own, and without one it would not be read as a CIM profile at all.
     */
    @Test
    void listGraphs_readsAProfileThatSpellsTheCimNamespaceUnderAnotherPrefix() {
        // Teaches the dataset a "cim" prefix for the namespace the second graph calls "cim16".
        createGraph("http://example.org/graphs/legacy", CIM_16_PROFILE);
        createGraph("http://example.org/graphs/other", CIM_16_PROFILE_UNDER_ANOTHER_PREFIX);

        var graphs = service.listGraphs(DATASET);

        assertThat(graphOf(graphs, "http://example.org/graphs/other").getKeyword())
                .isEqualTo("LGC");
        assertThat(graphOf(graphs, "http://example.org/graphs/other").getProfileClassIri())
                .isEqualTo("http://example.org/other#LegacyVersion");
    }

    /**
     * A graph is an unordered set of triples, so picking the first match would send the class
     * editor to whichever class the iterator happened to yield first.
     */
    @Test
    void listGraphs_picksTheSameVersionClassWhereAProfileHasSeveral() {
        createGraph("http://example.org/graphs/legacy", CIM_16_PROFILE_WITH_TWO_VERSION_CLASSES);

        var graph = onlyGraph();

        assertThat(graph.getProfileClassIri()).isEqualTo("http://example.org/legacy#LegacyVersion");
    }

    private GraphDTO onlyGraph() {
        var graphs = service.listGraphs(DATASET);
        assertThat(graphs).hasSize(1);
        return graphs.getFirst();
    }

    private static String labelOf(List<GraphDTO> graphs, String graphUri) {
        return graphOf(graphs, graphUri).getLabel();
    }

    private static GraphDTO graphOf(List<GraphDTO> graphs, String graphUri) {
        return graphs.stream()
                .filter(graph -> graph.getUri().toString().equals(graphUri))
                .findFirst()
                .orElseThrow();
    }

    /**
     * Imports a schema the way the app does, through a committed transaction — which is what
     * assigns the {@code rdfa:uuid} the class editor navigates by. Creating the graph without one
     * leaves every resource without an id.
     */
    private void createGraph(String graphUri, String turtle) {
        var graph = GraphFactory.createDefaultGraph();
        RDFParser.create().source(new StringReader(turtle)).lang(Lang.TURTLE).parse(graph);
        var identifier = new GraphIdentifier(DATASET, graphUri);
        databasePort.createGraph(identifier, graph);
        try (var ctx = databasePort.getGraphWithContext(identifier).begin(ReadWrite.WRITE)) {
            ctx.commit("import");
        }
    }
}

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

package org.rdfarchitect.services.shacl.validation;

import static org.assertj.core.api.Assertions.assertThat;

import de.soptim.opencgmes.cimvocabcheck.core.VersionIri;

import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.sparql.graph.GraphFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.config.SchemaConfig;
import org.rdfarchitect.context.SessionContext;
import org.rdfarchitect.database.DatabaseFixtures;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseAdapter;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseImpl;

import java.time.Duration;
import java.util.UUID;

/** Keeping an indexed schema only as long as the schema it was built from. */
class SchemaIndexCacheTest {

    private static final String WORKSPACE = "cgmes";
    private static final GraphIdentifier SCHEMA_ID = new GraphIdentifier(WORKSPACE, "EQ");

    private static final String SCHEMA =
            """
            @prefix rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
            @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
            @prefix cim:  <http://iec.ch/TC57/CIM100#> .

            cim:ACLineSegment a rdfs:Class .
            """;

    private final InMemoryDatabaseImpl database = new InMemoryDatabaseImpl(new SchemaConfig());
    private final InMemoryDatabaseAdapter databasePort = new InMemoryDatabaseAdapter(database);

    private SchemaIndexCache cache;

    /** The clock the bounded caches below read, so the idle bound can be tested without waiting. */
    private long now = 1_000_000L;

    @BeforeEach
    void setUp() {
        SessionContext.setSessionId(UUID.randomUUID().toString());
        databasePort.createDataset(WORKSPACE);
        var schema = GraphFactory.createDefaultGraph();
        RDFParser.fromString(SCHEMA, Lang.TURTLE).parse(schema);
        DatabaseFixtures.createGraph(databasePort, SCHEMA_ID, schema);
        cache = new SchemaIndexCache(databasePort);
    }

    @AfterEach
    void tearDown() {
        database.listDatasets().forEach(database::deleteDataset);
        SessionContext.clear();
    }

    @Test
    void askingTwiceWithoutAChangeIndexesOnce() {
        assertThat(cache.apiFor(WORKSPACE)).isSameAs(cache.apiFor(WORKSPACE));
    }

    @Test
    void aCommitToTheSchemaMakesTheNextLookupReindex() {
        var before = cache.apiFor(WORKSPACE);
        addClass("http://iec.ch/TC57/CIM100#Terminal");

        var after = cache.apiFor(WORKSPACE);

        assertThat(after).isNotSameAs(before);
        assertThat(
                        after.schemaIndex()
                                .findClass(
                                        NodeFactory.createURI(
                                                "http://iec.ch/TC57/CIM100#Terminal")))
                .isNotEmpty();
    }

    @Test
    void anUndoMakesTheNextLookupReindexToo() {
        addClass("http://iec.ch/TC57/CIM100#Terminal");
        var withTerminal = cache.apiFor(WORKSPACE);
        undo();

        var afterUndo = cache.apiFor(WORKSPACE);

        assertThat(afterUndo).isNotSameAs(withTerminal);
        assertThat(
                        afterUndo
                                .schemaIndex()
                                .findClass(
                                        NodeFactory.createURI(
                                                "http://iec.ch/TC57/CIM100#Terminal")))
                .isEmpty();
    }

    @Test
    void aNewSchemaInTheWorkspaceMakesTheNextLookupReindex() {
        var before = cache.apiFor(WORKSPACE);
        DatabaseFixtures.createGraph(
                databasePort,
                new GraphIdentifier(WORKSPACE, "TP"),
                GraphFactory.createDefaultGraph());

        assertThat(cache.apiFor(WORKSPACE)).isNotSameAs(before);
    }

    @Test
    void twoSessionsHoldingAWorkspaceOfTheSameNameDoNotShareOneIndex() {
        var firstSession = cache.apiFor(WORKSPACE);

        SessionContext.setSessionId(UUID.randomUUID().toString());
        databasePort.createDataset(WORKSPACE);
        DatabaseFixtures.createGraph(databasePort, SCHEMA_ID, GraphFactory.createDefaultGraph());

        assertThat(cache.apiFor(WORKSPACE)).isNotSameAs(firstSession);
    }

    // -------------------------------------------------------------------------
    // Staying inside its bounds
    // -------------------------------------------------------------------------

    /** A cache with the given bounds and a clock the test moves by hand. */
    private SchemaIndexCache bounded(long maxIndexedTriples, Duration maxIdle, int maxEntries) {
        return new SchemaIndexCache(
                databasePort, maxIndexedTriples, maxIdle, maxEntries, () -> now);
    }

    @Test
    void anIndexNothingHasAskedForIsReleased() {
        var bounded = bounded(Long.MAX_VALUE, Duration.ofMinutes(30), 64);
        var first = bounded.apiFor(WORKSPACE);

        now += Duration.ofMinutes(31).toMillis();

        // Sessions end without telling anyone, so nothing else would ever release this.
        assertThat(bounded.apiFor(WORKSPACE)).isNotSameAs(first);
    }

    @Test
    void anIndexStillInUseIsKept() {
        var bounded = bounded(Long.MAX_VALUE, Duration.ofMinutes(30), 64);
        var first = bounded.apiFor(WORKSPACE);

        now += Duration.ofMinutes(20).toMillis();
        var second = bounded.apiFor(WORKSPACE);
        now += Duration.ofMinutes(20).toMillis();

        // Asking again is what keeps it: the bound is on idleness, not on age.
        assertThat(second).isSameAs(first);
        assertThat(bounded.apiFor(WORKSPACE)).isSameAs(first);
    }

    @Test
    void aWorkspaceTooBigForTheBudgetIsStillCached() {
        // Evicting the entry the caller is about to use would rebuild on every call — slower than
        // having no cache at all, and silently so.
        var bounded = bounded(1, Duration.ofDays(1), 64);

        assertThat(bounded.apiFor(WORKSPACE)).isSameAs(bounded.apiFor(WORKSPACE));
    }

    @Test
    void theLeastRecentlyUsedWorkspaceMakesRoomForANewOne() {
        databasePort.createDataset("other");
        DatabaseFixtures.createGraph(
                databasePort,
                new GraphIdentifier("other", "EQ"),
                GraphFactory.createDefaultGraph());
        var bounded = bounded(1, Duration.ofDays(1), 64);

        var first = bounded.apiFor(WORKSPACE);
        bounded.apiFor("other");

        // Two entries and room for one, so the older one went.
        assertThat(bounded.apiFor(WORKSPACE)).isNotSameAs(first);
    }

    @Test
    void theEntryCountIsBoundedForWorkspacesTooSmallToTripTheSizeBound() {
        databasePort.createDataset("other");
        DatabaseFixtures.createGraph(
                databasePort,
                new GraphIdentifier("other", "EQ"),
                GraphFactory.createDefaultGraph());
        var bounded = bounded(Long.MAX_VALUE, Duration.ofDays(1), 1);

        var first = bounded.apiFor(WORKSPACE);
        bounded.apiFor("other");

        assertThat(bounded.apiFor(WORKSPACE)).isNotSameAs(first);
    }

    private void addClass(String classUri) {
        try (var transaction = databasePort.beginTransaction(WORKSPACE, ReadWrite.WRITE)) {
            transaction
                    .graph(SCHEMA_ID.schemaName())
                    .getRdfGraph()
                    .add(
                            Triple.create(
                                    NodeFactory.createURI(classUri),
                                    NodeFactory.createURI(
                                            "http://www.w3.org/1999/02/22-rdf-syntax-ns#type"),
                                    NodeFactory.createURI(
                                            "http://www.w3.org/2000/01/rdf-schema#Class")));
            transaction.commit("add a class");
        }
    }

    private void undo() {
        databasePort.undo(WORKSPACE);
    }

    // -------------------------------------------------------------------------
    // Which graphs a profile comes from
    // -------------------------------------------------------------------------

    private static final String LIBRARY = "../external/entsoe-application-profiles-library/";

    private void loadFile(String schemaName, String file, String extraClass) {
        var graph = GraphFactory.createDefaultGraph();
        RDFParser.source(java.nio.file.Path.of(LIBRARY + file).toUri().toString()).parse(graph);
        if (extraClass != null) {
            graph.add(
                    Triple.create(
                            NodeFactory.createURI(extraClass),
                            org.apache.jena.vocabulary.RDF.type.asNode(),
                            org.apache.jena.vocabulary.RDFS.Class.asNode()));
        }
        DatabaseFixtures.createGraph(
                databasePort, new GraphIdentifier(WORKSPACE, schemaName), graph);
    }

    @Test
    void aCopyOfAProfileUnderTheSameVersionIriStillHasItsOwnTermsIndexed() {
        var equipment = "CGMES/CurrentRelease/RDFS/61970-600-2_Equipment-AP-Voc-RDFS2020.rdf";
        var added = "http://iec.ch/TC57/CIM100#SomethingOnlyTheCopyDeclares";
        loadFile("EQ-official", equipment, null);
        loadFile("EQ-edited", equipment, added);

        var index = cache.apiFor(WORKSPACE).schemaIndex();

        assertThat(index.findClass(NodeFactory.createURI(added))).isNotEmpty();
    }

    @Test
    void aCgmes2ProfileIsIndexedOnceAndNotAlsoUnderASyntheticIri() {
        // CGMES 2.4.15 states its version on a {Profile}Version class, not as owl:versionIRI.
        loadFile(
                "SV",
                "CGMES/PastReleases/v2-4/Original/RDFS/"
                        + "StateVariableProfileRDFSAugmented-v2_4_15-4Sep2020.rdf",
                null);

        var profiles = cache.apiFor(WORKSPACE).schemaIndex().getAllProfiles();

        // The schema graph from setUp is not a profile and keeps its synthetic IRI; the 2.4.15
        // profile must not get one next to its real version IRI.
        assertThat(profiles)
                .filteredOn(ProfileVersionIris::isSynthetic)
                .extracting(VersionIri::iri)
                .noneMatch(iri -> iri.contains("SV"));
        assertThat(profiles)
                .filteredOn(profile -> !ProfileVersionIris.isSynthetic(profile))
                .isNotEmpty();
    }
}

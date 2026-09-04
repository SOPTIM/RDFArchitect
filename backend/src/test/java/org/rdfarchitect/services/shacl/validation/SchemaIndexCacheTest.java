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
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseAdapter;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseImpl;

import java.time.Duration;
import java.util.UUID;

/** Keeping an indexed schema only as long as the schema it was built from. */
class SchemaIndexCacheTest {

    private static final String DATASET = "cgmes";
    private static final GraphIdentifier GRAPH = new GraphIdentifier(DATASET, "http://ex.org/EQ");

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
        databasePort.createDataset(DATASET);
        var schema = GraphFactory.createDefaultGraph();
        RDFParser.fromString(SCHEMA, Lang.TURTLE).parse(schema);
        databasePort.createGraph(GRAPH, schema);
        cache = new SchemaIndexCache(databasePort);
    }

    @AfterEach
    void tearDown() {
        database.listDatasets().forEach(database::deleteDataset);
        SessionContext.clear();
    }

    @Test
    void askingTwiceWithoutAChangeIndexesOnce() {
        assertThat(cache.apiFor(DATASET)).isSameAs(cache.apiFor(DATASET));
    }

    @Test
    void aCommitToTheSchemaMakesTheNextLookupReindex() {
        var before = cache.apiFor(DATASET);
        addClass("http://iec.ch/TC57/CIM100#Terminal");

        var after = cache.apiFor(DATASET);

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
        var withTerminal = cache.apiFor(DATASET);
        undo();

        var afterUndo = cache.apiFor(DATASET);

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
    void aNewGraphInTheWorkspaceMakesTheNextLookupReindex() {
        var before = cache.apiFor(DATASET);
        databasePort.createEmptyGraph(new GraphIdentifier(DATASET, "http://ex.org/TP"));

        assertThat(cache.apiFor(DATASET)).isNotSameAs(before);
    }

    @Test
    void twoSessionsHoldingAWorkspaceOfTheSameNameDoNotShareOneIndex() {
        var firstSession = cache.apiFor(DATASET);

        SessionContext.setSessionId(UUID.randomUUID().toString());
        databasePort.createDataset(DATASET);
        databasePort.createGraph(GRAPH, GraphFactory.createDefaultGraph());

        assertThat(cache.apiFor(DATASET)).isNotSameAs(firstSession);
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
        var first = bounded.apiFor(DATASET);

        now += Duration.ofMinutes(31).toMillis();

        // Sessions end without telling anyone, so nothing else would ever release this.
        assertThat(bounded.apiFor(DATASET)).isNotSameAs(first);
    }

    @Test
    void anIndexStillInUseIsKept() {
        var bounded = bounded(Long.MAX_VALUE, Duration.ofMinutes(30), 64);
        var first = bounded.apiFor(DATASET);

        now += Duration.ofMinutes(20).toMillis();
        var second = bounded.apiFor(DATASET);
        now += Duration.ofMinutes(20).toMillis();

        // Asking again is what keeps it: the bound is on idleness, not on age.
        assertThat(second).isSameAs(first);
        assertThat(bounded.apiFor(DATASET)).isSameAs(first);
    }

    @Test
    void aWorkspaceTooBigForTheBudgetIsStillCached() {
        // Evicting the entry the caller is about to use would rebuild on every call — slower than
        // having no cache at all, and silently so.
        var bounded = bounded(1, Duration.ofDays(1), 64);

        assertThat(bounded.apiFor(DATASET)).isSameAs(bounded.apiFor(DATASET));
    }

    @Test
    void theLeastRecentlyUsedWorkspaceMakesRoomForANewOne() {
        databasePort.createDataset("other");
        databasePort.createGraph(
                new GraphIdentifier("other", "http://ex.org/EQ"),
                GraphFactory.createDefaultGraph());
        var bounded = bounded(1, Duration.ofDays(1), 64);

        var first = bounded.apiFor(DATASET);
        bounded.apiFor("other");

        // Two entries and room for one, so the older one went.
        assertThat(bounded.apiFor(DATASET)).isNotSameAs(first);
    }

    @Test
    void theEntryCountIsBoundedForWorkspacesTooSmallToTripTheSizeBound() {
        databasePort.createDataset("other");
        databasePort.createGraph(
                new GraphIdentifier("other", "http://ex.org/EQ"),
                GraphFactory.createDefaultGraph());
        var bounded = bounded(Long.MAX_VALUE, Duration.ofDays(1), 1);

        var first = bounded.apiFor(DATASET);
        bounded.apiFor("other");

        assertThat(bounded.apiFor(DATASET)).isNotSameAs(first);
    }

    private void addClass(String classUri) {
        try (var ctx = databasePort.getGraphWithContext(GRAPH).begin(ReadWrite.WRITE)) {
            ctx.getRdfGraph()
                    .add(
                            Triple.create(
                                    NodeFactory.createURI(classUri),
                                    NodeFactory.createURI(
                                            "http://www.w3.org/1999/02/22-rdf-syntax-ns#type"),
                                    NodeFactory.createURI(
                                            "http://www.w3.org/2000/01/rdf-schema#Class")));
            ctx.commit("add a class");
        }
    }

    private void undo() {
        databasePort.getGraphWithContext(GRAPH).undo();
    }
}

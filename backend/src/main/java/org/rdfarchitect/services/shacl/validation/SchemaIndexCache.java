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

import de.soptim.opencgmes.cimvocabcheck.core.CgmesSchemaLoader;
import de.soptim.opencgmes.cimvocabcheck.core.SparqlValidationApi;
import de.soptim.opencgmes.cimvocabcheck.core.VersionIri;
import de.soptim.opencgmes.cimvocabcheck.core.schema.RdfsSchemaIndex;

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.GraphUtil;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.sparql.graph.GraphFactory;
import org.rdfarchitect.context.SessionContext;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * The CIM schema of a workspace, indexed for term lookups and kept until the schema changes.
 *
 * <h2>Why this is cached</h2>
 *
 * <p>Indexing a full CGMES profile set takes a few hundred milliseconds, and the schema is what
 * every class-editor edit changes. Rebuilding it per validation call — and Phase 3 validates while
 * the user types — would make editing shapes cost as much as reloading the schema.
 *
 * <h2>Why the key is the graphs' version ids</h2>
 *
 * <p>Every graph's committed version has an id, so the set of ids across a workspace identifies its
 * schema exactly. Keying on that means a commit, an undo and a redo all invalidate the entry by
 * themselves, without the commit path having to know this cache exists — and an undo back to an
 * earlier version correctly reuses nothing, because the version id it returns to is a different id
 * from the one that was cached.
 *
 * <p>The cost of that simplicity is one extra rebuild when something other than the schema is
 * committed: a context commits all of its participants together to keep their version counters in
 * lockstep, so saving a shapes document mints a new version id for the schema graph as well.
 * Editing shapes does not pay it — an editor validates unsaved text, which commits nothing — so it
 * amounts to one reindex per save, measured at 39 ms for two profiles.
 *
 * <h2>Scope: the whole workspace, not one graph</h2>
 *
 * <p>The index spans every graph in the workspace and validation is scoped to all of them. Official
 * ENTSO-E constraints files reference terms across profiles on purpose — the cross-profile files do
 * nothing else — so scoping to the one graph a document belongs to reports its every cross-profile
 * reference as an unknown class. Measured against {@code
 * 61970-456_StateVariables-AP-Con-Complex-Explicit-CrossProfile-SHACL.ttl}: 25 such errors scoped
 * to one profile, none scoped to the workspace. A term that no profile in the workspace declares is
 * still an error, which is the question worth answering.
 */
public class SchemaIndexCache {

    private static final Logger logger = LoggerFactory.getLogger(SchemaIndexCache.class);

    /**
     * How much indexed schema to keep, across every workspace and session, counted in the triples
     * the indexes were built from.
     *
     * <p>Counting entries instead said nothing about memory: eight indexes is a few megabytes of
     * one-profile workspaces and something else entirely of eight full CGMES releases, and the
     * bound that matters is the second one. Triples are a proxy rather than a measurement, but they
     * are the thing an index is proportional to and they are free to count.
     *
     * <p>Five million is roughly a dozen full CGMES profile sets. A workspace bigger than the whole
     * budget is still cached — see {@link #evictBySize} — because evicting the entry a caller is
     * about to use would turn the cache into pure overhead.
     */
    private static final long DEFAULT_MAX_INDEXED_TRIPLES = 5_000_000;

    /**
     * How long an index is kept after the last time anything asked for it.
     *
     * <p>The size bound alone only releases memory when some other workspace needs the room, so a
     * server that indexed a large workspace an hour ago and has been idle since would hold it for
     * ever. Sessions end without telling anyone, and this is what notices.
     */
    private static final Duration DEFAULT_MAX_IDLE = Duration.ofMinutes(30);

    /**
     * A backstop on the number of entries, for workspaces small enough that the size bound never
     * bites. Generous on purpose: it is not the bound that is meant to do the work.
     */
    private static final int DEFAULT_MAX_ENTRIES = 64;

    private final DatabasePort databasePort;

    private final long maxIndexedTriples;

    private final Duration maxIdle;

    private final int maxEntries;

    /** Wall clock, in milliseconds. Injected so the idle bound can be tested without waiting. */
    private final LongSupplier clock;

    public SchemaIndexCache(DatabasePort databasePort) {
        this(
                databasePort,
                DEFAULT_MAX_INDEXED_TRIPLES,
                DEFAULT_MAX_IDLE,
                DEFAULT_MAX_ENTRIES,
                System::currentTimeMillis);
    }

    /** Visible for testing: the bounds and the clock are what the eviction rules are about. */
    SchemaIndexCache(
            DatabasePort databasePort,
            long maxIndexedTriples,
            Duration maxIdle,
            int maxEntries,
            LongSupplier clock) {
        this.databasePort = databasePort;
        this.maxIndexedTriples = maxIndexedTriples;
        this.maxIdle = maxIdle;
        this.maxEntries = maxEntries;
        this.clock = clock;
    }

    /**
     * Keyed by session as well as workspace: the in-memory database is per session, so two sessions
     * each holding a workspace called {@code cgmes} hold two different schemas.
     */
    private record CacheKey(String sessionId, String datasetName) {}

    /**
     * @param indexedTriples what the index was built from, standing in for how much room it takes
     * @param lastUsedMillis when it was last handed out, for the idle bound
     */
    private record Entry(
            Map<String, UUID> graphVersions,
            SparqlValidationApi api,
            long indexedTriples,
            long lastUsedMillis) {

        Entry usedAt(long now) {
            return new Entry(graphVersions, api, indexedTriples, now);
        }
    }

    /** An index and the size it was built from. */
    private record Built(SparqlValidationApi api, long indexedTriples) {}

    /**
     * Access-ordered so the eldest entry is the least recently used. Guarded by {@link #cacheLock};
     * building an index is deliberately done outside that lock, so two threads racing on a cold
     * workspace may both build one and the second simply replaces the first.
     */
    private final LinkedHashMap<CacheKey, Entry> cache = new LinkedHashMap<>(16, 0.75f, true);

    private final Object cacheLock = new Object();

    /**
     * The validation API for {@code datasetName}, built from its graphs the first time it is asked
     * for and after every change to any of them.
     */
    public SparqlValidationApi apiFor(String datasetName) {
        var key = new CacheKey(SessionContext.getSessionId(), datasetName);
        var versions = graphVersions(datasetName);
        synchronized (cacheLock) {
            // Only the idle pass here. Evicting for size before the lookup could drop the very
            // entry this call is about to hit, and rebuilding it would cost more than the room it
            // was holding; the miss path below is where the size bound is enforced, and a miss is
            // the only thing that can push the cache over it.
            evictIdle(clock.getAsLong());
            var cached = cache.get(key);
            if (cached != null && cached.graphVersions().equals(versions)) {
                cache.put(key, cached.usedAt(clock.getAsLong()));
                return cached.api();
            }
        }
        var built = build(datasetName, versions.keySet());
        // Building reads the graphs again, outside the lock, so a commit landing in between means
        // the index describes content newer than the versions it was asked for. Filing it under
        // those would hand a later reader of the *older* version an index of the newer schema —
        // after an undo, terms the schema no longer declares would still validate. The caller gets
        // the index it built either way; only the caching waits for a version that stayed put.
        var versionsAfter = graphVersions(datasetName);
        if (!versionsAfter.equals(versions)) {
            return built.api();
        }
        synchronized (cacheLock) {
            cache.put(
                    key,
                    new Entry(versions, built.api(), built.indexedTriples(), clock.getAsLong()));
            evictBySize();
        }
        return built.api();
    }

    /**
     * Drops indexes nothing has asked for in {@code maxIdle}.
     *
     * <p>Sessions end without telling anyone, so nothing else would ever release the schema of a
     * workspace the server has finished with. Applies whatever an entry's size — an index nobody
     * has wanted for half an hour is worth the rebuild if it turns out to be wanted again.
     *
     * <p>Callers hold {@link #cacheLock}.
     */
    private void evictIdle(long now) {
        cache.entrySet()
                .removeIf(entry -> now - entry.getValue().lastUsedMillis() > maxIdle.toMillis());
    }

    /**
     * Drops the least recently used indexes until the cache is inside both bounds.
     *
     * <p>The most recently used entry is never dropped. A workspace bigger than the whole budget
     * would otherwise be evicted the moment it was cached and rebuilt on every call — slower than
     * having no cache at all, and silently so.
     *
     * <p>Callers hold {@link #cacheLock}.
     */
    private void evictBySize() {
        // Access-ordered, so iteration runs least-recently-used first.
        var iterator = cache.entrySet().iterator();
        var total = cache.values().stream().mapToLong(Entry::indexedTriples).sum();
        while ((total > maxIndexedTriples || cache.size() > maxEntries) && cache.size() > 1) {
            var eldest = iterator.next();
            total -= eldest.getValue().indexedTriples();
            iterator.remove();
        }
    }

    /** Committed version id per graph, which together identify the workspace's schema. */
    private Map<String, UUID> graphVersions(String datasetName) {
        var versions = new LinkedHashMap<String, UUID>();
        for (String graphUri : databasePort.listGraphUris(datasetName)) {
            var identifier = new GraphIdentifier(datasetName, graphUri);
            try (var ctx = databasePort.getGraphWithContext(identifier).begin(ReadWrite.READ)) {
                versions.put(graphUri, ctx.getRdfGraphVersion());
            }
        }
        return versions;
    }

    private Built build(String datasetName, Iterable<String> graphUris) {
        var started = System.currentTimeMillis();
        var copies = new LinkedHashMap<String, Graph>();
        for (String graphUri : graphUris) {
            var identifier = new GraphIdentifier(datasetName, graphUri);
            try (var ctx = databasePort.getGraphWithContext(identifier).begin(ReadWrite.READ)) {
                copies.put(graphUri, copyOf(ctx.getRdfGraph()));
            }
        }

        var cimIndex = indexCimProfiles(copies.values());
        var covered = new LinkedHashSet<>(cimIndex.getAllProfiles());

        var builder = RdfsSchemaIndex.builder();
        cimIndex.profiles().values().forEach(builder::addProfile);
        for (var entry : copies.entrySet()) {
            var fallback = fallbackProfile(entry.getKey(), entry.getValue(), covered);
            if (fallback != null) {
                builder.addProfile(fallback, entry.getValue());
            }
        }
        var index = builder.build();

        var indexedTriples = copies.values().stream().mapToLong(Graph::size).sum();
        logger.info(
                "Indexed the schema of workspace \"{}\" in {} ms: {} graph(s), {} triple(s), {}"
                        + " profile(s), {} class(es), {} property(ies).",
                datasetName,
                System.currentTimeMillis() - started,
                copies.size(),
                indexedTriples,
                index.getAllProfiles().size(),
                index.allClasses().size(),
                index.allProperties().size());
        return new Built(new SparqlValidationApi(index), indexedTriples);
    }

    /**
     * Indexes the graphs that are recognisable CIM profiles. Only this path resolves {@code
     * cims:dataType}, so it is what makes datatype and multiplicity checks possible; a workspace
     * holding no CIM profile at all simply gets nothing from it.
     */
    private static RdfsSchemaIndex indexCimProfiles(Iterable<Graph> graphs) {
        try {
            return CgmesSchemaLoader.indexFromGraphs(graphs);
        } catch (CgmesSchemaLoader.SchemaLoadException e) {
            logger.debug(
                    "No CIM profile could be indexed, falling back to generic RDFS: {}",
                    e.getMessage());
            return RdfsSchemaIndex.builder().build();
        }
    }

    /**
     * The version IRI under which a graph is indexed generically, or {@code null} when it already
     * contributed a CIM profile.
     *
     * <p>A graph reaches this either because it declares no {@code owl:versionIRI} (a profile still
     * being authored here), or because it declares one but is not a CIM profile the loader
     * recognises — a header profile, or a plain RDFS vocabulary. Both are indexed from their
     * triples alone, which finds their classes and properties but not the CIM datatypes. Leaving
     * them out instead would report every term they declare as unknown.
     */
    private static VersionIri fallbackProfile(
            String graphUri, Graph graph, LinkedHashSet<VersionIri> covered) {
        var declared = new ArrayList<>(ProfileVersionIris.declaredIn(graph));
        if (declared.stream().anyMatch(covered::contains)) {
            return null;
        }
        var fallback =
                declared.isEmpty() ? ProfileVersionIris.syntheticFor(graphUri) : declared.get(0);
        return covered.add(fallback) ? fallback : null;
    }

    /**
     * A detached copy of the graph, taken while the read transaction is held.
     *
     * <p>Indexing must not run against the live graph: it outlives the transaction, and the CIM
     * loader asserts a missing {@code cim} prefix on the graph it is given, which on a versioned
     * graph would be a write outside a transaction.
     */
    private static Graph copyOf(Graph live) {
        var copy = GraphFactory.createDefaultGraph();
        GraphUtil.addInto(copy, live);
        copy.getPrefixMapping().setNsPrefixes(live.getPrefixMapping());
        return copy;
    }
}

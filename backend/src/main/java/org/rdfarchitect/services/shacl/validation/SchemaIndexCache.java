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

import lombok.RequiredArgsConstructor;

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.GraphUtil;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.sparql.graph.GraphFactory;
import org.rdfarchitect.context.SessionContext;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.UUID;

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
@RequiredArgsConstructor
public class SchemaIndexCache {

    private static final Logger logger = LoggerFactory.getLogger(SchemaIndexCache.class);

    /**
     * How many indexed schemas to keep. An index of a full CGMES profile set is large, and there is
     * one per workspace per session, so the map is bounded and evicts the least recently used
     * rather than growing with every workspace a server has ever been asked about.
     */
    private static final int MAX_ENTRIES = 8;

    private final DatabasePort databasePort;

    /**
     * Keyed by session as well as workspace: the in-memory database is per session, so two sessions
     * each holding a workspace called {@code cgmes} hold two different schemas.
     */
    private record CacheKey(String sessionId, String datasetName) {}

    private record Entry(Map<String, UUID> graphVersions, SparqlValidationApi api) {}

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
            var cached = cache.get(key);
            if (cached != null && cached.graphVersions().equals(versions)) {
                return cached.api();
            }
        }
        var api = build(datasetName, versions.keySet());
        synchronized (cacheLock) {
            cache.put(key, new Entry(versions, api));
            while (cache.size() > MAX_ENTRIES) {
                var eldest = cache.keySet().iterator().next();
                cache.remove(eldest);
            }
        }
        return api;
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

    private SparqlValidationApi build(String datasetName, Iterable<String> graphUris) {
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

        logger.info(
                "Indexed the schema of workspace \"{}\" in {} ms: {} graph(s), {} profile(s), {}"
                        + " class(es), {} property(ies).",
                datasetName,
                System.currentTimeMillis() - started,
                copies.size(),
                index.getAllProfiles().size(),
                index.allClasses().size(),
                index.allProperties().size());
        return new SparqlValidationApi(index);
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

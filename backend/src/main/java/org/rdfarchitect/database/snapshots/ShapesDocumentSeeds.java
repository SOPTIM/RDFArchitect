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

package org.rdfarchitect.database.snapshots;

import org.apache.jena.graph.Graph;
import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.riot.RiotException;
import org.apache.jena.sparql.graph.GraphFactory;
import org.rdfarchitect.database.ShapesDocument;
import org.rdfarchitect.database.ShapesDocumentSeed;
import org.rdfarchitect.rdf.graph.GraphUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Reads back the shapes documents {@link ShapesDocumentGraphs} wrote into a dataset. */
public final class ShapesDocumentSeeds {

    private ShapesDocumentSeeds() {}

    /**
     * Indexes the dataset's shapes documents by the graph they belong to, each graph's in document
     * order.
     *
     * <p>Collected up front because a dataset lists its graphs in no particular order: a shapes
     * graph may well be visited before its owner.
     *
     * @return the documents by the URI of the graph owning them; a graph with none has no entry
     */
    public static Map<String, List<ShapesDocumentSeed>> of(Dataset dataset) {
        var byOwner = new HashMap<String, List<ShapesDocumentSeed>>();
        var seenByOwner = new HashMap<String, Set<UUID>>();
        for (Iterator<Resource> it = dataset.listModelNames(); it.hasNext(); ) {
            var graphUri = it.next().getURI();
            var name = ShapesGraphNaming.decode(graphUri).orElse(null);
            var documentId = name == null ? null : parseDocumentId(name.documentId());
            if (documentId == null) {
                continue;
            }
            var owner = name.ownerGraphUri();
            seenByOwner.computeIfAbsent(owner, _ -> new HashSet<>()).add(documentId);
            byOwner.computeIfAbsent(owner, _ -> new ArrayList<>())
                    .add(
                            seed(
                                    documentId,
                                    metadataOf(dataset, owner),
                                    dataset.getNamedModel(graphUri).getGraph()));
        }
        collectEmptyDocuments(dataset, byOwner, seenByOwner);
        byOwner.values()
                .forEach(seeds -> seeds.sort(Comparator.comparingInt(ShapesDocumentSeed::order)));
        return byOwner;
    }

    /**
     * Adds the documents the metadata describes but no shapes graph carries.
     *
     * <p>A document holding no triples is not written as a graph — a snapshot store will not take
     * an empty one — so discovering documents by their shapes alone would drop every empty one,
     * name, position and all. The metadata is the complete list.
     */
    private static void collectEmptyDocuments(
            Dataset dataset,
            Map<String, List<ShapesDocumentSeed>> byOwner,
            Map<String, Set<UUID>> seenByOwner) {
        for (Iterator<Resource> it = dataset.listModelNames(); it.hasNext(); ) {
            var graphUri = it.next().getURI();
            var owner = ShapesGraphNaming.decodeMetadata(graphUri).orElse(null);
            if (owner == null) {
                continue;
            }
            var metadata = dataset.getNamedModel(graphUri);
            var seen = seenByOwner.getOrDefault(owner, Set.of());
            for (UUID documentId : ShapesDocumentMetadata.documentIds(metadata)) {
                if (!seen.contains(documentId)) {
                    byOwner.computeIfAbsent(owner, _ -> new ArrayList<>())
                            .add(seed(documentId, metadata, GraphFactory.createDefaultGraph()));
                }
            }
        }
    }

    /** Describes one document as storage recorded it, with defaults for what it did not record. */
    private static ShapesDocumentSeed seed(UUID documentId, Model metadata, Graph stored) {
        var entry = ShapesDocumentMetadata.read(metadata, documentId);
        var rawText = entry.map(ShapesDocumentMetadata.Entry::rawText).orElse(null);
        var graph = rawText == null ? stored : parsedOrStored(rawText, stored);
        return new ShapesDocumentSeed(
                documentId,
                entry.map(ShapesDocumentMetadata.Entry::name)
                        .orElseGet(() -> ShapesDocumentMetadata.defaultName(documentId)),
                entry.map(ShapesDocumentMetadata.Entry::sourceFileName).orElse(null),
                entry.map(ShapesDocumentMetadata.Entry::origin)
                        .orElse(ShapesDocument.Origin.IMPORTED),
                entry.map(ShapesDocumentMetadata.Entry::enabled).orElse(true),
                entry.map(ShapesDocumentMetadata.Entry::order).orElse(0),
                rawText,
                graph);
    }

    /**
     * The shapes as the recorded text states them.
     *
     * <p>A store keeps prefixes per dataset, so a stored shapes graph comes back with none of its
     * own, and hands blank nodes back relabelled. Everything that maps the text to its shapes —
     * line numbers, prefixed names on export — needs the text's own prefixes, and the text is
     * authoritative anyway. Text that no longer parses keeps the stored triples.
     */
    private static Graph parsedOrStored(String rawText, Graph stored) {
        try {
            var parsed = GraphFactory.createDefaultGraph();
            RDFParser.fromString(rawText, Lang.TURTLE).parse(parsed);
            return GraphUtils.normalizeBlankNodes(parsed);
        } catch (RiotException _) {
            return stored;
        }
    }

    private static Model metadataOf(Dataset dataset, String ownerGraphUri) {
        var metadataGraph = ShapesGraphNaming.encodeMetadata(ownerGraphUri);
        return dataset.containsNamedModel(metadataGraph)
                ? dataset.getNamedModel(metadataGraph)
                : ShapesDocumentMetadata.emptyModel();
    }

    /** A name in the reserved namespace without a uuid was not written by this application. */
    private static UUID parseDocumentId(String documentId) {
        try {
            return UUID.fromString(documentId);
        } catch (IllegalArgumentException _) {
            return null;
        }
    }
}

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

import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Reads back the shapes documents {@link ShapesDocumentGraphs} wrote into a dataset. */
public final class ShapesDocumentSeeds {

    private ShapesDocumentSeeds() {}

    /**
     * Reads the dataset's shapes documents, in document order.
     *
     * <p>A document holding no triples is not written as a graph — a snapshot store will not take
     * an empty one — so discovering documents by their shapes alone would drop every empty one,
     * name, position and all. The metadata lists those too.
     *
     * @return the documents, or nothing when the dataset holds none
     */
    public static List<ShapesDocumentSeed> of(Dataset dataset) {
        var metadata =
                dataset.containsNamedModel(ShapesGraphNaming.metadataGraph())
                        ? dataset.getNamedModel(ShapesGraphNaming.metadataGraph())
                        : ShapesDocumentMetadata.emptyModel();
        var ids = new LinkedHashSet<>(ShapesDocumentMetadata.documentIds(metadata));
        for (Iterator<Resource> it = dataset.listModelNames(); it.hasNext(); ) {
            ShapesGraphNaming.decode(it.next().getURI()).ifPresent(ids::add);
        }
        return ids.stream()
                .map(
                        id -> {
                            var name = ShapesGraphNaming.encode(id);
                            return seed(
                                    id,
                                    metadata,
                                    dataset.containsNamedModel(name)
                                            ? dataset.getNamedModel(name).getGraph()
                                            : GraphFactory.createDefaultGraph());
                        })
                .sorted(Comparator.comparingInt(ShapesDocumentSeed::order))
                .toList();
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
                entry.map(ShapesDocumentMetadata.Entry::schemas).orElse(Set.of()),
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
}

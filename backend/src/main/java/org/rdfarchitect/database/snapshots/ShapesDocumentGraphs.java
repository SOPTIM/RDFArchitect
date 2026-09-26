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
import org.rdfarchitect.database.ShapesDocument;
import org.rdfarchitect.rdf.graph.GraphUtils;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A graph's shapes documents written out as named graphs, the form in which both share snapshots
 * and a persisted dataset store them and {@code GraphWithContextCollection} reads them back.
 */
public final class ShapesDocumentGraphs {

    private ShapesDocumentGraphs() {}

    /**
     * One named graph per document holding triples, plus one holding every document's metadata.
     *
     * <p>Empty documents get no graph of their own: a store such as Fuseki will not take an empty
     * one, and a missing graph is how an empty document has always looked. Their metadata is still
     * written, so an empty-but-named document is not lost.
     *
     * <p>Only the triples and the recorded source text travel. A shapes graph's own prefix map is
     * left behind on purpose: stores keep prefixes per dataset, so writing them could overwrite the
     * schema's {@code cim:} mapping with a conflicting one from an imported constraints file. The
     * verbatim source text in the metadata is what preserves an imported file exactly, and loading
     * re-derives the prefixes from it.
     *
     * <p>Must be called inside a transaction on the context holding {@code documents}. The graphs
     * returned are copies.
     *
     * @return graphs by name, in document order with the metadata last
     */
    public static Map<String, Graph> of(
            String ownerGraphUri, Collection<ShapesDocument> documents) {
        var graphs = new LinkedHashMap<String, Graph>();
        var metadata = ShapesDocumentMetadata.emptyModel();
        for (var document : documents) {
            ShapesDocumentMetadata.write(metadata, document);
            if (!document.getGraph().isEmpty()) {
                graphs.put(
                        ShapesGraphNaming.encode(ownerGraphUri, document.getId().toString()),
                        GraphUtils.deepCopy(document.getGraph()));
            }
        }
        if (!metadata.isEmpty()) {
            graphs.put(ShapesGraphNaming.encodeMetadata(ownerGraphUri), metadata.getGraph());
        }
        return graphs;
    }

    /** Whether {@code graphUri} names a shapes or metadata graph of {@code ownerGraphUri}. */
    public static boolean belongsTo(String graphUri, String ownerGraphUri) {
        return ShapesGraphNaming.decode(graphUri)
                        .map(name -> name.ownerGraphUri().equals(ownerGraphUri))
                        .orElse(false)
                || ShapesGraphNaming.decodeMetadata(graphUri)
                        .map(ownerGraphUri::equals)
                        .orElse(false);
    }
}

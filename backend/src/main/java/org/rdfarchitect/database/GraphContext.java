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

package org.rdfarchitect.database;

import org.apache.jena.graph.Graph;
import org.apache.jena.query.ReadWrite;
import org.rdfarchitect.database.inmemory.diagrams.CustomDiagram;
import org.rdfarchitect.models.changelog.ChangeLog;
import org.rdfarchitect.rdf.graph.wrapper.DiagramLayoutDelta;
import org.rdfarchitect.rdf.graph.wrapper.RDFGraphDelta;

import java.util.Map;
import java.util.UUID;

/**
 * Transactional access to the RDF graph, diagram layout, and custom SHACL data for a single named
 * graph. Extends {@link Transactional} so it can be used in try-with-resources.
 */
public interface GraphContext extends Transactional, VersionControl {

    @Override
    GraphContext begin(ReadWrite mode);

    Graph getRdfGraph();

    /**
     * Identifies the graph's current committed content. A commit that changes the graph mints a
     * fresh id, one that leaves it alone keeps it, and an undo or redo returns the id of the
     * content it moves to, so two reads seeing the same id are looking at the same triples.
     *
     * <p>Exposed so that work derived from a graph — indexing its schema for term lookups, say —
     * can be kept until the graph actually changes, without the commit path having to notify
     * anyone.
     */
    UUID getRdfGraphVersion();

    /**
     * Identifies the committed state of the graph's shapes documents — which exist, their triples,
     * their text and their metadata — the way {@link #getRdfGraphVersion()} does for the schema.
     */
    UUID getShapesDocumentsVersion();

    DiagramLayoutDelta getDiagramLayout();

    /**
     * Id of the document {@link #getCustomSHACL()} reads and writes.
     *
     * <p>Reserved and fixed so that it survives a snapshot round-trip and so the single shapes
     * graph of a session created before documents existed always migrates to the same place.
     */
    UUID DEFAULT_SHAPES_DOCUMENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    /** Name given to the default document. */
    String DEFAULT_SHAPES_DOCUMENT_NAME = "custom.ttl";

    /**
     * The graph's shapes documents, keyed by id.
     *
     * <p>The returned map is an ordered snapshot; use {@link #createShapesDocument} and {@link
     * #removeShapesDocument} to change which documents exist.
     */
    Map<UUID, ShapesDocument> getShapesDocuments();

    /**
     * Adds a shapes document to this graph.
     *
     * <p>Must be called in a write transaction. The new document joins the context's transactions
     * and history: an abort discards it, and undoing the commit that created it removes it again.
     */
    ShapesDocument createShapesDocument(String name, ShapesDocument.Origin origin);

    /**
     * Removes a shapes document.
     *
     * <p>Must be called in a write transaction. Which documents exist is versioned like their
     * content, so undoing the commit brings the document back with its shapes, text and metadata.
     */
    void removeShapesDocument(UUID documentId);

    /**
     * The default document's shapes.
     *
     * <p>Kept for callers that predate multiple documents per graph; it creates the default
     * document on first use so it never returns {@code null}.
     */
    RDFGraphDelta getCustomSHACL();

    ChangeLog getChangeLog();

    Map<UUID, CustomDiagram> getCustomDiagrams();
}

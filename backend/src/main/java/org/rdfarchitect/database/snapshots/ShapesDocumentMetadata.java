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

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.ResourceFactory;
import org.rdfarchitect.database.GraphContext;
import org.rdfarchitect.database.ShapesDocument;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads and writes the bookkeeping a shapes document needs beyond its triples: its name, position,
 * whether it is switched on, the file it came from, and the verbatim source text.
 *
 * <p>The text is stored as a literal. That is unusual for an RDF store, but it is the only way a
 * share link can hand back an imported constraints file with its comments and ordering intact —
 * re-serialising the parsed graph would lose both.
 */
public final class ShapesDocumentMetadata {

    private static final String NS = "http://rdfarchitect.soptim.de/ns/snapshot#";
    private static final String DOCUMENT_URI_PREFIX = "urn:rdfa:shacl-doc:";

    private static final Property NAME = ResourceFactory.createProperty(NS + "documentName");
    private static final Property SOURCE_FILE =
            ResourceFactory.createProperty(NS + "documentSourceFile");
    private static final Property ORIGIN = ResourceFactory.createProperty(NS + "documentOrigin");
    private static final Property ENABLED = ResourceFactory.createProperty(NS + "documentEnabled");
    private static final Property ORDER = ResourceFactory.createProperty(NS + "documentOrder");
    private static final Property RAW_TEXT = ResourceFactory.createProperty(NS + "documentRawText");

    private ShapesDocumentMetadata() {}

    /** Describes {@code document} in {@code metadata}. */
    public static void write(Model metadata, ShapesDocument document) {
        var subject = ResourceFactory.createResource(DOCUMENT_URI_PREFIX + document.getId());
        metadata.add(subject, NAME, document.getName());
        metadata.add(subject, ORIGIN, document.getOrigin().name());
        metadata.add(subject, ORDER, metadata.createTypedLiteral(document.getOrder()));
        metadata.add(subject, ENABLED, metadata.createTypedLiteral(document.isEnabled()));
        if (document.getSourceFileName() != null) {
            metadata.add(subject, SOURCE_FILE, document.getSourceFileName());
        }
        if (document.getRawText() != null) {
            metadata.add(subject, RAW_TEXT, document.getRawText());
        }
    }

    /** Creates an empty metadata model. */
    public static Model emptyModel() {
        return ModelFactory.createDefaultModel();
    }

    /**
     * What was recorded about one document.
     *
     * <p>Fields fall back to sensible values when the snapshot predates them, so a snapshot written
     * before this metadata existed still loads: an unnamed document is named after its id, and one
     * with no recorded flag counts as enabled.
     */
    public record Entry(
            String name,
            String sourceFileName,
            ShapesDocument.Origin origin,
            boolean enabled,
            int order,
            String rawText) {}

    /**
     * Every document the metadata describes, in no particular order.
     *
     * <p>The load path finds documents by their shapes graphs, and a document holding no triples
     * has none — an empty snapshot graph is not something Fuseki will accept. This is how such a
     * document is still found, so an empty-but-named one survives a share link.
     */
    public static List<UUID> documentIds(Model metadata) {
        var ids = new ArrayList<UUID>();
        metadata.listSubjects()
                .forEachRemaining(
                        subject -> {
                            if (!subject.isURIResource()
                                    || !subject.getURI().startsWith(DOCUMENT_URI_PREFIX)) {
                                return;
                            }
                            try {
                                ids.add(
                                        UUID.fromString(
                                                subject.getURI()
                                                        .substring(DOCUMENT_URI_PREFIX.length())));
                            } catch (IllegalArgumentException _) {
                                // A subject in our namespace that is not a uuid was not written by
                                // us; ignoring it is safer than failing the whole snapshot load.
                            }
                        });
        return List.copyOf(ids);
    }

    /** Reads what was recorded about {@code documentId}, if anything. */
    public static Optional<Entry> read(Model metadata, UUID documentId) {
        var subject = ResourceFactory.createResource(DOCUMENT_URI_PREFIX + documentId);
        if (!metadata.containsResource(subject)) {
            return Optional.empty();
        }
        var name = literal(metadata, subject, NAME);
        return Optional.of(
                new Entry(
                        name != null ? name : defaultName(documentId),
                        literal(metadata, subject, SOURCE_FILE),
                        readOrigin(metadata, subject),
                        readBoolean(metadata, subject),
                        readInt(metadata, subject),
                        literal(metadata, subject, RAW_TEXT)));
    }

    /** Name for a document the metadata says nothing about. */
    public static String defaultName(UUID documentId) {
        return GraphContext.DEFAULT_SHAPES_DOCUMENT_ID.equals(documentId)
                ? GraphContext.DEFAULT_SHAPES_DOCUMENT_NAME
                : "constraints-" + documentId + ".ttl";
    }

    private static String literal(
            Model metadata, org.apache.jena.rdf.model.Resource subject, Property property) {
        var statement = metadata.getProperty(subject, property);
        return statement == null ? null : statement.getString();
    }

    private static ShapesDocument.Origin readOrigin(
            Model metadata, org.apache.jena.rdf.model.Resource subject) {
        var value = literal(metadata, subject, ORIGIN);
        if (value == null) {
            return ShapesDocument.Origin.IMPORTED;
        }
        try {
            return ShapesDocument.Origin.valueOf(value);
        } catch (IllegalArgumentException _) {
            return ShapesDocument.Origin.IMPORTED;
        }
    }

    private static boolean readBoolean(Model metadata, org.apache.jena.rdf.model.Resource subject) {
        var statement = metadata.getProperty(subject, ENABLED);
        return statement == null || statement.getBoolean();
    }

    private static int readInt(Model metadata, org.apache.jena.rdf.model.Resource subject) {
        var statement = metadata.getProperty(subject, ORDER);
        return statement == null ? 0 : statement.getInt();
    }
}

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

package org.rdfarchitect.services.archive.parts;

import org.apache.jena.graph.Graph;
import org.apache.jena.riot.Lang;
import org.apache.jena.sparql.graph.GraphFactory;
import org.rdfarchitect.database.ShapesDocument;
import org.rdfarchitect.database.ShapesDocumentSeed;
import org.rdfarchitect.exception.database.InvalidContentException;
import org.rdfarchitect.services.archive.WorkspaceArchiveFormat;
import org.rdfarchitect.services.shacl.ShapesDocumentReader;
import org.rdfarchitect.services.shacl.ShapesUnion;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * A schema's {@code shacl.ttl}: the enabled shapes documents applying to the schema, merged.
 *
 * <p>Format 1.0 kept a schema's constraints in this file, and its readers require it for every
 * schema, so it is still written. A reader of 1.0 then keeps the constraints that apply to the
 * schema. From {@link WorkspaceArchiveFormat#SHAPES_DOCUMENTS_SINCE} on, the documents themselves
 * are in {@code shacl/}, and this file only repeats them, so it is not read.
 *
 * <p>In a 1.0 file it holds the constraints the schema had, which become a document applying to the
 * schema.
 */
public final class ShaclPart implements WorkspaceFilePart<GraphScope, ShaclPart.SchemaConstraints> {

    /** The constraints a schema has in a 1.0 file, as written there. */
    public record SchemaConstraints(String text, Graph shapes) {

        /** What a file listing its documents in {@code shacl/} gives a schema here. */
        static final SchemaConstraints NONE =
                new SchemaConstraints("", GraphFactory.createGraphMem());

        boolean isEmpty() {
            return shapes.isEmpty();
        }
    }

    @Override
    public void write(GraphScope source, PartWriter out) {
        var shapes =
                ShapesUnion.enabledFor(
                        source.transaction().shapesDocuments().values(), source.schemaName());
        out.writeRdf(WorkspaceArchiveFormat.SHACL, shapes.getGraph());
    }

    /**
     * @return the constraints, none for a file listing its documents in {@code shacl/}, {@code
     *     null} if the file is missing or cannot be saved as a document
     */
    @Override
    public SchemaConstraints read(PartReader in) {
        if (in.formatVersion().isAtLeast(WorkspaceArchiveFormat.SHAPES_DOCUMENTS_SINCE)) {
            return SchemaConstraints.NONE;
        }
        var text = in.readText(WorkspaceArchiveFormat.SHACL);
        if (text == null) {
            return null;
        }
        try {
            return new SchemaConstraints(
                    text, ShapesDocumentReader.read(text, Lang.TURTLE).getGraph());
        } catch (InvalidContentException e) {
            in.problem(WorkspaceArchiveFormat.SHACL, e.getMessage());
            return null;
        }
    }

    /**
     * Adds the constraints as a document applying to the schema. It is named the way an export of
     * the schema's constraints names its file, and gives the file in the workspace file it came
     * from.
     */
    @Override
    public void apply(GraphScope target, SchemaConstraints constraints) {
        if (constraints.isEmpty()) {
            return;
        }
        var transaction = target.transaction();
        var documents = transaction.shapesDocuments().values();
        transaction.restoreShapesDocument(
                new ShapesDocumentSeed(
                        UUID.randomUUID(),
                        freeName(target.schemaName() + "-shacl", documents),
                        WorkspaceArchiveFormat.schemaEntry(
                                target.schemaName(), WorkspaceArchiveFormat.SHACL),
                        ShapesDocument.Origin.IMPORTED,
                        true,
                        documents.size(),
                        Set.of(target.schemaName()),
                        constraints.text(),
                        constraints.shapes()));
    }

    /**
     * {@code stem.ttl}, or the first free {@code stem-2.ttl}, {@code stem-3.ttl}, ... Two schemas
     * can differ only in case, while document names must not.
     */
    private static String freeName(String stem, Collection<ShapesDocument> documents) {
        var taken =
                documents.stream()
                        .map(document -> document.getName().toLowerCase(Locale.ROOT))
                        .collect(Collectors.toSet());
        var candidate = stem + ".ttl";
        for (var suffix = 2; taken.contains(candidate.toLowerCase(Locale.ROOT)); suffix++) {
            candidate = stem + "-" + suffix + ".ttl";
        }
        return candidate;
    }
}

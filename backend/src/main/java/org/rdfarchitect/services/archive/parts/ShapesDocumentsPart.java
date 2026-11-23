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

import com.fasterxml.jackson.core.type.TypeReference;

import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.rdfarchitect.database.SchemaNames;
import org.rdfarchitect.database.ShapesDocument;
import org.rdfarchitect.database.ShapesDocumentSeed;
import org.rdfarchitect.database.WorkspaceTransaction;
import org.rdfarchitect.exception.database.InvalidContentException;
import org.rdfarchitect.exception.graph.InvalidSchemaNameException;
import org.rdfarchitect.services.archive.ArchivedShapesDocument;
import org.rdfarchitect.services.archive.WorkspaceArchiveFormat;
import org.rdfarchitect.services.shacl.ShapesDocumentReader;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * The shapes documents of the workspace: {@code shacl/documents.json} lists them with everything
 * but their text, and {@code shacl/<id>.ttl} holds each one's text as it is.
 *
 * <p>A document's text is written as it is, not as Turtle written out from its triples, because the
 * text is what the document is: comments, order and spelling included. The import reads it as
 * saving a document does, so a file can bring in nothing a save would refuse.
 *
 * <p>Files of a version before {@link WorkspaceArchiveFormat#SHAPES_DOCUMENTS_SINCE} hold no
 * documents.
 */
public final class ShapesDocumentsPart
        implements WorkspaceFilePart<WorkspaceTransaction, List<ShapesDocumentSeed>> {

    private static final String LIST = WorkspaceArchiveFormat.SHAPES_DOCUMENTS;

    @Override
    public void write(WorkspaceTransaction source, PartWriter out) {
        var documents =
                source.shapesDocuments().values().stream()
                        .sorted(
                                Comparator.comparingInt(ShapesDocument::getOrder)
                                        .thenComparing(ShapesDocument::getId))
                        .toList();
        for (var document : documents) {
            out.writeText(
                    WorkspaceArchiveFormat.shapesDocumentEntry(document.getId()), textOf(document));
        }
        out.writeJson(LIST, documents.stream().map(ArchivedShapesDocument::of).toList());
    }

    /** The text the document holds, or its triples as Turtle for one that only has those. */
    private static String textOf(ShapesDocument document) {
        if (document.getRawText() != null) {
            return document.getRawText();
        }
        var out = new StringWriter();
        RDFDataMgr.write(out, document.getGraph(), RDFFormat.TURTLE);
        return out.toString();
    }

    /**
     * @return the documents, in the order listed
     */
    @Override
    public List<ShapesDocumentSeed> read(PartReader in) {
        if (!in.formatVersion().isAtLeast(WorkspaceArchiveFormat.SHAPES_DOCUMENTS_SINCE)) {
            return List.of();
        }
        var archived = in.readJson(LIST, new TypeReference<List<ArchivedShapesDocument>>() {});
        if (archived == null) {
            return null;
        }
        var seeds = new ArrayList<ShapesDocumentSeed>();
        var ids = new HashSet<UUID>();
        var names = new HashSet<String>();
        var valid = true;
        for (var document : archived) {
            var seed = read(document, ids, names, in);
            if (seed == null) {
                valid = false;
            } else {
                seeds.add(seed);
            }
        }
        return valid ? seeds : null;
    }

    private static ShapesDocumentSeed read(
            ArchivedShapesDocument document, Set<UUID> ids, Set<String> names, PartReader in) {
        var where = "document " + document.id();
        if (!ids.add(document.id())) {
            in.problem(LIST, where + " is there twice.");
            return null;
        }
        var valid = true;
        var name = document.name().strip();
        if (name.isEmpty() || name.codePoints().anyMatch(Character::isISOControl)) {
            in.problem(LIST, where + ": \"" + document.name() + "\" is not a valid name.");
            valid = false;
        } else if (!names.add(name.toLowerCase(Locale.ROOT))) {
            in.problem(LIST, where + ": another document is named \"" + name + "\" too.");
            valid = false;
        }
        for (var schemaName : document.schemas()) {
            if (!isValidSchemaName(schemaName)) {
                in.problem(LIST, where + ": \"" + schemaName + "\" is not a valid schema name.");
                valid = false;
            }
        }
        var file = WorkspaceArchiveFormat.shapesDocumentEntry(document.id());
        var text = in.readText(file);
        if (text == null) {
            return null;
        }
        try {
            var shapes = ShapesDocumentReader.read(text, Lang.TURTLE);
            return valid
                    ? new ShapesDocumentSeed(
                            document.id(),
                            name,
                            document.sourceFileName(),
                            document.origin(),
                            document.enabled(),
                            document.order(),
                            Set.copyOf(document.schemas()),
                            text,
                            shapes.getGraph())
                    : null;
        } catch (InvalidContentException e) {
            in.problem(file, e.getMessage());
            return null;
        }
    }

    private static boolean isValidSchemaName(String name) {
        try {
            return name != null && SchemaNames.validate(name).equals(name);
        } catch (InvalidSchemaNameException _) {
            return false;
        }
    }

    @Override
    public void apply(WorkspaceTransaction target, List<ShapesDocumentSeed> documents) {
        documents.forEach(target::restoreShapesDocument);
    }
}

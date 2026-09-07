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

package org.rdfarchitect.services.shacl;

import org.apache.jena.riot.Lang;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.shacl.dto.ShapesDocumentInfo;

import java.util.List;
import java.util.UUID;

/**
 * Managing the several sets of SHACL shapes a graph can hold.
 *
 * <p>Every enabled document applies; there is no precedence between them (SHACL is conjunctive), so
 * nothing here lets a caller rank one document above another.
 */
public interface SHACLDocumentUseCase {

    /** Lists the graph's documents in {@code order}, without their content. */
    List<ShapesDocumentInfo> listShapesDocuments(GraphIdentifier graphIdentifier);

    /**
     * Adds a document holding {@code content}.
     *
     * @param name display name, unique within the graph
     * @param sourceFileName file it came from, or {@code null} when authored here
     * @param content the shapes, in {@code lang}
     * @param lang syntax of {@code content}; Turtle keeps the text verbatim, anything else is
     *     converted to Turtle once on import because that is what the editor works in
     */
    ShapesDocumentInfo createShapesDocument(
            GraphIdentifier graphIdentifier,
            String name,
            String sourceFileName,
            String content,
            Lang lang);

    /** Returns the document's Turtle source. */
    String getShapesDocumentText(GraphIdentifier graphIdentifier, UUID documentId);

    /** Replaces the document's content with {@code turtle}, keeping the text verbatim. */
    void replaceShapesDocumentText(GraphIdentifier graphIdentifier, UUID documentId, String turtle);

    /**
     * Changes a document's metadata. Any {@code null} argument leaves that field alone.
     *
     * @param order new list position; other documents shift to keep the order dense
     */
    ShapesDocumentInfo updateShapesDocument(
            GraphIdentifier graphIdentifier,
            UUID documentId,
            String name,
            Boolean enabled,
            Integer order);

    /**
     * Deletes a document. The default document cannot be deleted, only cleared.
     *
     * <p>Destructive and <strong>not undoable</strong>: which documents exist is not part of the
     * graph's version history, so an undo restores a deleted document's neighbours but not the
     * document. Confirm before calling.
     */
    void deleteShapesDocument(GraphIdentifier graphIdentifier, UUID documentId);
}

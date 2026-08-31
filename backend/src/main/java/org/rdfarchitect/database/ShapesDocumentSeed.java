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

import java.util.UUID;

/**
 * A shapes document as it arrives from storage, before it belongs to a graph context.
 *
 * <p>Used when loading a snapshot: the context creates the real {@link ShapesDocument} so that its
 * graph joins the transaction machinery, and takes the metadata from here.
 *
 * @param id the document's id; {@link GraphContext#DEFAULT_SHAPES_DOCUMENT_ID} for the default one
 * @param name display name
 * @param sourceFileName file it was imported from, or {@code null}
 * @param origin whether it was imported or authored
 * @param enabled whether its shapes take part in validation and export
 * @param order position in the document list
 * @param rawText verbatim Turtle source, or {@code null} when only the triples were stored
 * @param graph the shapes themselves
 */
public record ShapesDocumentSeed(
        UUID id,
        String name,
        String sourceFileName,
        ShapesDocument.Origin origin,
        boolean enabled,
        int order,
        String rawText,
        Graph graph) {}

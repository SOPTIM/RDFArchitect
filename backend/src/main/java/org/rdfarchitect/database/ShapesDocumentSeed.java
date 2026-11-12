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
import org.rdfarchitect.rdf.graph.GraphUtils;

import java.util.Set;
import java.util.UUID;

/**
 * A shapes document as it arrives from storage, before it belongs to a workspace.
 *
 * <p>Used when loading a snapshot: the workspace creates the real {@link ShapesDocument}, so that
 * it joins the workspace's transactions, and takes the metadata from here.
 *
 * @param id the document's id, kept so that links to it survive a reload
 * @param name display name
 * @param sourceFileName file it was imported from, or {@code null}
 * @param origin whether it was imported or authored
 * @param enabled whether its shapes take part in validation and export
 * @param order position in the document list
 * @param schemas URIs of the schemas the document applies to
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
        Set<String> schemas,
        String rawText,
        Graph graph) {

    /**
     * Describes {@code document} as it stands, with a copy of its shapes and their prefixes. Must
     * be called inside a transaction on the workspace holding it.
     */
    public static ShapesDocumentSeed copyOf(ShapesDocument document) {
        var graph = GraphUtils.deepCopy(document.getGraph());
        graph.getPrefixMapping().setNsPrefixes(document.getGraph().getPrefixMapping());
        return new ShapesDocumentSeed(
                document.getId(),
                document.getName(),
                document.getSourceFileName(),
                document.getOrigin(),
                document.isEnabled(),
                document.getOrder(),
                document.getSchemas(),
                document.getRawText(),
                graph);
    }
}

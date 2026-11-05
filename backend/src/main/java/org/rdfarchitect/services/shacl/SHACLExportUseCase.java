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

import org.apache.jena.graph.Graph;
import org.apache.jena.riot.RDFFormat;
import org.rdfarchitect.database.GraphIdentifier;

import java.io.ByteArrayOutputStream;
import java.util.Collection;
import java.util.UUID;

public interface SHACLExportUseCase {

    /**
     * Export the custom SHACL graph for a given graph identifier.
     *
     * @param graphIdentifier The identifier of the graph to export SHACL for.
     * @param format The format to export the SHACL graph in.
     * @return A ByteArrayOutputStream containing the exported SHACL graph.
     */
    ByteArrayOutputStream exportCustomSHACLGraph(GraphIdentifier graphIdentifier, RDFFormat format);

    /**
     * Export the generated SHACL graph for a given graph identifier with a specific shape URI.
     *
     * @param graphIdentifier The identifier of the graph to export SHACL for.
     * @param format The format to export the SHACL graph in.
     * @return A ByteArrayOutputStream containing the exported SHACL graph.
     */
    ByteArrayOutputStream exportGeneratedSHACLGraph(
            GraphIdentifier graphIdentifier, RDFFormat format);

    /**
     * Export the combined SHACL graph for a given graph identifier with a specific shape URI. This
     * includes both custom and generated SHACL shapes. If a ressource exists in both custom and
     * generated SHACL, the custom SHACL will be used.
     *
     * @param graphIdentifier The identifier of the graph to export SHACL for.
     * @param format The format to export the SHACL graph in.
     * @return A ByteArrayOutputStream containing the exported SHACL graph.
     */
    ByteArrayOutputStream exportCombinedSHACLGraph(
            GraphIdentifier graphIdentifier, RDFFormat format);

    ByteArrayOutputStream exportGeneratedSHACLGraph(Graph graph, RDFFormat format);

    /**
     * Exports the chosen shapes documents as one file, optionally merged with the generated shapes.
     *
     * <p>A document is included because the caller named it, whether or not it is enabled.
     *
     * @param graphIdentifier The identifier of the graph holding the documents.
     * @param format The format to export in.
     * @param documentIds The documents to include.
     * @param includeGenerated Whether to merge in the shapes generated from the schema.
     * @return A ByteArrayOutputStream containing the export.
     */
    ByteArrayOutputStream exportSelectedSHACLGraph(
            GraphIdentifier graphIdentifier,
            RDFFormat format,
            Collection<UUID> documentIds,
            boolean includeGenerated);

    /**
     * Export the custom SHACL namespaces for a given graph identifier.
     *
     * @param graphIdentifier The identifier of the graph to export SHACL namespaces for.
     * @param format The format to export the SHACL namespaces in.
     * @return A ByteArrayOutputStream containing the exported SHACL namespaces.
     */
    ByteArrayOutputStream exportCustomSHACLNamespaces(
            GraphIdentifier graphIdentifier, RDFFormat format);

    /**
     * Export the generated SHACL namespaces for a given graph identifier.
     *
     * @param graphIdentifier The identifier of the graph to export SHACL namespaces for.
     * @param format The format to export the SHACL namespaces in.
     * @return A ByteArrayOutputStream containing the exported SHACL namespaces.
     */
    ByteArrayOutputStream exportGeneratedSHACLNamespaces(
            GraphIdentifier graphIdentifier, RDFFormat format);
}

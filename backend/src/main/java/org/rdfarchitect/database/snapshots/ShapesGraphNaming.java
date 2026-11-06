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

import java.util.Optional;
import java.util.UUID;

/**
 * Naming convention for the named graphs that carry shapes documents inside a stored dataset.
 *
 * <p>A snapshot is a plain RDF dataset, and the load path discovers its content by asking for every
 * named graph it contains ({@code SELECT DISTINCT ?graph}). Shapes therefore need a graph name that
 * cannot be mistaken for a CIM profile: profile graphs are {@code http(s)} IRIs, so a {@code urn:}
 * name is unambiguous.
 *
 * <pre>
 *   urn:rdfa:shacl:1fac0fd3-d607-4b1e-8e3c-0a6f3c2d9b11   one document's triples
 *   urn:rdfa:shacl-meta                                   what the workspace's documents record
 * </pre>
 *
 * <p>Earlier test images wrote graphs named after an owning schema under the same two prefixes. No
 * release holds any. They are still reserved, so they never become schemas, but nothing reads them,
 * and persisting removes them.
 */
public final class ShapesGraphNaming {

    private static final String PREFIX = "urn:rdfa:shacl:";

    private static final String METADATA = "urn:rdfa:shacl-meta";

    private ShapesGraphNaming() {}

    /** Names the graph holding a document's triples. */
    public static String encode(UUID documentId) {
        return PREFIX + documentId;
    }

    /** Names the graph holding what the workspace's documents record beyond their triples. */
    public static String metadataGraph() {
        return METADATA;
    }

    /** Returns whether {@code graphUri} is one of the reserved graphs, of either kind. */
    public static boolean isReserved(String graphUri) {
        return graphUri != null && (graphUri.startsWith(PREFIX) || graphUri.startsWith(METADATA));
    }

    /**
     * The document a graph holds the triples of.
     *
     * @return empty when {@code graphUri} does not name a document's graph
     */
    public static Optional<UUID> decode(String graphUri) {
        if (graphUri == null || !graphUri.startsWith(PREFIX)) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(graphUri.substring(PREFIX.length())));
        } catch (IllegalArgumentException _) {
            return Optional.empty();
        }
    }
}

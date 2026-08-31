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

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Naming convention for the named graphs that carry custom SHACL inside a snapshot dataset.
 *
 * <p>A snapshot is a plain RDF dataset, and the load path discovers its content by asking for every
 * named graph it contains ({@code SELECT DISTINCT ?graph}). Shapes therefore need a graph name that
 * cannot be mistaken for a CIM profile: profile graphs are {@code http(s)} IRIs, so a {@code urn:}
 * name is unambiguous, and putting the owning graph URI inside it keeps the association explicit
 * rather than positional.
 *
 * <pre>
 *   urn:rdfa:shacl:http%3A%2F%2Fiec.ch%2FTC57%2F...%2FEquipment:default
 *   └─ prefix ────┘└─ url-encoded owner graph URI ──────────────────┘└ id
 * </pre>
 *
 * <p>The trailing document id is always {@code default} today, because a graph holds exactly one
 * custom SHACL graph. It is part of the name already so that storing several shapes documents per
 * graph becomes an additive change: readers that ignore the id keep working, and only the writer
 * has to start emitting real ids.
 */
public final class ShapesGraphNaming {

    private static final String PREFIX = "urn:rdfa:shacl:";

    /** Document id used while a graph can only hold a single shapes graph. */
    public static final String DEFAULT_DOCUMENT_ID = "default";

    private ShapesGraphNaming() {}

    /** Identifies the shapes graph {@code documentId} belonging to {@code ownerGraphUri}. */
    public record ShapesGraphName(String ownerGraphUri, String documentId) {}

    /** Builds the snapshot graph name for a shapes document of {@code ownerGraphUri}. */
    public static String encode(String ownerGraphUri, String documentId) {
        return PREFIX + URLEncoder.encode(ownerGraphUri, StandardCharsets.UTF_8) + ":" + documentId;
    }

    /** Returns whether {@code graphUri} names a shapes graph rather than a CIM profile graph. */
    public static boolean isShapesGraph(String graphUri) {
        return graphUri != null && graphUri.startsWith(PREFIX);
    }

    /**
     * Splits a shapes graph name back into its owner and document id.
     *
     * @return empty when {@code graphUri} is not a shapes graph name, or is malformed
     */
    public static Optional<ShapesGraphName> decode(String graphUri) {
        if (!isShapesGraph(graphUri)) {
            return Optional.empty();
        }
        var body = graphUri.substring(PREFIX.length());
        // The owner URI is url-encoded, so the last ':' is always the id separator.
        var separator = body.lastIndexOf(':');
        if (separator <= 0 || separator == body.length() - 1) {
            return Optional.empty();
        }
        var owner = URLDecoder.decode(body.substring(0, separator), StandardCharsets.UTF_8);
        return Optional.of(new ShapesGraphName(owner, body.substring(separator + 1)));
    }
}

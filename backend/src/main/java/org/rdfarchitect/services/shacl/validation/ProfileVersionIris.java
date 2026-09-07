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

package org.rdfarchitect.services.shacl.validation;

import de.soptim.opencgmes.cimvocabcheck.core.VersionIri;

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.vocabulary.OWL2;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * How a graph is named when CIM terms are looked up in it.
 *
 * <p>CIMVocabCheck addresses a profile by its {@code owl:versionIRI} — that is how it can say a
 * class exists in {@code StateVariables-EU/3.0} but not in the profile you are writing shapes
 * against. A graph in RDFArchitect need not carry one: it may be a profile still being authored, or
 * a header profile, and the schema index would then hold nothing for it, making every one of its
 * own classes look unknown. Such a graph therefore gets a synthetic version IRI so its terms are
 * recognised, distinguishable from a real profile IRI by its {@code urn:rdfa:profile:} scheme.
 */
public final class ProfileVersionIris {

    /**
     * Scheme for graphs with no {@code owl:versionIRI}. {@code urn:} cannot collide with the {@code
     * http(s)} IRIs real profiles use.
     */
    private static final String SYNTHETIC_PREFIX = "urn:rdfa:profile:";

    private ProfileVersionIris() {}

    /**
     * Returns the version IRIs the graph declares, in the order they are found, or an empty set
     * when it declares none.
     */
    public static Set<VersionIri> declaredIn(Graph graph) {
        var out = new LinkedHashSet<VersionIri>();
        var it = graph.find(Node.ANY, OWL2.versionIRI.asNode(), Node.ANY);
        while (it.hasNext()) {
            var object = it.next().getObject();
            if (object.isURI()) {
                out.add(new VersionIri(object));
            }
        }
        return out;
    }

    /** The stand-in version IRI for a graph that declares none. */
    public static VersionIri syntheticFor(String graphUri) {
        return VersionIri.of(
                SYNTHETIC_PREFIX + URLEncoder.encode(graphUri, StandardCharsets.UTF_8));
    }

    /** Whether {@code versionIri} was minted by {@link #syntheticFor} rather than declared. */
    public static boolean isSynthetic(VersionIri versionIri) {
        return versionIri.iri().startsWith(SYNTHETIC_PREFIX);
    }
}

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

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    /** A synthetic IRI inside a message: URL-encoded, so it ends at the first delimiter. */
    private static final Pattern SYNTHETIC_IRI =
            Pattern.compile(Pattern.quote(SYNTHETIC_PREFIX) + "[^\\s,\\]>]+");

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

    /**
     * How a profile is named to a user: its version IRI, or for a synthetic one the graph it stands
     * for. The synthetic IRI is an addressing detail of the index; the graph URI is what the user
     * picked and sees in the navigation.
     */
    public static String displayName(String versionIri) {
        if (!versionIri.startsWith(SYNTHETIC_PREFIX)) {
            return versionIri;
        }
        return URLDecoder.decode(
                versionIri.substring(SYNTHETIC_PREFIX.length()), StandardCharsets.UTF_8);
    }

    /** {@code text} with every synthetic version IRI in it replaced by its {@link #displayName}. */
    public static String withDisplayNames(String text) {
        if (text == null || !text.contains(SYNTHETIC_PREFIX)) {
            return text;
        }
        return SYNTHETIC_IRI
                .matcher(text)
                .replaceAll(match -> Matcher.quoteReplacement(displayName(match.group())));
    }

    /** Whether {@code versionIri} was minted by {@link #syntheticFor} rather than declared. */
    public static boolean isSynthetic(VersionIri versionIri) {
        return versionIri.iri().startsWith(SYNTHETIC_PREFIX);
    }
}

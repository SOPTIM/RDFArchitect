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

package org.rdfarchitect.services.update.graph;

import static org.rdfarchitect.rdf.RDFUtils.withColon;
import static org.rdfarchitect.rdf.RDFUtils.withoutColon;

import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.shared.impl.PrefixMappingImpl;
import org.rdfarchitect.exception.graph.InvalidPrefixException;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * How every prefix of a {@link PrefixComparison} stands once the answers to it are folded in: which
 * namespace keeps it, which ones are renamed and which ones are imported without one. Settled
 * before anything is stored, which is what makes the result independent of the order the files are
 * imported in.
 */
public final class ResolvedPrefixes {

    private static final ResolvedPrefixes NONE = new ResolvedPrefixes(Map.of());

    private static final String PROBE_NAMESPACE = "http://example.org/probe#";

    /** prefix (without a trailing colon) to what is to happen to it. */
    private final Map<String, Decision> decisions;

    private ResolvedPrefixes(Map<String, Decision> decisions) {
        this.decisions = decisions;
    }

    static ResolvedPrefixes none() {
        return NONE;
    }

    /**
     * Folds the answers to a comparison. A binding without an answer keeps its default: the prefix
     * stays with the dataset, or with the first file to declare it, and the rest are imported
     * without one. An answer to a prefix that was never compared is checked but not acted on, which
     * rejects a request contradicting itself without letting a caller rewrite prefixes it was not
     * asked about.
     *
     * @throws InvalidPrefixException if a rename names a prefix that cannot be bound, or two
     *     namespaces end up on the same prefix
     */
    static ResolvedPrefixes of(
            List<PrefixComparison> comparisons, List<PrefixResolutionDTO> resolutions) {
        if (comparisons.isEmpty()) {
            return NONE;
        }
        var decisions = new LinkedHashMap<String, Decision>();
        for (var comparison : comparisons) {
            decisions.put(
                    withoutColon(comparison.prefix()), new Decision(defaultHolderOf(comparison)));
        }
        var claimants = new HashMap<String, String>();
        for (var resolution : resolutions) {
            if (resolution.iri() == null || resolution.action() == null) {
                continue;
            }
            claimPrefix(claimants, resolution);
            var decision = decisions.get(withoutColon(resolution.prefix()));
            if (decision == null) {
                continue;
            }
            decision.apply(resolution);
        }
        var folded = new ResolvedPrefixes(Map.copyOf(decisions));
        folded.rejectClaimsOnPrefixesThatStay(claimants);
        return folded;
    }

    /**
     * Notes where an answer leaves its namespace, and rejects the two prefixes the import cannot
     * bind: one RDF does not allow as a name, and one two namespaces were sent to, which would
     * leave whichever of them is written first without a prefix.
     */
    private static void claimPrefix(Map<String, String> claimants, PrefixResolutionDTO resolution) {
        var claimed =
                switch (resolution.action()) {
                    case KEEP -> withoutColon(resolution.prefix());
                    case RENAME -> validatedRenameOf(resolution);
                    case DROP -> null;
                };
        if (claimed == null) {
            return;
        }
        var claimant = claimants.putIfAbsent(claimed, resolution.iri());
        if (claimant != null && !claimant.equals(resolution.iri())) {
            throw InvalidPrefixException.contested(withColon(claimed));
        }
    }

    /**
     * Answers may leave bindings out, and one nobody wrote about stays where it is, so a rename
     * onto its prefix would rebind a namespace that was never asked about.
     */
    private void rejectClaimsOnPrefixesThatStay(Map<String, String> claimants) {
        for (var claim : claimants.entrySet()) {
            var decision = decisions.get(claim.getKey());
            if (decision != null && !decision.isFree() && !decision.holds(claim.getValue())) {
                throw InvalidPrefixException.contested(withColon(claim.getKey()));
            }
        }
    }

    /**
     * The prefix a rename asks for. The empty one is refused: Jena reads it as the default
     * namespace, which is a binding of its own and not a rename.
     */
    private static String validatedRenameOf(PrefixResolutionDTO resolution) {
        var newPrefix = withoutColon(resolution.newPrefix());
        if (newPrefix.isEmpty()) {
            throw InvalidPrefixException.notAName(resolution.newPrefix());
        }
        try {
            new PrefixMappingImpl().setNsPrefix(newPrefix, PROBE_NAMESPACE);
        } catch (PrefixMapping.IllegalPrefixException _) {
            throw InvalidPrefixException.notAName(resolution.newPrefix());
        }
        return newPrefix;
    }

    private static String defaultHolderOf(PrefixComparison comparison) {
        if (comparison.workspace() != null) {
            return comparison.workspace().iri();
        }
        return comparison.imported().isEmpty() ? null : comparison.imported().getFirst().iri();
    }

    /**
     * Rewrites the prefixes of one file according to the decisions taken. Every binding gives its
     * prefix up before any of them takes a new one, or a rename into a prefix another binding of
     * the same mapping is leaving would come out differently depending on the order the mapping
     * hands its bindings out in.
     */
    void applyTo(PrefixMapping prefixes) {
        if (decisions.isEmpty()) {
            return;
        }
        var renamed = new LinkedHashMap<String, String>();
        for (var binding : Map.copyOf(prefixes.getNsPrefixMap()).entrySet()) {
            var decision = decisions.get(binding.getKey());
            if (decision == null || decision.holds(binding.getValue())) {
                continue;
            }
            prefixes.removeNsPrefix(binding.getKey());
            var newPrefix = decision.renameOf(binding.getValue());
            if (newPrefix != null) {
                renamed.put(newPrefix, binding.getValue());
            }
        }
        renamed.forEach(prefixes::setNsPrefix);
    }

    /**
     * The prefixes the dataset is to have once the decisions are applied, or empty when they leave
     * it as it is. Moving a prefix of the dataset is what makes room for an imported namespace.
     */
    Optional<PrefixMapping> rewriteWorkspacePrefixes(PrefixMapping current) {
        var before = Map.copyOf(current.getNsPrefixMap());
        var rewritten = new PrefixMappingImpl().setNsPrefixes(before);
        applyTo(rewritten);
        return before.equals(rewritten.getNsPrefixMap())
                ? Optional.empty()
                : Optional.of(rewritten);
    }

    /** What is to happen to one prefix. */
    private static final class Decision {

        private String holderIri;
        private final Map<String, String> renamesByIri = new HashMap<>();

        private Decision(String holderIri) {
            this.holderIri = holderIri;
        }

        /**
         * A renamed binding hands its prefix on; who gets it is decided by a KEEP, or by nobody.
         */
        private void apply(PrefixResolutionDTO resolution) {
            switch (resolution.action()) {
                case KEEP -> holderIri = resolution.iri();
                case RENAME -> {
                    renamesByIri.put(resolution.iri(), withoutColon(resolution.newPrefix()));
                    if (resolution.iri().equals(holderIri)) {
                        holderIri = null;
                    }
                }
                case DROP -> {
                    renamesByIri.remove(resolution.iri());
                    if (resolution.iri().equals(holderIri)) {
                        holderIri = null;
                    }
                }
            }
        }

        private boolean holds(String iri) {
            return iri.equals(holderIri);
        }

        private boolean isFree() {
            return holderIri == null;
        }

        private String renameOf(String iri) {
            return renamesByIri.get(iri);
        }
    }
}

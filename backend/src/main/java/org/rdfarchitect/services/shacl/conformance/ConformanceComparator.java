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

package org.rdfarchitect.services.shacl.conformance;

import org.apache.jena.shared.PrefixMapping;
import org.rdfarchitect.shacl.dto.ConformanceFinding;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Compares what a schema implies with what a constraints document asserts.
 *
 * <p>The distinction that matters is between a document that is <em>stricter</em> than the schema
 * and one that <em>contradicts</em> it. Being stricter is normal and often the point of a profile:
 * the schema allows many values, the profile permits one. Contradicting is drift — the two cannot
 * both be satisfied, so data valid against one is invalid against the other, and somebody has
 * changed something without the other side following.
 *
 * <p>Coverage is a third thing again, and not a disagreement. Constraints the documents say nothing
 * about are reported so a gap is visible, but they are not scored against agreement: official
 * releases split their rules over several files, some of which carry one cross-profile rule, and
 * counting silence as disagreement made such a file read as "0 of 49 agree".
 */
final class ConformanceComparator {

    private ConformanceComparator() {}

    /** Every disagreement, worst first, then in class and property order. */
    static List<ConformanceFinding> compare(
            Map<EffectiveConstraints.Key, EffectiveConstraints.Constraint> schema,
            EffectiveConstraints.Asserted documents,
            PrefixMapping prefixes) {
        var findings = new ArrayList<ConformanceFinding>();
        var document = documents.constraints();

        schema.forEach(
                (key, implied) -> {
                    var asserted = document.get(key);
                    if (asserted == null) {
                        findings.add(
                                finding(
                                        ConformanceFinding.Kind.MISSING_IN_DOCUMENT,
                                        key,
                                        describe(implied, prefixes),
                                        null,
                                        "The schema implies this constraint; no constraints"
                                                + " document states it.",
                                        List.of()));
                        return;
                    }
                    disagreement(key, implied, asserted, statedIn(documents, key), prefixes)
                            .ifPresent(findings::add);
                });

        document.forEach(
                (key, asserted) -> {
                    if (!schema.containsKey(key)) {
                        findings.add(
                                finding(
                                        ConformanceFinding.Kind.NOT_IN_SCHEMA,
                                        key,
                                        null,
                                        describe(asserted, prefixes),
                                        "The document constrains this property, but the schema does"
                                                + " not have it on this class.",
                                        statedIn(documents, key)));
                    }
                });

        findings.sort(
                Comparator.comparing((ConformanceFinding f) -> f.getKind().ordinal())
                        .thenComparing(ConformanceFinding::getTargetClass)
                        .thenComparing(ConformanceFinding::getPath));
        return List.copyOf(findings);
    }

    /**
     * How these two disagree, if they do.
     *
     * <p>One finding per property rather than one per clause: a property whose datatype and
     * cardinality both drifted is one thing that went wrong, and reporting it twice would make the
     * list longer without making it more useful.
     */
    private static java.util.Optional<ConformanceFinding> disagreement(
            EffectiveConstraints.Key key,
            EffectiveConstraints.Constraint schema,
            EffectiveConstraints.Constraint document,
            List<String> statedIn,
            PrefixMapping prefixes) {
        var contradictions = contradictions(schema, document, prefixes);
        if (!contradictions.isEmpty()) {
            return java.util.Optional.of(
                    finding(
                            ConformanceFinding.Kind.CONTRADICTED,
                            key,
                            describe(schema, prefixes),
                            describe(document, prefixes),
                            String.join(" ", contradictions),
                            statedIn));
        }
        if (schema.equals(document)) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(
                finding(
                        ConformanceFinding.Kind.DIFFERENT,
                        key,
                        describe(schema, prefixes),
                        describe(document, prefixes),
                        "Both can be satisfied, but the schema and the document do not say the same"
                                + " thing.",
                        statedIn));
    }

    private static List<String> contradictions(
            EffectiveConstraints.Constraint schema,
            EffectiveConstraints.Constraint document,
            PrefixMapping prefixes) {
        var reasons = new ArrayList<String>();
        disjoint(schema.dataTypes(), document.dataTypes())
                .ifPresent(
                        pair ->
                                reasons.add(
                                        "A value cannot be both %s and %s."
                                                .formatted(
                                                        terms(schema.dataTypes(), prefixes),
                                                        terms(document.dataTypes(), prefixes))));
        disjoint(schema.valueClasses(), document.valueClasses())
                .ifPresent(
                        pair ->
                                reasons.add(
                                        "A value cannot be an instance of both %s and %s."
                                                .formatted(
                                                        terms(schema.valueClasses(), prefixes),
                                                        terms(document.valueClasses(), prefixes))));
        disjoint(schema.nodeKinds(), document.nodeKinds())
                .ifPresent(
                        pair ->
                                reasons.add(
                                        "A value cannot be both %s and %s."
                                                .formatted(
                                                        terms(schema.nodeKinds(), prefixes),
                                                        terms(document.nodeKinds(), prefixes))));
        if (exceeds(schema.minCount(), document.maxCount())) {
            reasons.add(
                    "The schema requires at least %d, the document allows at most %d."
                            .formatted(schema.minCount(), document.maxCount()));
        }
        if (exceeds(document.minCount(), schema.maxCount())) {
            reasons.add(
                    "The document requires at least %d, the schema allows at most %d."
                            .formatted(document.minCount(), schema.maxCount()));
        }
        return reasons;
    }

    /** Two stated sets with nothing in common: both apply, so nothing can satisfy them. */
    private static java.util.Optional<Boolean> disjoint(Set<String> left, Set<String> right) {
        if (left.isEmpty() || right.isEmpty()) {
            return java.util.Optional.empty();
        }
        var shared = new LinkedHashSet<>(left);
        shared.retainAll(right);
        return shared.isEmpty() ? java.util.Optional.of(true) : java.util.Optional.empty();
    }

    private static boolean exceeds(Integer minimum, Integer maximum) {
        return minimum != null && maximum != null && minimum > maximum;
    }

    private static List<String> statedIn(
            EffectiveConstraints.Asserted documents, EffectiveConstraints.Key key) {
        return documents.statedIn().getOrDefault(key, List.of());
    }

    private static ConformanceFinding finding(
            ConformanceFinding.Kind kind,
            EffectiveConstraints.Key key,
            String schemaSays,
            String documentSays,
            String message,
            List<String> statedIn) {
        return ConformanceFinding.builder()
                .kind(kind)
                .targetClass(key.targetClass())
                .path(key.path())
                .schemaSays(schemaSays)
                .documentSays(documentSays)
                .message(message)
                .statedIn(List.copyOf(statedIn))
                .build();
    }

    /** A constraint in words, listing only what was actually stated. */
    static String describe(EffectiveConstraints.Constraint constraint, PrefixMapping prefixes) {
        var parts = new ArrayList<String>();
        if (constraint.minCount() != null || constraint.maxCount() != null) {
            parts.add(
                    "%s..%s"
                            .formatted(
                                    constraint.minCount() == null ? "0" : constraint.minCount(),
                                    constraint.maxCount() == null ? "n" : constraint.maxCount()));
        }
        if (!constraint.dataTypes().isEmpty()) {
            parts.add(terms(constraint.dataTypes(), prefixes));
        }
        if (!constraint.valueClasses().isEmpty()) {
            parts.add("of class " + terms(constraint.valueClasses(), prefixes));
        }
        if (!constraint.nodeKinds().isEmpty()) {
            parts.add(terms(constraint.nodeKinds(), prefixes));
        }
        return String.join(", ", parts);
    }

    private static String terms(Set<String> iris, PrefixMapping prefixes) {
        return iris.stream()
                .map(iri -> term(iri, prefixes))
                .sorted()
                .reduce((a, b) -> a + " and " + b)
                .orElse("");
    }

    private static String term(String iri, PrefixMapping prefixes) {
        var shortened = prefixes.shortForm(iri);
        return shortened.equals(iri) ? "<" + iri + ">" : shortened;
    }
}

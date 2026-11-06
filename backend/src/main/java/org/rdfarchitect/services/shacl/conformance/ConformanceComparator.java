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

import org.apache.jena.graph.Node;
import org.apache.jena.shared.PrefixMapping;
import org.rdfarchitect.services.shacl.effective.ClassHierarchy;
import org.rdfarchitect.services.shacl.effective.EffectiveConstraints;
import org.rdfarchitect.shacl.dto.ConformanceFinding;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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

    /**
     * As {@link #compare(Map, EffectiveConstraints.Asserted, PrefixMapping, ClassHierarchy, Set)},
     * knowing nothing of the schema beyond what it implies.
     */
    static List<ConformanceFinding> compare(
            Map<EffectiveConstraints.Key, EffectiveConstraints.Constraint> schema,
            EffectiveConstraints.Asserted documents,
            PrefixMapping prefixes) {
        return compare(schema, documents, prefixes, ClassHierarchy.NONE, Set.of());
    }

    /**
     * Every disagreement, worst first, then in class and property order.
     *
     * @param hierarchy the schema's classes, so a value type is compared by the instances it admits
     *     rather than by the classes it names
     * @param properties every property the schema declares, so a constraint on a property no
     *     profile has can be told apart from one on a property the class does not have
     */
    static List<ConformanceFinding> compare(
            Map<EffectiveConstraints.Key, EffectiveConstraints.Constraint> schema,
            EffectiveConstraints.Asserted documents,
            PrefixMapping prefixes,
            ClassHierarchy hierarchy,
            Set<String> properties) {
        var findings = new ArrayList<ConformanceFinding>();
        var context = new Context(prefixes, hierarchy);
        var document = documents.constraints();
        var advisory = documents.advisory();

        schema.forEach(
                (key, implied) -> {
                    var asserted = document.get(key);
                    if (asserted != null) {
                        disagreement(key, implied, asserted, statedIn(documents, key), context)
                                .ifPresent(findings::add);
                        return;
                    }
                    var reported = advisory.get(key);
                    if (reported != null) {
                        findings.add(advisoryOnly(key, implied, reported, documents, context));
                        return;
                    }
                    findings.add(
                            finding(
                                    ConformanceFinding.Kind.MISSING_IN_DOCUMENT,
                                    key,
                                    context.describe(implied),
                                    null,
                                    "The schema implies this constraint; no constraints"
                                            + " document states it.",
                                    List.of()));
                });

        var stated = new LinkedHashSet<>(document.keySet());
        stated.addAll(advisory.keySet());
        stated.forEach(
                key -> {
                    if (!schema.containsKey(key)) {
                        var asserted = document.getOrDefault(key, advisory.get(key));
                        findings.add(
                                finding(
                                        ConformanceFinding.Kind.NOT_IN_SCHEMA,
                                        key,
                                        null,
                                        context.describe(asserted),
                                        notInSchema(key, hierarchy, properties),
                                        statedIn(documents, key)));
                    }
                });

        findings.sort(
                Comparator.comparing((ConformanceFinding f) -> f.getKind().ordinal())
                        .thenComparing(ConformanceFinding::getTargetClass)
                        .thenComparing(ConformanceFinding::getPath));
        return List.copyOf(findings);
    }

    /** Why the schema implies nothing for a key a document constrains. */
    private static String notInSchema(
            EffectiveConstraints.Key key, ClassHierarchy hierarchy, Set<String> properties) {
        if (!properties.isEmpty() && !properties.contains(key.path())) {
            return "The document constrains this property, but no schema in the workspace declares"
                    + " it.";
        }
        if (!hierarchy.isEmpty() && !hierarchy.declares(key.targetClass())) {
            return "The document constrains this class, but no schema in the workspace declares it.";
        }
        return "The document constrains this property, but the schema does not have it on this"
                + " class.";
    }

    /** How findings are worded and value types compared, fixed for one comparison. */
    private record Context(PrefixMapping prefixes, ClassHierarchy hierarchy) {

        String describe(EffectiveConstraints.Constraint constraint) {
            return EffectiveConstraints.describe(constraint, prefixes, hierarchy);
        }

        String terms(Set<String> iris) {
            return EffectiveConstraints.terms(iris, prefixes);
        }

        String classes(Set<String> iris) {
            return EffectiveConstraints.terms(hierarchy.roots(iris), prefixes);
        }

        /**
         * The types a value may carry under {@code constraint}, or {@code null} when it states no
         * value type. {@code sh:class} admits subclasses and a value-type list does not; stated
         * together, both apply.
         */
        Set<String> admitted(EffectiveConstraints.Constraint constraint) {
            Set<String> admitted =
                    constraint.valueClasses().isEmpty()
                            ? null
                            : hierarchy.instancesOf(constraint.valueClasses());
            if (constraint.valueTypes() != null) {
                var typed = hierarchy.typedAs(constraint.valueTypes());
                if (admitted == null) {
                    admitted = typed;
                } else {
                    admitted = new LinkedHashSet<>(admitted);
                    admitted.retainAll(typed);
                }
            }
            return admitted;
        }

        /** The classes a constraint names as its value type, for a message. */
        String valueType(EffectiveConstraints.Constraint constraint) {
            var named = new LinkedHashSet<>(constraint.valueClasses());
            if (constraint.valueTypes() != null) {
                named.addAll(constraint.valueTypes());
            }
            return classes(named);
        }
    }

    /**
     * How these two disagree, if they do.
     *
     * <p>One finding per property rather than one per clause: a property whose datatype and
     * cardinality both drifted is one thing that went wrong, and reporting it twice would make the
     * list longer without making it more useful.
     */
    private static Optional<ConformanceFinding> disagreement(
            EffectiveConstraints.Key key,
            EffectiveConstraints.Constraint schema,
            EffectiveConstraints.Constraint document,
            List<String> statedIn,
            Context context) {
        var contradictions = contradictions(schema, document, context);
        if (!contradictions.isEmpty()) {
            return Optional.of(
                    finding(
                            ConformanceFinding.Kind.CONTRADICTED,
                            key,
                            context.describe(schema),
                            context.describe(document),
                            String.join(" ", contradictions),
                            statedIn));
        }
        if (same(schema, document, context)) {
            return Optional.empty();
        }
        return Optional.of(
                finding(
                        ConformanceFinding.Kind.DIFFERENT,
                        key,
                        context.describe(schema),
                        context.describe(document),
                        "Both can be satisfied, but the schema and the document do not say the same"
                                + " thing.",
                        statedIn));
    }

    /**
     * A constraint the documents state only at {@code sh:Warning} or {@code sh:Info}.
     *
     * <p>Never a contradiction: data breaking it still conforms, so nothing has to be decided. But
     * it is not agreement either, because the schema's constraint is one data can fail.
     */
    private static ConformanceFinding advisoryOnly(
            EffectiveConstraints.Key key,
            EffectiveConstraints.Constraint schema,
            EffectiveConstraints.Constraint reported,
            EffectiveConstraints.Asserted documents,
            Context context) {
        var reasons = new ArrayList<String>();
        reasons.add(
                "The documents state this only at a severity below sh:Violation, so data breaking"
                        + " it still conforms.");
        reasons.addAll(contradictions(schema, reported, context));
        return finding(
                ConformanceFinding.Kind.DIFFERENT,
                key,
                context.describe(schema),
                context.describe(reported),
                String.join(" ", reasons),
                statedIn(documents, key));
    }

    /** Equal, with value types compared by the instances they admit. */
    private static boolean same(
            EffectiveConstraints.Constraint schema,
            EffectiveConstraints.Constraint document,
            Context context) {
        return Objects.equals(schema.minCount(), document.minCount())
                && Objects.equals(schema.maxCount(), document.maxCount())
                && schema.dataTypes().equals(document.dataTypes())
                && schema.nodeKinds().equals(document.nodeKinds())
                && Objects.equals(schema.allowedValues(), document.allowedValues())
                && Objects.equals(context.admitted(schema), context.admitted(document));
    }

    private static List<String> contradictions(
            EffectiveConstraints.Constraint schema,
            EffectiveConstraints.Constraint document,
            Context context) {
        var reasons = new ArrayList<String>();
        if (disjoint(schema.dataTypes(), document.dataTypes())) {
            reasons.add(
                    "A value cannot be both %s and %s."
                            .formatted(
                                    context.terms(schema.dataTypes()),
                                    context.terms(document.dataTypes())));
        }
        var schemaTypes = context.admitted(schema);
        var documentTypes = context.admitted(document);
        if (schemaTypes != null && documentTypes != null && disjoint(schemaTypes, documentTypes)) {
            reasons.add(
                    "A value cannot be an instance of both %s and %s."
                            .formatted(context.valueType(schema), context.valueType(document)));
        }
        if (disjoint(schema.nodeKinds(), document.nodeKinds())) {
            reasons.add(
                    "A value cannot be both %s and %s."
                            .formatted(
                                    context.terms(schema.nodeKinds()),
                                    context.terms(document.nodeKinds())));
        }
        var extra = notAllowedBySchema(schema.allowedValues(), document.allowedValues());
        if (!extra.isEmpty()) {
            reasons.add(
                    "The document allows %s, which the schema does not."
                            .formatted(EffectiveConstraints.values(extra, context.prefixes())));
        }
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

    /**
     * Values the document's {@code sh:in} lists and the schema's does not.
     *
     * <p>Reported as a contradiction although data using only shared values satisfies both: a value
     * the schema does not know is how an extended enumeration drifts from its schema, and it is the
     * one direction that needs a decision. Listing fewer values is merely stricter.
     */
    private static Set<Node> notAllowedBySchema(Set<Node> schema, Set<Node> document) {
        if (schema == null || document == null) {
            return Set.of();
        }
        var extra = new LinkedHashSet<>(document);
        extra.removeAll(schema);
        return extra;
    }

    /**
     * Two stated sets with nothing in common: both apply, so nothing can satisfy them.
     *
     * <p>A set nobody stated is not a disagreement — silence constrains nothing — so an empty side
     * is never disjoint from the other.
     */
    private static boolean disjoint(Set<String> left, Set<String> right) {
        if (left.isEmpty() || right.isEmpty()) {
            return false;
        }
        var shared = new LinkedHashSet<>(left);
        shared.retainAll(right);
        return shared.isEmpty();
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
}

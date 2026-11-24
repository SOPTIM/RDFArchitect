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
import org.rdfarchitect.services.shacl.effective.ConstraintFormatter;
import org.rdfarchitect.shacl.dto.ConformanceNotCompared;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Puts what the comparison left out of each document into the report's words. */
final class NotCompared {

    private static final String SH = "http://www.w3.org/ns/shacl#";

    private static final Set<String> COMBINATIONS = Set.of(SH + "or", SH + "xone", SH + "not");

    private NotCompared() {}

    /**
     * @param documents the documents' names in reading order
     * @param leftOut per document, constraints left out per namespace, from {@link
     *     OtherVocabularies}
     * @param unread per document, shapes not read per SHACL term, from {@code
     *     EffectiveConstraints.unread}
     */
    static List<ConformanceNotCompared> of(
            Collection<String> documents,
            Map<String, Map<String, Integer>> leftOut,
            Map<String, Map<String, Integer>> unread,
            PrefixMapping prefixes) {
        var report = new ArrayList<ConformanceNotCompared>();
        for (var document : documents) {
            var namespaces = leftOut.getOrDefault(document, Map.of());
            if (!namespaces.isEmpty()) {
                var count = sum(namespaces.values());
                report.add(
                        entry(
                                document,
                                ConformanceNotCompared.Reason.OTHER_VOCABULARY,
                                count,
                                namespaces.keySet(),
                                "%s about classes of a vocabulary this schema does not use: %s."
                                        .formatted(
                                                count == 1
                                                        ? "1 property constraint is"
                                                        : count + " property constraints are",
                                                String.join(", ", namespaces.keySet()))));
            }
            var terms = unread.getOrDefault(document, Map.of());
            var targets = new LinkedHashSet<String>();
            var combinations = new LinkedHashSet<String>();
            var targeting = 0;
            var combining = 0;
            for (var term : terms.entrySet()) {
                if (COMBINATIONS.contains(term.getKey())) {
                    combinations.add(term.getKey());
                    combining += term.getValue();
                } else {
                    targets.add(term.getKey());
                    targeting += term.getValue();
                }
            }
            if (targeting > 0) {
                report.add(
                        entry(
                                document,
                                ConformanceNotCompared.Reason.OTHER_TARGET,
                                targeting,
                                targets,
                                "%s what %s to with %s rather than by class."
                                        .formatted(
                                                shapes(targeting, "chooses", "choose"),
                                                targeting == 1 ? "it applies" : "they apply",
                                                or(targets, prefixes))));
            }
            if (combining > 0) {
                report.add(
                        entry(
                                document,
                                ConformanceNotCompared.Reason.COMBINATION,
                                combining,
                                combinations,
                                "%s rules with %s, which a comparison per class and property"
                                                .formatted(
                                                        shapes(combining, "combines", "combine"),
                                                        or(combinations, prefixes))
                                        + " cannot follow."));
            }
        }
        return List.copyOf(report);
    }

    private static ConformanceNotCompared entry(
            String document,
            ConformanceNotCompared.Reason reason,
            int count,
            Collection<String> terms,
            String message) {
        return ConformanceNotCompared.builder()
                .document(document)
                .reason(reason)
                .count(count)
                .terms(List.copyOf(terms))
                .message(message)
                .build();
    }

    private static String shapes(int count, String one, String many) {
        return count == 1 ? "1 shape " + one : count + " shapes " + many;
    }

    private static String or(Set<String> iris, PrefixMapping prefixes) {
        return ConstraintFormatter.terms(iris, prefixes).replace(" and ", " or ");
    }

    private static int sum(Collection<Integer> counts) {
        return counts.stream().mapToInt(Integer::intValue).sum();
    }
}

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

package org.rdfarchitect.services.shacl.effective;

import org.apache.jena.graph.Node;
import org.apache.jena.shared.PrefixMapping;
import org.rdfarchitect.services.shacl.effective.EffectiveConstraints.Constraint;

import java.util.ArrayList;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Puts what {@link EffectiveConstraints} reads into words.
 *
 * <p>Every view that shows a constraint formats it here, so that "0..1, xsd:float" reads the same
 * wherever it appears.
 */
public final class ConstraintFormatter {

    /**
     * The vocabularies a constraint is written in, for a workspace whose prefixes do not bind them.
     * Most do not: a schema declares {@code cim:}, not {@code sh:} or {@code xsd:}, and {@code
     * <http://www.w3.org/ns/shacl#IRI>} reads far worse than {@code sh:IRI}.
     */
    private static final PrefixMapping STANDARD =
            PrefixMapping.Factory.create()
                    .setNsPrefix("sh", "http://www.w3.org/ns/shacl#")
                    .setNsPrefix("xsd", "http://www.w3.org/2001/XMLSchema#")
                    .setNsPrefix("rdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns#")
                    .setNsPrefix("rdfs", "http://www.w3.org/2000/01/rdf-schema#")
                    .lock();

    private ConstraintFormatter() {}

    /**
     * A constraint in words, listing only what was actually stated. A value type is named by its
     * most general classes in {@code hierarchy}: a list of a range and every class deriving from it
     * reads as the range.
     */
    public static String describe(
            Constraint constraint, PrefixMapping prefixes, ClassHierarchy hierarchy) {
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
        // Every sh:class applies, while a list offers alternatives and reads as the most general of
        // them. A list saying nothing the classes don't already say is left out.
        if (!constraint.valueClasses().isEmpty()) {
            parts.add("of class " + terms(constraint.valueClasses(), prefixes));
        }
        if (constraint.valueTypes() != null) {
            var alternatives = hierarchy.roots(constraint.valueTypes());
            if (alternatives.isEmpty()) {
                parts.add("of class nothing");
            } else if (!constraint.valueClasses().containsAll(alternatives)) {
                parts.add("of class " + terms(alternatives, prefixes, " or "));
            }
        }
        if (!constraint.nodeKinds().isEmpty()) {
            parts.add(terms(constraint.nodeKinds(), prefixes));
        }
        if (constraint.allowedValues() != null) {
            parts.add("one of " + values(constraint.allowedValues(), prefixes));
        }
        return String.join(", ", parts);
    }

    /** The terms of a set, shortest form first, joined as prose. */
    public static String terms(Set<String> iris, PrefixMapping prefixes) {
        return terms(iris, prefixes, " and ");
    }

    private static String terms(Set<String> iris, PrefixMapping prefixes, String separator) {
        return iris.stream()
                .map(iri -> term(iri, prefixes))
                .sorted()
                .collect(Collectors.joining(separator));
    }

    /** The values of an {@code sh:in} list as a reader would write them, sorted. */
    public static String values(Set<Node> values, PrefixMapping prefixes) {
        if (values.isEmpty()) {
            return "nothing";
        }
        return values.stream()
                .map(value -> value.isURI() ? term(value.getURI(), prefixes) : value.toString())
                .sorted()
                .collect(Collectors.joining(", "));
    }

    private static String term(String iri, PrefixMapping prefixes) {
        var shortened = prefixes.shortForm(iri);
        if (shortened.equals(iri)) {
            shortened = STANDARD.shortForm(iri);
        }
        return shortened.equals(iri) ? "<" + iri + ">" : shortened;
    }
}

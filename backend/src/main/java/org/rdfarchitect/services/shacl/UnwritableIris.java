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
import org.apache.jena.graph.Node;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * IRIs that Jena's parsers read with no more than a warning, but that no SPARQL update can carry.
 *
 * <p>Turtle and SPARQL forbid the same characters in an IRI. Jena still reads {@code <a|b>}, or
 * {@code \} escapes that decode to one of those characters, and only warns. A document holding such
 * an IRI could be saved, and every later persist of its workspace would then send an update the
 * store refuses. So these IRIs are refused where a document's text is read.
 */
final class UnwritableIris {

    /** What an IRI cannot hold in Turtle or SPARQL: controls, space and {@code <>"{}|^`\}. */
    private static final Pattern NOT_IN_AN_IRI = Pattern.compile("[\\x00-\\x20<>\"{}|^`\\\\]");

    /** What the parser warned about, where. */
    record Warning(String message, long line, long column) {}

    /**
     * An IRI that cannot be written, with the place a warning gave for it.
     *
     * @param line 1-based, or -1 when no warning placed it
     * @param column 1-based, or -1 when no warning placed it
     */
    record Found(String iri, long line, long column) {

        /** Says which character is the problem and how to write it instead. */
        String describe() {
            var character = iri.codePoints().filter(UnwritableIris::notInAnIri).findFirst();
            var code = character.orElseThrow();
            var shown =
                    code <= ' ' ? "U+%04X".formatted(code) : "'" + Character.toString(code) + "'";
            return "The IRI <%s> contains %s, which an IRI cannot hold. Write it percent-encoded, as %%%02X."
                    .formatted(escaped(iri), shown, code);
        }

        /** {@link #describe()}, preceded by the place in the parser's own manner. */
        String describeWithPlace() {
            return line > 0
                    ? "[line: %d, col: %d] %s".formatted(line, column, describe())
                    : describe();
        }

        private static String escaped(String iri) {
            var out = new StringBuilder();
            iri.codePoints()
                    .forEach(
                            code -> {
                                if (code < ' ') {
                                    out.append("\\u%04X".formatted(code));
                                } else {
                                    out.appendCodePoint(code);
                                }
                            });
            return out.toString();
        }
    }

    private UnwritableIris() {}

    /**
     * Finds every IRI in {@code graph} that a SPARQL update cannot carry: subjects, predicates,
     * objects and the datatypes of literals.
     *
     * @param warnings what the parser warned about, in the order it did, which places each IRI at
     *     the first warning that names it
     * @return the IRIs found, those a warning placed first and in text order
     */
    static List<Found> in(Graph graph, List<Warning> warnings) {
        var iris = new LinkedHashSet<String>();
        graph.find().forEachRemaining(triple -> collect(iris, triple));
        if (iris.isEmpty()) {
            return List.of();
        }
        var found = new ArrayList<Found>();
        for (var warning : warnings) {
            for (var iterator = iris.iterator(); iterator.hasNext(); ) {
                var iri = iterator.next();
                if (warning.message().contains(iri)) {
                    found.add(new Found(iri, warning.line(), warning.column()));
                    iterator.remove();
                }
            }
        }
        iris.forEach(iri -> found.add(new Found(iri, -1, -1)));
        return found;
    }

    private static void collect(Set<String> iris, org.apache.jena.graph.Triple triple) {
        collect(iris, triple.getSubject());
        collect(iris, triple.getPredicate());
        collect(iris, triple.getObject());
    }

    private static void collect(Set<String> iris, Node node) {
        if (node.isTripleTerm()) {
            collect(iris, node.getTriple());
            return;
        }
        var iri =
                node.isURI()
                        ? node.getURI()
                        : node.isLiteral() ? node.getLiteralDatatypeURI() : null;
        if (iri != null && NOT_IN_AN_IRI.matcher(iri).find()) {
            iris.add(iri);
        }
    }

    static boolean notInAnIri(int code) {
        return NOT_IN_AN_IRI.matcher(Character.toString(code)).matches();
    }
}

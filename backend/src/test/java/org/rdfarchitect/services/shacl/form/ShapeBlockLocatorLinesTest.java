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

package org.rdfarchitect.services.shacl.form;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.jena.shared.impl.PrefixMappingImpl;
import org.junit.jupiter.api.Test;

/**
 * The one-pass line index behind "which line does this shape start on".
 *
 * <p>It replaces a per-subject call to {@link ShapeBlockLocator#locate}, which rescanned the whole
 * document every time — quadratic on an official constraints file. The two must agree exactly, or
 * the class dialog would link to a different line than the one the workbench opens.
 */
class ShapeBlockLocatorLinesTest {

    private static final String TURTLE =
            """
            @prefix sh:  <http://www.w3.org/ns/shacl#> .
            @prefix ex:  <http://example.org/shapes#> .

            # A comment before the first shape.
            ex:First  a sh:NodeShape ;
                      sh:select \"""
                          SELECT ?this WHERE { ?this a ?type }
                      \""" .

            ex:Second a sh:NodeShape .
            """;

    private static PrefixMappingImpl prefixes() {
        var prefixes = new PrefixMappingImpl();
        prefixes.setNsPrefix("sh", "http://www.w3.org/ns/shacl#");
        prefixes.setNsPrefix("ex", "http://example.org/shapes#");
        return prefixes;
    }

    @Test
    void reportsTheLineEachSubjectStartsOn() {
        var lines = ShapeBlockLocator.linesBySubject(TURTLE, prefixes());

        assertThat(lines).containsEntry("http://example.org/shapes#First", 5);
        // Counted past the newlines inside the triple-quoted literal, which is where the
        // embedded SPARQL of a real constraints file lives.
        assertThat(lines).containsEntry("http://example.org/shapes#Second", 10);
    }

    @Test
    void agreesWithLocateForEverySubject() {
        var prefixes = prefixes();
        var lines = ShapeBlockLocator.linesBySubject(TURTLE, prefixes);

        lines.forEach(
                (iri, line) -> {
                    var located = ShapeBlockLocator.locate(TURTLE, iri, prefixes).orElseThrow();
                    var expected = (int) TURTLE.substring(0, located.start()).lines().count() + 1;
                    assertThat(line).as(iri).isEqualTo(expected);
                });
    }

    @Test
    void hasNothingToSayAboutTextThatIsNotThere() {
        assertThat(ShapeBlockLocator.linesBySubject(null, prefixes())).isEmpty();
        assertThat(ShapeBlockLocator.linesBySubject("", prefixes())).isEmpty();
    }
}

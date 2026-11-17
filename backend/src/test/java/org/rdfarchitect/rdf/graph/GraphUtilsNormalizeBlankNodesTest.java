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

package org.rdfarchitect.rdf.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.vocabulary.RDF;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.time.Duration;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

class GraphUtilsNormalizeBlankNodesTest {

    private static final String PREFIXES =
            "@prefix sh: <http://www.w3.org/ns/shacl#> .\n@prefix ex: <http://example.org/> .\n";

    private static final String SHAPES =
            PREFIXES
                    + "ex:LineShape a sh:NodeShape ; sh:targetClass ex:Line ;\n"
                    + "  sh:property [ sh:path ex:length ; sh:minCount 1 ; sh:maxCount 1 ] ,\n"
                    + "              [ sh:path ex:kind ; sh:in ( \"a\" \"b\" ) ] .\n";

    @Test
    void reparsingTheSameTextGivesTheSameLabels() {
        assertEquals(blankLabels(normalized(SHAPES)), blankLabels(normalized(SHAPES)));
    }

    @Test
    void labelsAreIsomorphicToTheInput() {
        var input = parse(SHAPES);

        assertTrue(normalized(SHAPES).isIsomorphicWith(input));
    }

    /**
     * The labels are compared across versions of a graph, so the algorithm computing them must not
     * drift: these are the labels it has always produced for this document.
     */
    @Test
    void labelsStayTheOnesEarlierVersionsProduced() {
        assertEquals(
                Set.of(
                        "36f3a06e6186dbaeee27f10a4da8251e",
                        "8eaacecb44e3664ff91aa082d718b2ff",
                        "9e3530bceb1e5ee8bb88773a2a771f7a",
                        "a7021e690e1e69ed380a8377ef701fd1"),
                blankLabels(normalized(SHAPES)));
    }

    /**
     * An official constraints file holds tens of thousands of blank nodes. Fingerprinting each by
     * scanning every triple made a save of one take minutes.
     */
    @Test
    void normalisesManyBlankNodesQuickly() {
        var text = new StringBuilder(PREFIXES);
        for (int i = 0; i < 20_000; i++) {
            text.append("ex:S")
                    .append(i)
                    .append(" sh:property [ sh:path ex:p")
                    .append(i)
                    .append(" ; sh:in ( \"a\" \"b\" ) ] .\n");
        }
        var graph = parse(text.toString());

        var result =
                assertTimeoutPreemptively(
                        Duration.ofSeconds(20), () -> GraphUtils.normalizeBlankNodes(graph));

        assertEquals(graph.size(), result.size());
    }

    /**
     * Ten passes see ten hops. Two lists differing only further along than that used to share the
     * cells in the middle, and a shared cell has two ways on: a reader then took the wrong one.
     */
    @Test
    void longListsDifferingFarAwayStayTwoLists() {
        var shared =
                IntStream.range(0, 40).mapToObj(i -> "ex:v" + i).collect(Collectors.joining(" "));
        var text =
                PREFIXES
                        + "ex:A sh:path ex:p ; sh:in ( "
                        + shared
                        + " ex:endA ) .\n"
                        + "ex:B sh:path ex:q ; sh:in ( "
                        + shared
                        + " ex:endB ) .\n";

        var result = normalized(text);

        assertEquals(0, forkingCells(result));
        assertEquals(parse(text).size(), result.size());
        assertTrue(result.isIsomorphicWith(parse(text)));
    }

    /** A blank node that is the same all the way out is still one node, as it always was. */
    @Test
    void trueDuplicatesStillBecomeOne() {
        var text =
                PREFIXES
                        + "ex:S sh:property [ sh:path ex:p ; sh:minCount 1 ] ,\n"
                        + "                 [ sh:path ex:p ; sh:minCount 1 ] .\n";

        var result = normalized(text);

        assertEquals(1, blankLabels(result).size());
        assertEquals(3, result.size());
    }

    /** A far-away edit must not rename what it did not touch: diffs are taken over these labels. */
    @Test
    void labelsOutsideALookalikeDoNotDependOnOne() {
        var alone = normalized(SHAPES);
        var shared =
                IntStream.range(0, 40).mapToObj(i -> "ex:v" + i).collect(Collectors.joining(" "));
        var text =
                SHAPES
                        + "ex:A sh:path ex:p ; sh:in ( "
                        + shared
                        + " ex:endA ) .\n"
                        + "ex:B sh:path ex:q ; sh:in ( "
                        + shared
                        + " ex:endB ) .\n";

        assertTrue(blankLabels(normalized(text)).containsAll(blankLabels(alone)));
    }

    /**
     * Refining splits a list of identical values two cells a pass, so a long enough one could take
     * longer than anybody waits. It has to end anyway, and still as a list.
     */
    /** Nor may it rename true duplicates, which share a label just as lookalikes do. */
    @Test
    void duplicatesKeepTheirLabelWhenLookalikesElsewhereSplit() {
        var duplicates =
                PREFIXES
                        + "ex:S sh:property [ sh:path ex:p ; sh:minCount 1 ] ,\n"
                        + "                 [ sh:path ex:p ; sh:minCount 1 ] .\n";
        var shared =
                IntStream.range(0, 40).mapToObj(i -> "ex:v" + i).collect(Collectors.joining(" "));
        var text =
                duplicates
                        + "ex:A sh:path ex:p ; sh:in ( "
                        + shared
                        + " ex:endA ) .\n"
                        + "ex:B sh:path ex:p ; sh:in ( "
                        + shared
                        + " ex:endB ) .\n";

        assertTrue(blankLabels(normalized(text)).containsAll(blankLabels(normalized(duplicates))));
    }

    @Test
    void aHugeListOfTheSameValueEndsAndStaysAList() {
        var text = PREFIXES + "ex:A sh:path ex:p ; sh:in ( " + "ex:same ".repeat(20_000) + ") .\n";
        var graph = parse(text);

        var result =
                assertTimeoutPreemptively(
                        Duration.ofSeconds(30), () -> GraphUtils.normalizeBlankNodes(graph));

        assertEquals(0, forkingCells(result));
        assertEquals(graph.size(), result.size());
    }

    private static long forkingCells(Graph graph) {
        return Stream.of(RDF.first.asNode(), RDF.rest.asNode())
                .flatMap(
                        predicate ->
                                graph.stream(Node.ANY, predicate, Node.ANY)
                                        .collect(
                                                Collectors.groupingBy(
                                                        Triple::getSubject, Collectors.counting()))
                                        .values()
                                        .stream())
                .filter(count -> count > 1)
                .count();
    }

    private static Graph normalized(String turtle) {
        return GraphUtils.normalizeBlankNodes(parse(turtle));
    }

    private static Graph parse(String turtle) {
        var model = ModelFactory.createDefaultModel();
        model.read(new StringReader(turtle), null, "TURTLE");
        return model.getGraph();
    }

    private static Set<String> blankLabels(Graph graph) {
        var labels = new TreeSet<String>();
        graph.find()
                .forEach(
                        triple -> {
                            for (Node node : new Node[] {triple.getSubject(), triple.getObject()}) {
                                if (node.isBlank()) {
                                    labels.add(node.getBlankNodeLabel());
                                }
                            }
                        });
        return labels;
    }
}

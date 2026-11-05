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
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.vocabulary.RDF;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * Every {@code sh:in} list of every official constraints file has to read the same afterwards.
 *
 * <p>The official lists are long and overlap a lot: which classes may stand somewhere is often
 * nearly the same list as somewhere else. That is what ten passes of refinement could not tell
 * apart, in current releases as well as past ones.
 */
class GraphUtilsNormalizeAgainstEntsoeProfilesTest {

    private static final Path LIBRARY = Path.of("../external/entsoe-application-profiles-library");
    private static final Node IN = NodeFactory.createURI("http://www.w3.org/ns/shacl#in");

    @Test
    void everyNamedListReadsTheSameAfterNormalising() throws IOException {
        List<Path> files;
        try (var walk = Files.walk(LIBRARY)) {
            files =
                    walk.filter(path -> path.toString().contains("SHACL"))
                            .filter(path -> path.toString().endsWith(".ttl"))
                            .toList();
        }
        assertFalse(files.isEmpty());

        var changed = new ArrayList<String>();
        for (var file : files) {
            var parsed = RDFParser.source(file).lang(Lang.TURTLE).toGraph();
            var normalized = GraphUtils.normalizeBlankNodes(parsed);
            parsed.stream(Node.ANY, IN, Node.ANY)
                    .filter(triple -> triple.getSubject().isURI())
                    .forEach(
                            triple -> {
                                var after =
                                        normalized.stream(triple.getSubject(), IN, Node.ANY)
                                                .map(
                                                        found ->
                                                                members(
                                                                        normalized,
                                                                        found.getObject()))
                                                .toList();
                                if (!after.equals(List.of(members(parsed, triple.getObject())))) {
                                    changed.add(file.getFileName() + " " + triple.getSubject());
                                }
                            });
        }

        assertEquals(List.of(), changed);
    }

    /** The members in order, or nothing at all once the list forks or never ends. */
    private static List<Node> members(Graph graph, Node head) {
        var members = new ArrayList<Node>();
        var visited = new HashSet<Node>();
        var cell = head;
        while (!RDF.nil.asNode().equals(cell)) {
            var firsts = graph.stream(cell, RDF.first.asNode(), Node.ANY).toList();
            var rests = graph.stream(cell, RDF.rest.asNode(), Node.ANY).toList();
            if (!visited.add(cell) || firsts.size() != 1 || rests.size() != 1) {
                return List.of();
            }
            members.add(firsts.getFirst().getObject());
            cell = rests.getFirst().getObject();
        }
        return members;
    }
}

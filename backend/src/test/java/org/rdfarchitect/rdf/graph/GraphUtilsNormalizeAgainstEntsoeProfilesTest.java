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
import static org.junit.jupiter.api.Assertions.assertNull;

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.vocabulary.RDF;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * Normalising has to leave every official constraints file as it is, apart from merging what the
 * file writes twice.
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
        var changed = new ArrayList<String>();
        for (var file : constraintFiles()) {
            var parsed = read(file);
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

    /** A label that differs between two reads of one file shows up as a change nobody made. */
    @Test
    void everyFileGetsTheSameLabelsOnEveryRead() throws IOException {
        var differing = new ArrayList<String>();
        for (var file : constraintFiles()) {
            var first = GraphUtils.normalizeBlankNodes(read(file)).find().toSet();
            var second = GraphUtils.normalizeBlankNodes(read(file)).find().toSet();
            if (!first.equals(second)) {
                differing.add(file.getFileName().toString());
            }
        }

        assertEquals(List.of(), differing);
    }

    /**
     * What a file writes twice becomes one, and nothing else changes: a file that writes nothing
     * twice comes out with the same triples, up to the labels of its blank nodes.
     */
    @Test
    void onlyWhatAFileWritesTwiceIsMerged() throws IOException {
        var wrong = new ArrayList<String>();
        for (var file : constraintFiles()) {
            var parsed = read(file);
            var normalized = GraphUtils.normalizeBlankNodes(parsed);
            var expected = sizeWithDuplicatesMerged(parsed);
            if (normalized.size() != expected) {
                wrong.add(
                        file.getFileName() + ": " + normalized.size() + " instead of " + expected);
            } else if (expected == parsed.size() && !normalized.isIsomorphicWith(parsed)) {
                wrong.add(file.getFileName() + ": not the same graph");
            }
        }

        assertEquals(List.of(), wrong);
    }

    /** Its lists overlap for long stretches, and it writes nothing twice. */
    @Test
    void theCurrentEquipmentConstraintsKeepEveryTriple() {
        var parsed =
                read(
                        LIBRARY.resolve(
                                "CGMES/CurrentRelease/SHACL/TTL/"
                                        + "61970-600-2_Equipment-AP-Con-Simple-SHACL.ttl"));

        assertEquals(parsed.size(), GraphUtils.normalizeBlankNodes(parsed).size());
    }

    private static List<Path> constraintFiles() throws IOException {
        List<Path> files;
        try (var walk = Files.walk(LIBRARY)) {
            files =
                    walk.filter(path -> path.toString().contains("SHACL"))
                            .filter(path -> path.toString().endsWith(".ttl"))
                            .sorted()
                            .toList();
        }
        assertFalse(files.isEmpty());
        return files;
    }

    private static Graph read(Path file) {
        return RDFParser.source(file).lang(Lang.TURTLE).toGraph();
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

    /**
     * How many triples {@code graph} has once its duplicate blank nodes are merged, worked out
     * without the code under test.
     *
     * <p>In the official files every blank node is the object of exactly one triple, or of none, so
     * they form trees. Two blank nodes are then duplicates exactly when they hang off the same
     * place and have the same tree below them, and each can be named by those two.
     */
    private static int sizeWithDuplicatesMerged(Graph graph) {
        var parents = new HashMap<Node, Triple>();
        var children = new HashMap<Node, List<Triple>>();
        for (var triple : graph.find().toList()) {
            if (triple.getObject().isBlank()) {
                var earlier = parents.put(triple.getObject(), triple);
                assertNull(earlier, "a blank node with two parents: " + triple);
            }
            if (triple.getSubject().isBlank()) {
                children.computeIfAbsent(triple.getSubject(), ignored -> new ArrayList<>())
                        .add(triple);
            }
        }
        var names = new TreeNames(parents, children);
        var merged = new HashSet<List<String>>();
        graph.find()
                .forEach(
                        triple ->
                                merged.add(
                                        List.of(
                                                names.of(triple.getSubject()),
                                                triple.getPredicate().toString(),
                                                names.of(triple.getObject()))));
        return merged.size();
    }

    /** Names a blank node by where it hangs and by the tree below it. */
    private static final class TreeNames {

        private final Map<Node, Triple> parents;
        private final Map<Node, List<Triple>> children;
        private final Map<Node, String> trees = new HashMap<>();
        private final Map<Node, String> names = new HashMap<>();

        TreeNames(Map<Node, Triple> parents, Map<Node, List<Triple>> children) {
            this.parents = parents;
            this.children = children;
        }

        String of(Node node) {
            if (!node.isBlank()) {
                return node.toString();
            }
            var name = names.get(node);
            if (name == null) {
                var parent = parents.get(node);
                var place =
                        parent == null
                                ? "root"
                                : of(parent.getSubject()) + " " + parent.getPredicate();
                name = digest(place + " / " + tree(node));
                names.put(node, name);
            }
            return name;
        }

        private String tree(Node node) {
            var tree = trees.get(node);
            if (tree == null) {
                var lines = new ArrayList<String>();
                for (var triple : children.getOrDefault(node, List.of())) {
                    var object = triple.getObject();
                    lines.add(
                            triple.getPredicate()
                                    + " "
                                    + (object.isBlank() ? tree(object) : object.toString()));
                }
                lines.sort(null);
                tree = digest(String.join("\n", lines));
                trees.put(node, tree);
            }
            return tree;
        }
    }

    private static String digest(String text) {
        try {
            var bytes =
                    MessageDigest.getInstance("SHA-256")
                            .digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

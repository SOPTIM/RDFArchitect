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

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.sparql.graph.GraphFactory;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.rdfarchitect.models.cim.rdf.resources.RDFA;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Utility class for graph operations */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class GraphUtils {

    private static final Set<String> RELEVANT_TYPES =
            Set.of(RDF.Property.toString(), RDFS.Class.toString());

    public static Graph deepCopy(Graph graph) {
        var newGraph = GraphFactory.createDefaultGraph();
        var iterator = graph.find();
        while (iterator.hasNext()) {
            newGraph.add(iterator.next());
        }
        return newGraph;
    }

    public static Graph enhanceWithUUIDs(Graph graph) {
        var model = ModelFactory.createModelForGraph(graph);
        addUUIDsToTypedResources(model);
        addUUIDsToReferencedOnlyResources(model);
        return graph;
    }

    public static void removeUUIDs(Graph graph) {
        graph.find(Node.ANY, RDFA.uuid.asNode(), Node.ANY).toList().forEach(graph::delete);
    }

    private static void addUUIDsToTypedResources(Model model) {
        var subjects =
                model.listResourcesWithProperty(RDF.type)
                        .filterKeep(r -> r.isURIResource() && !r.hasProperty(RDFA.uuid))
                        .toSet();
        for (var subject : subjects) {
            subject.addProperty(RDFA.uuid, UUID.randomUUID().toString());
        }
    }

    private static void addUUIDsToReferencedOnlyResources(Model model) {
        var objects = new HashSet<Resource>();
        model.listResourcesWithProperty(RDF.type)
                .filterKeep(r -> r.isURIResource() && hasAnyType(r))
                .forEachRemaining(
                        subject ->
                                subject.listProperties()
                                        .mapWith(Statement::getObject)
                                        .filterKeep(GraphUtils::isReferencedOnlyURI)
                                        .mapWith(RDFNode::asResource)
                                        .forEachRemaining(objects::add));
        objects.forEach(o -> o.addProperty(RDFA.uuid, UUID.randomUUID().toString()));
    }

    private static boolean hasAnyType(Resource resource) {
        return resource.listProperties(RDF.type)
                .mapWith(Statement::getObject)
                .filterKeep(o -> RELEVANT_TYPES.contains(o.asResource().getURI()))
                .hasNext();
    }

    private static boolean isReferencedOnlyURI(RDFNode node) {
        return node.isURIResource()
                && !RELEVANT_TYPES.contains(node.asResource().getURI())
                && !node.asResource().hasProperty(RDFA.uuid)
                && !node.asResource().listProperties().hasNext();
    }

    /**
     * Returns a copy of {@code graph} where every blank node is relabelled with a deterministic,
     * content-addressed ID derived from hashing its structural neighbourhood. Re-parsing the same
     * RDF content produces the same blank-node labels, so diffing two versions of the graph yields
     * only real semantic changes rather than spurious blank-node renames.
     */
    public static Graph normalizeBlankNodes(Graph graph) {
        var triples = graph.find().toList();
        var blankNodes =
                triples.stream()
                        .flatMap(t -> Stream.of(t.getSubject(), t.getObject()))
                        .filter(Node::isBlank)
                        .collect(Collectors.toSet());

        var result = GraphFactory.createDefaultGraph();
        if (blankNodes.isEmpty()) {
            triples.forEach(result::add);
        } else {
            var hashes = computeBlankNodeHashes(triples, blankNodes);
            for (var t : triples) {
                result.add(
                        Triple.create(
                                remapBlank(t.getSubject(), hashes),
                                t.getPredicate(),
                                remapBlank(t.getObject(), hashes)));
            }
        }
        result.getPrefixMapping().setNsPrefixes(graph.getPrefixMapping());
        return result;
    }

    private static Node remapBlank(Node n, Map<Node, String> hashes) {
        return n.isBlank() ? NodeFactory.createBlankNode(hashes.get(n)) : n;
    }

    private static Map<Node, String> computeBlankNodeHashes(
            List<Triple> triples, Set<Node> blankNodes) {
        Map<Node, String> hashes = new HashMap<>();
        blankNodes.forEach(bn -> hashes.put(bn, ""));

        for (int pass = 0; pass < 10; pass++) {
            var next = new HashMap<Node, String>();
            var changed = false;
            for (var bn : blankNodes) {
                var h = blankNodeFingerprint(bn, triples, hashes);
                next.put(bn, h);
                if (!h.equals(hashes.get(bn))) {
                    changed = true;
                }
            }
            hashes.putAll(next);
            if (!changed) {
                break;
            }
        }
        return hashes;
    }

    private static String blankNodeFingerprint(
            Node bn, List<Triple> triples, Map<Node, String> hashes) {
        var lines = new ArrayList<String>();
        for (var t : triples) {
            if (t.getSubject().equals(bn)) {
                lines.add("o:" + t.getPredicate() + "=" + nodeLabel(t.getObject(), hashes));
            }
            if (t.getObject().equals(bn)) {
                lines.add("s:" + t.getPredicate() + "=" + nodeLabel(t.getSubject(), hashes));
            }
        }
        Collections.sort(lines);
        return sha256prefix(String.join("|", lines));
    }

    private static String nodeLabel(Node n, Map<Node, String> hashes) {
        return n.isBlank() ? ("B:" + hashes.getOrDefault(n, "")) : n.toString();
    }

    private static String sha256prefix(String input) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            var bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            var sb = new StringBuilder(32);
            for (int i = 0; i < 16; i++) {
                sb.append(String.format("%02x", bytes[i]));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

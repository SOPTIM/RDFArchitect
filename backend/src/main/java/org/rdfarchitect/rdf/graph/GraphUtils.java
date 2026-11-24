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
import org.apache.jena.rdf.model.Property;
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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Utility class for graph operations */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class GraphUtils {

    /**
     * Upper bound on the edges {@link #separateLookalikes} may fingerprint, so that a crafted file
     * cannot keep it busy for long.
     */
    private static final long REFINEMENT_BUDGET = 5_000_000L;

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

    /**
     * Copies {@code subject}'s statements from {@code source} to {@code target}, together with the
     * statements of every blank node they lead to, however deeply nested. A named object is
     * followed only when the statement's predicate is in {@code alsoFollow}.
     *
     * <p>Walks with a work list and a visited set, so that a cyclic list ends and a deeply nested
     * structure cannot overflow the stack. Both can come from a document a user uploads.
     */
    public static void copyWithBlankNodes(
            Model source, Model target, Resource subject, Set<Property> alsoFollow) {
        var visited = new HashSet<RDFNode>();
        var pending = new ArrayDeque<Resource>();
        pending.push(subject);
        visited.add(subject);
        while (!pending.isEmpty()) {
            var statements = source.listStatements(pending.pop(), null, (RDFNode) null);
            while (statements.hasNext()) {
                var statement = statements.nextStatement();
                target.add(statement);
                var object = statement.getObject();
                var follow =
                        object.isAnon()
                                || (object.isResource()
                                        && alsoFollow.contains(statement.getPredicate()));
                if (follow && visited.add(object)) {
                    pending.push(object.asResource());
                }
            }
        }
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
        return normalizeBlankNodes(graph, REFINEMENT_BUDGET);
    }

    /** {@link #normalizeBlankNodes(Graph)} with another {@link #REFINEMENT_BUDGET}. */
    static Graph normalizeBlankNodes(Graph graph, long refinementBudget) {
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
            var hashes = computeBlankNodeHashes(triples, blankNodes, refinementBudget);
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

    /**
     * Labels every blank node with a hash of its edges, refined from its neighbours' labels for up
     * to ten passes, so that a node's label covers what lies within ten hops of it. Nodes that
     * still share a label but differ further away are then told apart by {@link
     * #separateLookalikes}.
     *
     * <p>Each node's edges are collected once, so a pass takes time linear in the number of
     * triples.
     */
    private static Map<Node, String> computeBlankNodeHashes(
            List<Triple> triples, Set<Node> blankNodes, long refinementBudget) {
        var edges = new HashMap<Node, List<Edge>>();
        for (var t : triples) {
            if (t.getSubject().isBlank()) {
                edges.computeIfAbsent(t.getSubject(), ignored -> new ArrayList<>())
                        .add(new Edge("o:", t.getPredicate(), t.getObject()));
            }
            if (t.getObject().isBlank()) {
                edges.computeIfAbsent(t.getObject(), ignored -> new ArrayList<>())
                        .add(new Edge("s:", t.getPredicate(), t.getSubject()));
            }
        }

        Map<Node, String> hashes = new HashMap<>();
        blankNodes.forEach(bn -> hashes.put(bn, ""));

        for (int pass = 0; pass < 10; pass++) {
            var next = new HashMap<Node, String>();
            var changed = false;
            for (var bn : blankNodes) {
                var h = blankNodeFingerprint(edges.getOrDefault(bn, List.of()), hashes);
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
        separateLookalikes(edges, hashes, refinementBudget);
        return hashes;
    }

    /**
     * Gives distinct labels to blank nodes that share a label but differ more than ten hops away.
     *
     * <p>Blank nodes with the same label are merged into one, which is only correct if they are
     * identical all the way out. Two long {@code rdf:List}s that differ only after their tenth cell
     * would otherwise share their middle cells.
     *
     * <p>Nodes sharing a label are refined until no group splits any further. When a group splits,
     * its largest part keeps the group's label and the other parts take new ones. A label therefore
     * changes only where a group splits: true duplicates, and every node outside a look-alike
     * group, keep theirs however long refining takes elsewhere in the graph.
     *
     * <p>A node is fingerprinted again only after its own label or a neighbour's has changed. The
     * other members of its group still share the signature the group had when they were last
     * fingerprinted, so a long list of identical values, which splits two cells a pass, costs a few
     * fingerprints a pass rather than one for every cell. If {@code budget} edges have been
     * fingerprinted and groups are still splitting, the remaining groups are kept apart by {@link
     * #keepApart}.
     */
    private static void separateLookalikes(
            Map<Node, List<Edge>> edges, Map<Node, String> labels, long budget) {
        new Lookalikes(edges, labels).separate(budget);
    }

    /** The groups of blank nodes still sharing a label while {@link #separateLookalikes} runs. */
    private static final class Lookalikes {

        private final Map<Node, List<Edge>> edges;
        private final Map<Node, String> labels;

        /** Every label carried by more than one node, with those nodes. */
        private final Map<String, Set<Node>> groups = new HashMap<>();

        /** Per group, the signature shared by those of its members that were not due in a pass. */
        private final Map<String, String> settled = new HashMap<>();

        Lookalikes(Map<Node, List<Edge>> edges, Map<Node, String> labels) {
            this.edges = edges;
            this.labels = labels;
            labels.forEach(
                    (node, label) ->
                            groups.computeIfAbsent(label, ignored -> new HashSet<>()).add(node));
            groups.values().removeIf(group -> group.size() < 2);
        }

        void separate(long budget) {
            Set<Node> due = new HashSet<>();
            groups.values().forEach(due::addAll);
            var left = budget;
            while (!due.isEmpty()) {
                var signatures = new HashMap<Node, String>();
                for (var node : due) {
                    var nodeEdges = edges.getOrDefault(node, List.of());
                    // The label goes in as well, so that a group can only ever split further.
                    signatures.put(
                            node,
                            sha256prefix(
                                    labels.get(node)
                                            + "|"
                                            + blankNodeFingerprint(nodeEdges, labels)));
                    left -= nodeEdges.size() + 1L;
                }
                var relabelled = new ArrayList<Node>();
                due.stream()
                        .collect(Collectors.groupingBy(labels::get))
                        .forEach((label, members) -> split(label, members, signatures, relabelled));
                if (left <= 0) {
                    keepApart(groups.values(), labels);
                    return;
                }
                due = nextDue(relabelled);
            }
        }

        /**
         * Splits the group labelled {@code label} by the signatures of {@code dueMembers}; its
         * other members share the signature in {@link #settled}.
         *
         * <p>Every part but the largest takes its signature as label, and a part of more than one
         * node becomes a group of its own. Of equally large parts, the one with the smallest
         * signature keeps the label.
         */
        private void split(
                String label,
                List<Node> dueMembers,
                Map<Node, String> signatures,
                List<Node> relabelled) {
            var group = groups.get(label);
            var parts = new HashMap<String, List<Node>>();
            dueMembers.forEach(
                    node ->
                            parts.computeIfAbsent(
                                            signatures.get(node), ignored -> new ArrayList<>())
                                    .add(node));
            var sizes = new HashMap<String, Integer>();
            parts.forEach((signature, members) -> sizes.put(signature, members.size()));
            var notDue = group.size() - dueMembers.size();
            if (notDue > 0) {
                sizes.merge(settled.get(label), notDue, Integer::sum);
            }
            var keeper =
                    Collections.max(
                                    sizes.entrySet(),
                                    Map.Entry.<String, Integer>comparingByValue()
                                            .thenComparing(
                                                    Map.Entry.comparingByKey(
                                                            Comparator.reverseOrder())))
                            .getKey();
            if (sizes.size() > 1) {
                var due = new HashSet<>(dueMembers);
                for (var signature : sizes.keySet()) {
                    if (signature.equals(keeper)) {
                        continue;
                    }
                    var members = parts.getOrDefault(signature, new ArrayList<>());
                    if (notDue > 0 && signature.equals(settled.get(label))) {
                        group.stream().filter(node -> !due.contains(node)).forEach(members::add);
                    }
                    for (var node : members) {
                        labels.put(node, signature);
                        group.remove(node);
                        relabelled.add(node);
                    }
                    if (members.size() > 1) {
                        groups.put(signature, new HashSet<>(members));
                    }
                }
            }
            if (group.size() > 1) {
                settled.put(label, keeper);
            } else {
                groups.remove(label);
                settled.remove(label);
            }
        }

        /** The nodes still sharing a label whose own label or a neighbour's has just changed. */
        private Set<Node> nextDue(List<Node> relabelled) {
            var due = new HashSet<Node>();
            for (var node : relabelled) {
                if (groups.containsKey(labels.get(node))) {
                    due.add(node);
                }
                for (var edge : edges.getOrDefault(node, List.of())) {
                    if (edge.other().isBlank() && groups.containsKey(labels.get(edge.other()))) {
                        due.add(edge.other());
                    }
                }
            }
            return due;
        }
    }

    /**
     * Gives up on telling {@code groups} apart by content, and does not merge them.
     *
     * <p>Only something built to be pathological gets here, and a label that differs between two
     * parses of it costs a noisier diff, where a wrong merge would change what it says.
     */
    private static void keepApart(Collection<Set<Node>> groups, Map<Node, String> labels) {
        for (var group : groups) {
            var index = 0;
            for (var node : group) {
                if (index > 0) {
                    labels.put(node, sha256prefix(labels.get(node) + "|" + index));
                }
                index++;
            }
        }
    }

    /** One triple seen from a blank node: which end the node is on, and what is at the other. */
    private record Edge(String direction, Node predicate, Node other) {}

    private static String blankNodeFingerprint(List<Edge> edges, Map<Node, String> hashes) {
        var lines = new ArrayList<String>(edges.size());
        for (var edge : edges) {
            lines.add(edge.direction() + edge.predicate() + "=" + nodeLabel(edge.other(), hashes));
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
            return HexFormat.of().formatHex(bytes, 0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

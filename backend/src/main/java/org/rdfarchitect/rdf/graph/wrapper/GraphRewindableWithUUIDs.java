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

package org.rdfarchitect.rdf.graph.wrapper;

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.vocabulary.RDF;
import org.jetbrains.annotations.NotNull;
import org.rdfarchitect.cim.rdf.resources.RDFA;
import org.rdfarchitect.exception.graph.GraphNotInATransactionException;
import org.rdfarchitect.exception.graph.GraphTransactionException;
import org.rdfarchitect.rdf.graph.DeltaCompressible;

import java.util.HashSet;
import java.util.UUID;

public class GraphRewindableWithUUIDs extends GraphRewindable {

    /**
     * Accepts a {@link Graph} that serves as a base version of the {@link GraphRewindableWithUUIDs}.
     *
     * @param base          The base graph
     * @param maxVersions   The maximum amount of versions the graph stores.
     * @param compressCount The amount of versions that are compressed to a new base when compressing.
     */
    public GraphRewindableWithUUIDs(@NotNull Graph base, int maxVersions, int compressCount) {
        super(enhanceWithUUIDs(base), maxVersions, compressCount);
    }

    @Override
    public void commit() {
        if (!isInTransaction()) {
            throw new GraphNotInATransactionException();
        }
        if (transactionMode() == ReadWrite.READ) {
            throw new GraphTransactionException("Trying to commit a read transaction!");
        }
        if (noChangesInTransaction()) {
            logger.debug("Commiting a transaction with no changes.");
            return;
        }
        enhanceWithUUIDs(this);
        pastDeltas.push(currentDelta);
        assert pastDeltas.peek() != null;
        currentDelta = new DeltaCompressible(pastDeltas.peek());
        futureDeltas.clear();
        if (countVersions() > maxVersions) {
            compressBase();
        }
        logger.debug("Committed transaction.");
    }

    static Graph enhanceWithUUIDs(Graph graph) {
        addUUIDsToTypedResources(graph);
        addUUIDsToUnreferencedResources(graph);
        return graph;
    }

    private static void addUUIDsToTypedResources(Graph graph) {
        var subjects = new HashSet<Node>();
        var typeTriples = graph.find(Node.ANY, RDF.type.asNode(), Node.ANY);
        while (typeTriples.hasNext()) {
            var triple = typeTriples.next();
            var subject = triple.getSubject();

            if (subject.isBlank() || graph.contains(subject, RDFA.uuid.asNode(), Node.ANY)) {
                continue;
            }

            subjects.add(subject);
        }

        for (var subject : subjects) {
            graph.add(subject, RDFA.uuid.asNode(), createUUIDNode());
        }
    }

    private static void addUUIDsToUnreferencedResources(Graph graph) {
        var objects = new HashSet<Node>();
        var allTriples = graph.find(Node.ANY, Node.ANY, Node.ANY);
        while (allTriples.hasNext()) {
            var triple = allTriples.next();
            var object = triple.getObject();

            if (!object.isURI()) {
                continue;
            }

            if (graph.contains(object, RDFA.uuid.asNode(), Node.ANY)) {
                continue;
            }

            if (graph.contains(object, Node.ANY, Node.ANY)) {
                continue;
            }

            objects.add(object);
        }

        for (var object : objects) {
            graph.add(object, RDFA.uuid.asNode(), createUUIDNode());
        }
    }

    private static Node createUUIDNode() {
        return ResourceFactory.createPlainLiteral(UUID.randomUUID().toString()).asNode();
    }

    public static void removeUUIDs(Graph graph) {
        graph.find(Node.ANY, RDFA.uuid.asNode(), Node.ANY).toList().forEach(graph::delete);
    }
}

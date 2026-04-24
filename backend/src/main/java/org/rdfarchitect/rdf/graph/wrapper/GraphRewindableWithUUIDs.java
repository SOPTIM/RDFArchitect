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
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.jetbrains.annotations.NotNull;
import org.rdfarchitect.exception.graph.GraphNotInATransactionException;
import org.rdfarchitect.exception.graph.GraphTransactionException;
import org.rdfarchitect.models.cim.rdf.resources.RDFA;
import org.rdfarchitect.rdf.graph.DeltaCompressible;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class GraphRewindableWithUUIDs extends GraphRewindable {

    private static final Set<String> RELEVANT_TYPES =
            Set.of(RDF.Property.toString(), RDFS.Class.toString());

    /**
     * Accepts a {@link Graph} that serves as a base version of the {@link
     * GraphRewindableWithUUIDs}.
     *
     * @param base The base graph
     * @param maxVersions The maximum amount of versions the graph stores.
     * @param compressCount The amount of versions that are compressed to a new base when
     *     compressing.
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
        var model = ModelFactory.createModelForGraph(graph);
        addUUIDsToTypedResources(model);
        addUUIDsToReferencedOnlyResources(model);
        return graph;
    }

    private static void addUUIDsToTypedResources(Model model) {
        var subjects =
                model.listResourcesWithProperty(RDF.type)
                        .filterKeep(r -> r.isURIResource() && !r.hasProperty(RDFA.uuid))
                        .toSet();

        for (var subject : subjects) {
            subject.addProperty(RDFA.uuid, createUUID());
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
                                        .filterKeep(GraphRewindableWithUUIDs::isReferencedOnlyURI)
                                        .mapWith(RDFNode::asResource)
                                        .forEachRemaining(objects::add));

        objects.forEach(o -> o.addProperty(RDFA.uuid, createUUID()));
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

    private static String createUUID() {
        return UUID.randomUUID().toString();
    }

    public static void removeUUIDs(Graph graph) {
        graph.find(Node.ANY, RDFA.uuid.asNode(), Node.ANY).toList().forEach(graph::delete);
    }
}

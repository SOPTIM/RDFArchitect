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
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.vocabulary.RDFS;
import org.jetbrains.annotations.NotNull;
import org.rdfarchitect.exception.graph.GraphNotInATransactionException;
import org.rdfarchitect.exception.graph.GraphTransactionException;
import org.rdfarchitect.models.cim.rdf.resources.CIMS;
import org.rdfarchitect.models.cim.rdf.resources.RDFA;
import org.rdfarchitect.rdf.graph.DeltaCompressible;
import org.rdfarchitect.rdf.graph.GraphUtils;

@Deprecated
public class GraphRewindableWithUUIDs extends GraphRewindable {

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
        super(GraphUtils.enhanceWithUUIDs(base), maxVersions, compressCount);
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
        GraphUtils.enhanceWithUUIDs(this);
        pastDeltas.push(currentDelta);
        assert pastDeltas.peek() != null;
        currentDelta = new DeltaCompressible(pastDeltas.peek());
        futureDeltas.clear();
        if (countVersions() > maxVersions) {
            compressBase();
        }
        logger.debug("Committed transaction.");
    }

    public static void removeUUIDs(Graph graph) {
        graph.find(Node.ANY, RDFA.uuid.asNode(), Node.ANY).toList().forEach(graph::delete);
    }

    public static void correctPackagePrefix(Graph graph, boolean usePackagePrefix) {
        var model = ModelFactory.createModelForGraph(graph);
        var packages = model.listResourcesWithProperty(RDF.type, CIMS.classCategory).toList();

        final var PREFIX = "Package_";

        for (var packageResource : packages) {
            var labelStmt = packageResource.getProperty(RDFS.label);
            if (labelStmt == null) {
                continue;
            }

            var currentLabel = labelStmt.getString();
            var currentUri = packageResource.getURI();
            var sepIdx = Math.max(currentUri.lastIndexOf('#'), currentUri.lastIndexOf('/'));
            var uriBase = currentUri.substring(0, sepIdx + 1);
            var uriSuffix = currentUri.substring(sepIdx + 1);

            String newLabel;
            String newUriSuffix;

            if (usePackagePrefix) {
                newLabel = currentLabel.startsWith(PREFIX) ? currentLabel : PREFIX + currentLabel;
                newUriSuffix = uriSuffix.startsWith(PREFIX) ? uriSuffix : PREFIX + uriSuffix;
            } else {
                newLabel =
                        currentLabel.startsWith(PREFIX)
                                ? currentLabel.substring(PREFIX.length())
                                : currentLabel;
                newUriSuffix =
                        uriSuffix.startsWith(PREFIX)
                                ? uriSuffix.substring(PREFIX.length())
                                : uriSuffix;
            }

            var newUri = uriBase + newUriSuffix;
            var uriChanged = !newUri.equals(currentUri);
            var labelChanged = !newLabel.equals(currentLabel);

            if (uriChanged) {
                changeURIAndLabel(packageResource, labelChanged, newUri, newLabel, model);
            } else if (labelChanged) {
                changeLabel(packageResource, labelStmt, newLabel);
            }
        }
    }

    private static void changeURIAndLabel(
            Resource packageResource,
            boolean labelChanged,
            String newUri,
            String newLabel,
            Model model) {
        var newResource = model.createResource(newUri);
        packageResource
                .listProperties()
                .toList()
                .forEach(
                        stmt -> {
                            if (stmt.getPredicate().equals(RDFS.label) && labelChanged) {
                                changeLabel(newResource, stmt, newLabel);
                            } else {
                                newResource.addProperty(stmt.getPredicate(), stmt.getObject());
                            }
                        });

        model.listStatements(null, null, packageResource)
                .toList()
                .forEach(stmt -> model.add(stmt.getSubject(), stmt.getPredicate(), newResource));
        model.listStatements(null, null, packageResource).toList().forEach(model::remove);

        model.removeAll(packageResource, null, null);
    }

    private static void changeLabel(
            Resource packageResource, Statement labelStmt, String newLabel) {
        var lang = labelStmt.getLanguage();
        packageResource.removeAll(RDFS.label);
        if (lang != null && !lang.isEmpty()) {
            packageResource.addProperty(RDFS.label, newLabel, lang);
        } else {
            packageResource.addProperty(RDFS.label, newLabel);
        }
    }
}

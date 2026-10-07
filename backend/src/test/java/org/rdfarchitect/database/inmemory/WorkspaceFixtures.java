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

package org.rdfarchitect.database.inmemory;

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Triple;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.sparql.graph.GraphFactory;
import org.rdfarchitect.models.changelog.WorkspaceChangeLogEntry;

import java.util.List;

/**
 * The commits the tests around a workspace build their histories from.
 *
 * <p>Transactions, changelog attribution and restoring all need the same few moves to set a
 * workspace up — creating a graph, writing into it, renaming or deleting it, reading back what it
 * holds — and each of them has to open and commit a transaction to be a change at all. Written once
 * here, so that the commit messages the changelog records stay the same across the tests that read
 * them.
 */
final class WorkspaceFixtures {

    static final String WORKSPACE = "workspace";
    static final String GRAPH_A = "http://example.org/a";
    static final String GRAPH_B = "http://example.org/b";

    private WorkspaceFixtures() {}

    /** Creates an empty graph, as its own commit. */
    static void createGraph(Workspace workspace, String graphUri) {
        createGraph(workspace, graphUri, GraphFactory.createDefaultGraph());
    }

    /** Creates a graph holding the given triples, as its own commit. */
    static void createGraph(Workspace workspace, String graphUri, Graph graph) {
        try (var transaction = workspace.begin(ReadWrite.WRITE)) {
            transaction.createGraph(graphUri, graph);
            transaction.commit("created graph %s".formatted(graphUri));
        }
    }

    /** Renames a graph, as its own commit. */
    static void renameGraph(Workspace workspace, String oldGraphUri, String newGraphUri) {
        try (var transaction = workspace.begin(ReadWrite.WRITE)) {
            transaction.renameGraph(oldGraphUri, newGraphUri);
            transaction.commit("renamed graph %s".formatted(oldGraphUri));
        }
    }

    /** Deletes a graph, as its own commit. */
    static void deleteGraph(Workspace workspace, String graphUri) {
        try (var transaction = workspace.begin(ReadWrite.WRITE)) {
            transaction.deleteGraph(graphUri);
            transaction.commit("deleted graph %s".formatted(graphUri));
        }
    }

    /** Replaces the workspace's prefixes, as its own commit. */
    static void setPrefixes(Workspace workspace, PrefixMapping prefixMapping) {
        try (var transaction = workspace.begin(ReadWrite.WRITE)) {
            transaction.setPrefixes(prefixMapping);
            transaction.commit("changed the namespace prefixes");
        }
    }

    /** Adds a triple to a graph's schema, as its own commit under the given message. */
    static void commitTriple(Workspace workspace, String graphUri, Triple triple, String message) {
        try (var transaction = workspace.begin(ReadWrite.WRITE)) {
            transaction.graph(graphUri).getRdfGraph().add(triple);
            transaction.commit(message);
        }
    }

    /** Removes a triple from a graph's schema, as its own commit under the given message. */
    static void deleteTriple(Workspace workspace, String graphUri, Triple triple, String message) {
        try (var transaction = workspace.begin(ReadWrite.WRITE)) {
            transaction.graph(graphUri).getRdfGraph().delete(triple);
            transaction.commit(message);
        }
    }

    /** Returns what a graph's schema holds. */
    static List<Triple> triplesIn(Workspace workspace, String graphUri) {
        try (var transaction = workspace.begin(ReadWrite.READ)) {
            return transaction.graph(graphUri).getRdfGraph().find().toList();
        }
    }

    /** Returns the change the workspace recorded last, undone or not. */
    static WorkspaceChangeLogEntry newestChange(Workspace workspace) {
        return workspace.getChangeHistory().getFirst();
    }
}

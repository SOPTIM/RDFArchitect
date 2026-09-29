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

package org.rdfarchitect.database;

import org.apache.jena.query.ReadWrite;
import org.apache.jena.shared.PrefixMapping;
import org.rdfarchitect.database.inmemory.diagrams.CrossProfileDiagramInfo;
import org.rdfarchitect.database.inmemory.diagrams.CustomDiagram;
import org.rdfarchitect.rdf.graph.wrapper.DiagramLayout;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A running transaction on one workspace, and the only way to reach its contents.
 *
 * <p>Transactions belong to the workspace, not to the individual graph, so that a change spanning
 * several graphs — copying a class from one into another, deleting two at once — commits or rolls
 * back as a whole. Because graphs are reachable only through a running transaction, accessing one
 * without a transaction is not something that has to be checked at runtime: it does not compile.
 *
 * <p>Must be used in try-with-resources. Nesting is allowed and joins the running transaction; only
 * the outermost block takes and releases the workspace lock and writes a changelog entry.
 */
public interface WorkspaceTransaction extends AutoCloseable {

    /**
     * Returns the graph identified by {@code graphUri} for the duration of this transaction.
     *
     * @param graphUri the graph URI
     * @return the graph's contents
     */
    GraphContext graph(String graphUri);

    /**
     * Returns the URIs of the graphs the workspace currently holds.
     *
     * @return the graph URIs
     */
    List<String> graphUris();

    /**
     * Returns the custom diagrams defined on the workspace itself, as opposed to those defined on
     * one of its graphs.
     *
     * @return the workspace's custom diagrams by id
     */
    Map<UUID, CustomDiagram> diagrams();

    /**
     * Returns the layout of the workspace's own diagrams.
     *
     * @return the workspace diagram layout
     */
    DiagramLayout layout();

    /**
     * Returns the cross-profile diagram of the workspace.
     *
     * @return the cross-profile diagram information
     */
    CrossProfileDiagramInfo crossProfileInfo();

    /**
     * Returns the namespace prefixes shared by all graphs of the workspace.
     *
     * @return the prefix mapping
     */
    PrefixMapping prefixes();

    /**
     * Returns the mode this transaction was opened in.
     *
     * @return the transaction mode
     */
    ReadWrite mode();

    /**
     * Commits the changes of this transaction. Every commit names what it did, so that the change
     * can be recognised in the changelog. Only the outermost commit records an entry; an inner
     * commit contributes its message to that entry, which keeps one user action one undo step.
     *
     * @param message what the change did
     */
    void commit(String message);

    /**
     * Rolls back the whole transaction, including the levels that enclose this one. Any further
     * write fails, so that a caller that catches the failure cannot commit the remains.
     */
    void abort();

    /**
     * Leaves this transaction level, rolling back if the outermost level is left with uncommitted
     * changes.
     */
    @Override
    void close();
}

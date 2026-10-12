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

import java.util.UUID;

/**
 * How the graphs of a workspace are told apart on its cross-profile diagram.
 *
 * <p>What a caller may do with the colours, as opposed to what the implementation also has to offer
 * its workspace. The implementation is a transaction participant and therefore carries {@code
 * commit}, {@code abort}, {@code undo} and {@code redo} as well; those belong to the workspace that
 * drives the transaction, and calling them from outside would cut a version that no changelog entry
 * names.
 */
public interface CrossProfileColors {

    /**
     * Returns the colour a graph is drawn in.
     *
     * @param graphUri the graph URI
     * @return the colour, or {@code null} if the graph has none
     */
    String getColor(String graphUri);

    /**
     * Sets the colour a graph is drawn in. Takes part in the running transaction.
     *
     * @param graphUri the graph URI
     * @param color the colour to use
     */
    void setColor(String graphUri, String color);

    /** Returns the id of the workspace's cross-profile diagram. */
    UUID getCrossProfileDiagramUUID();
}

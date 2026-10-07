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

package org.rdfarchitect.services.dl.update.edgelayout;

import org.rdfarchitect.database.GraphIdentifier;

public interface SyncEdgeLayoutUseCase {

    /**
     * Brings the edge diagram objects in line with the schema after the edges of a graph may have
     * changed, for example because a super class was set or an association was created or deleted.
     * This covers the diagrams of the graph as well as the merged diagrams of its dataset, see
     * {@link EdgeLayoutReconciler#reconcileEdges}.
     *
     * @param graphIdentifier the graph whose schema changed
     */
    void syncEdgeLayout(GraphIdentifier graphIdentifier);
}

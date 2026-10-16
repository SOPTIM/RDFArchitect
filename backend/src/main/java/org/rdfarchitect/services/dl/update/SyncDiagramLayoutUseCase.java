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

package org.rdfarchitect.services.dl.update;

import org.rdfarchitect.database.GraphIdentifier;

public interface SyncDiagramLayoutUseCase {

    /**
     * Brings the layout data of the diagrams of a graph and of the merged diagrams of its dataset
     * in line with the schema, after the schema of the graph or one of its custom diagrams changed.
     * Layout data of classes a diagram no longer shows is deleted, diagrams without owner are
     * deleted as a whole, and the edge diagram objects are reconciled with the edges between the
     * remaining classes. Layout data is never created for classes, that stays lazy.
     *
     * @param graphIdentifier the graph whose schema or custom diagrams changed
     */
    void syncDiagramLayout(GraphIdentifier graphIdentifier);

    /**
     * Brings the layout data of the merged diagrams of a dataset, i.e. the cross-profile diagram
     * and the custom diagrams of the dataset, in line with the graphs of the dataset, see {@link
     * #syncDiagramLayout}.
     *
     * @param datasetName the name of the dataset
     */
    void syncDatasetDiagramLayout(String datasetName);
}

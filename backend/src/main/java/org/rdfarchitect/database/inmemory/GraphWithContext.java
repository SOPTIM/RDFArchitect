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

import lombok.Getter;
import lombok.Setter;
import org.rdfarchitect.rdf.graph.wrapper.DiagramLayout;
import org.rdfarchitect.rdf.graph.wrapper.GraphRewindableWithUUIDs;

/**
 * Wrapper class that combines an {@link GraphRewindableWithUUIDs} with its associated {@link DiagramLayout}.
 */

public class GraphWithContext {

    @Getter
    private final GraphRewindableWithUUIDs rdfGraph;
    @Getter
    @Setter
    private DiagramLayout diagramLayout;

    public GraphWithContext(GraphRewindableWithUUIDs rdfGraph) {
        this.rdfGraph = rdfGraph;
    }
}

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

package org.rdfarchitect.services.archive.importing;

import org.apache.jena.graph.Graph;
import org.apache.jena.shared.PrefixMapping;
import org.rdfarchitect.database.inmemory.diagrams.CustomDiagram;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record WorkspaceArchive(
        PrefixMapping prefixes,
        boolean readOnly,
        UUID crossProfileDiagramId,
        UUID defaultPackageMRID,
        List<ArchivedGraph> graphs,
        Graph layout,
        List<CustomDiagram> diagrams,
        Map<String, String> colors,
        List<String> notices) {

    public record ArchivedGraph(
            String uri,
            UUID defaultPackageMRID,
            Graph schema,
            Graph shacl,
            Graph layout,
            List<CustomDiagram> diagrams) {}
}

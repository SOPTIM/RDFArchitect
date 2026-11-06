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

package org.rdfarchitect.services.archive;

import org.rdfarchitect.database.inmemory.diagrams.ClassInDiagram;
import org.rdfarchitect.database.inmemory.diagrams.CustomDiagram;
import org.rdfarchitect.models.cim.data.dto.relations.uri.URI;

import java.util.List;
import java.util.UUID;

public record ArchivedGraphDiagram(UUID diagramId, String name, List<UUID> classes) {

    public static ArchivedGraphDiagram of(CustomDiagram diagram, String graphUri) {
        var classes =
                diagram.getClasses().stream()
                        // The editor only puts a graph's own classes on its diagrams.
                        .filter(entry -> entry.getGraphUri().toString().equals(graphUri))
                        .map(ClassInDiagram::getUuid)
                        .toList();
        return new ArchivedGraphDiagram(diagram.getDiagramId(), diagram.getName(), classes);
    }

    public CustomDiagram toCustomDiagram(String graphUri) {
        var uri = new URI(graphUri);
        var restored = classes.stream().map(uuid -> new ClassInDiagram(uuid, uri)).toList();
        return new CustomDiagram(diagramId, name, restored);
    }
}

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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ArchivedWorkspaceDiagram(
        UUID diagramId, String name, Map<String, List<UUID>> classes) {

    public static ArchivedWorkspaceDiagram of(
            CustomDiagram diagram, Map<String, String> folderByGraphUri) {
        var classes = new LinkedHashMap<String, List<UUID>>();
        for (var entry : diagram.getClasses()) {
            var folder = folderByGraphUri.get(entry.getGraphUri().toString());
            if (folder != null) {
                classes.computeIfAbsent(folder, _ -> new ArrayList<>()).add(entry.getUuid());
            }
        }
        return new ArchivedWorkspaceDiagram(diagram.getDiagramId(), diagram.getName(), classes);
    }

    public CustomDiagram toCustomDiagram(Map<String, String> graphUriByFolder) {
        var restored = new ArrayList<ClassInDiagram>();
        classes.forEach(
                (folder, uuids) -> {
                    var graphUri = new URI(graphUriByFolder.get(folder));
                    uuids.forEach(uuid -> restored.add(new ClassInDiagram(uuid, graphUri)));
                });
        return new CustomDiagram(diagramId, name, restored);
    }
}

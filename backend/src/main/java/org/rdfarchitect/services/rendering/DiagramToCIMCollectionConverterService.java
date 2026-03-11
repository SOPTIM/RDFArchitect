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

package org.rdfarchitect.services.rendering;

import lombok.RequiredArgsConstructor;
import org.rdfarchitect.cim.data.dto.CIMCollection;
import org.rdfarchitect.cim.rendering.GraphFilter;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DiagramToCIMCollectionConverterService implements DiagramToCIMCollectionConverterUseCase {

    private final DatabasePort databasePort;

    private final GraphToCIMCollectionConverterService converter;

    @Override
    public CIMCollection convert(GraphIdentifier graphIdentifier, String diagramId) {
        var graphWithContext = databasePort.getGraphWithContext(graphIdentifier);
        var diagrams = graphWithContext.getCustomDiagrams();
        var diagramUUID = UUID.fromString(diagramId);
        if (!diagrams.containsKey(diagramUUID)) {
            throw new IllegalArgumentException("Diagram with ID " + diagramId + " not found in graph " + graphIdentifier);
        }

        var diagram = diagrams.get(diagramUUID);
        var classUUIDs = diagram.getClasses().stream().map(cls -> cls.getUuid().toString()).toList();
        var filter = new GraphFilter(true);
        filter.setIncludeRelationsToExternalPackages(false);
        filter.setAllowedUUIDs(classUUIDs);
        return converter.convert(graphIdentifier, filter);
    }
}

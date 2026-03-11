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

package org.rdfarchitect.services.diagrams;

import lombok.RequiredArgsConstructor;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.database.inmemory.diagrams.CustomDiagram;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomDiagramService implements ReplaceCustomDiagramUseCase, DeleteCustomDiagramUseCase {

    private final DatabasePort databasePort;

    @Override
    public void deleteCustomDiagram(GraphIdentifier graphIdentifier, String diagramId) {
        var graphWithContext = databasePort.getGraphWithContext(graphIdentifier);
        graphWithContext.getCustomDiagrams().remove(UUID.fromString(diagramId));
    }

    @Override
    public void replaceCustomDiagram(GraphIdentifier graphIdentifier, String diagramId, CustomDiagram diagram) {
        var graphWithContext = databasePort.getGraphWithContext(graphIdentifier);
        graphWithContext.getCustomDiagrams().put(UUID.fromString(diagramId), diagram);
    }
}

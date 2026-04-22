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
import org.rdfarchitect.database.inmemory.diagrams.ClassInDiagram;
import org.rdfarchitect.database.inmemory.diagrams.CustomDiagram;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomDiagramService implements GetCustomDiagramsUseCase, ReplaceCustomDiagramUseCase, DeleteCustomDiagramUseCase, AddToDiagramUseCase, RemoveFromDiagramUseCase {

    private final DatabasePort databasePort;

    @Override
    public List<CustomDiagram> getCustomDiagramsForGraph(GraphIdentifier graphIdentifier) {
        return databasePort.getGraphWithContext(graphIdentifier).getCustomDiagrams().values().stream().toList();
    }

    @Override
    public List<CustomDiagram> getCustomDiagramsForDataset(String datasetName) {
        return databasePort.getDatasetDiagrams(datasetName).values().stream().toList();
    }

    @Override
    public void deleteCustomDiagram(String datasetName, String diagramId) {
        var diagrams = databasePort.getDatasetDiagrams(datasetName);
        diagrams.remove(UUID.fromString(diagramId));
    }

    @Override
    public void replaceCustomDiagram(String datasetName, String diagramId, CustomDiagram diagram) {
        var diagrams = databasePort.getDatasetDiagrams(datasetName);
        diagrams.put(UUID.fromString(diagramId), diagram);
    }

    @Override
    public void addToDiagram(String datasetName, String diagramId, List<ClassInDiagram> classes) {
        var diagrams = databasePort.getDatasetDiagrams(datasetName);
        var diagram = diagrams.get(UUID.fromString(diagramId));
        if (diagram != null) {
            diagram.getClasses().addAll(classes);
        }
    }

    @Override
    public void removeFromDiagram(String datasetName, String diagramId, UUID classId) {
        var diagrams = databasePort.getDatasetDiagrams(datasetName);

        var diagram = diagrams.get(UUID.fromString(diagramId));
        if (diagram != null) {
            diagram.getClasses().removeIf(c -> c.getUuid().equals(classId));
        }
    }

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

    @Override
    public void addToDiagram(GraphIdentifier graphIdentifier, String diagramId, List<ClassInDiagram> classes) {
        var graphWithContext = databasePort.getGraphWithContext(graphIdentifier);
        var diagram = graphWithContext.getCustomDiagrams().get(UUID.fromString(diagramId));
        if (diagram != null) {
            diagram.getClasses().addAll(classes);
        }
    }

    @Override
    public void removeFromDiagram(GraphIdentifier graphIdentifier, String diagramId, UUID classId) {
        var graphWithContext = databasePort.getGraphWithContext(graphIdentifier);
        var diagram = graphWithContext.getCustomDiagrams().get(UUID.fromString(diagramId));
        if (diagram != null) {
            diagram.getClasses().removeIf(c -> c.getUuid().equals(classId));
        }
    }

    @Override
    public void removeFromAllDiagrams(GraphIdentifier graphIdentifier, UUID classId) {
        var graphWithContext = databasePort.getGraphWithContext(graphIdentifier);
        for (var diagram : graphWithContext.getCustomDiagrams().values()) {
            diagram.getClasses().removeIf(c -> c.getUuid().equals(classId));
        }
        var datasetDiagrams = databasePort.getDatasetDiagrams(graphIdentifier.getDatasetName());
        for (var diagram : datasetDiagrams.values()) {
            diagram.getClasses().removeIf(c -> c.getUuid().equals(classId));
        }
    }
}

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

import org.apache.jena.query.ReadWrite;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.database.inmemory.diagrams.CustomDiagram;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomDiagramService
        implements GetCustomDiagramsUseCase,
                ReplaceCustomDiagramUseCase,
                DeleteCustomDiagramUseCase,
                RemoveFromDiagramUseCase {

    private final DatabasePort databasePort;

    @Override
    public List<CustomDiagram> getCustomDiagramsForGraph(GraphIdentifier graphIdentifier) {
        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.READ)) {
            return ctx.getCustomDiagrams().values().stream().toList();
        }
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
        if (!Objects.equals(diagramId, diagram.getDiagramId().toString())) {
            throw new IllegalArgumentException(
                    "Diagram ID mismatch: URL parameter '"
                            + diagramId
                            + "' does not match diagram object ID '"
                            + diagram.getDiagramId()
                            + "'");
        }
        var diagrams = databasePort.getDatasetDiagrams(datasetName);
        diagrams.put(UUID.fromString(diagramId), diagram);
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
        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.WRITE)) {
            ctx.getCustomDiagrams().remove(UUID.fromString(diagramId));
            ctx.commit("deleted diagram %s".formatted(diagramId));
        }
    }

    @Override
    public void replaceCustomDiagram(
            GraphIdentifier graphIdentifier, String diagramId, CustomDiagram diagram) {
        if (!Objects.equals(diagramId, diagram.getDiagramId().toString())) {
            throw new IllegalArgumentException(
                    "Diagram ID mismatch: URL parameter '"
                            + diagramId
                            + "' does not match diagram object ID '"
                            + diagram.getDiagramId()
                            + "'");
        }
        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.WRITE)) {
            ctx.getCustomDiagrams().put(UUID.fromString(diagramId), diagram);
            ctx.commit("replaced diagram %s".formatted(diagramId));
        }
    }

    @Override
    public void removeFromDiagram(GraphIdentifier graphIdentifier, String diagramId, UUID classId) {
        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.WRITE)) {
            var diagram = ctx.getCustomDiagrams().get(UUID.fromString(diagramId));
            if (diagram != null) {
                diagram.getClasses().removeIf(c -> c.getUuid().equals(classId));
            }
            ctx.commit("removed class %s from diagram %s".formatted(classId, diagramId));
        }
    }

    @Override
    public void removeFromAllDiagrams(GraphIdentifier graphIdentifier, UUID classId) {
        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.WRITE)) {
            for (var diagram :
                    databasePort
                            .getGraphWithContext(graphIdentifier)
                            .getCustomDiagrams()
                            .values()) {
                diagram.getClasses().removeIf(c -> c.getUuid().equals(classId));
            }
            for (var diagram :
                    databasePort.getDatasetDiagrams(graphIdentifier.datasetName()).values()) {
                diagram.getClasses().removeIf(c -> c.getUuid().equals(classId));
            }
            ctx.commit("removed class %s from all diagrams".formatted(classId));
        }
    }
}

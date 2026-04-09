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
import org.rdfarchitect.api.dto.ClassDTO;
import org.rdfarchitect.api.dto.ClassMapper;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.database.inmemory.diagrams.ClassInDiagram;
import org.rdfarchitect.database.inmemory.diagrams.CustomDiagram;
import org.rdfarchitect.models.cim.data.CIMObjectFetcher;
import org.rdfarchitect.models.cim.data.dto.CIMClass;
import org.rdfarchitect.models.cim.data.dto.relations.uri.URI;
import org.rdfarchitect.models.cim.queries.select.CIMQueries;
import org.rdfarchitect.rdf.graph.wrapper.GraphRewindableWithUUIDs;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CustomDiagramService implements GetCustomDiagramsUseCase, ReplaceCustomDiagramUseCase, DeleteCustomDiagramUseCase, AddToDiagramUseCase, RemoveFromDiagramUseCase,
          GetFullClassesForDiagramUseCase {

    private final DatabasePort databasePort;
    private final ClassMapper classMapper;

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
            diagram.getClasses().remove(new ClassInDiagram(classId, new URI(graphIdentifier.getGraphUri())));
        }
    }

    @Override
    public void removeFromAllDiagrams(GraphIdentifier graphIdentifier, UUID classId) {
        var graphWithContext = databasePort.getGraphWithContext(graphIdentifier);
        for (var diagram : graphWithContext.getCustomDiagrams().values()) {
            diagram.getClasses().remove(new ClassInDiagram(classId, new URI(graphIdentifier.getGraphUri())));
        }
        var datasetDiagrams = databasePort.getDatasetDiagrams(graphIdentifier.getDatasetName());
        for (var diagram : datasetDiagrams.values()) {
            diagram.getClasses().remove(new ClassInDiagram(classId, new URI(graphIdentifier.getGraphUri())));
        }
    }

    @Override
    public List<ClassDTO> getFullClasses(GraphIdentifier graphIdentifier, String diagramId) {
        var graphWithContext = databasePort.getGraphWithContext(graphIdentifier);
        var diagram = graphWithContext.getCustomDiagrams().get(UUID.fromString(diagramId));
        var classList = getSpecifiedClassesForGraph(graphIdentifier, diagram);
        return classMapper.toDTOList(classList);
    }

    @Override
    public List<ClassDTO> getFullClasses(String datasetName, String diagramId) {
        var diagrams = databasePort.getDatasetDiagrams(datasetName);
        var diagramUUID = UUID.fromString(diagramId);
        if (!diagrams.containsKey(diagramUUID)) {
            throw new IllegalArgumentException("Diagram with ID " + diagramId + " not found in dataset " + datasetName);
        }

        var diagram = diagrams.get(diagramUUID);
        var classesByGraph = diagram.getClasses().stream()
                                    .collect(Collectors.groupingBy(ClassInDiagram::getGraphUri));

        var mergedClassList = new ArrayList<CIMClass>();
        for (var entry : classesByGraph.entrySet()) {
            var graphIdentifier = new GraphIdentifier(datasetName, entry.getKey().toString());
            mergedClassList.addAll(getSpecifiedClassesForGraph(graphIdentifier, diagram));
        }
        return classMapper.toDTOList(mergedClassList);
    }

    private List<CIMClass> getSpecifiedClassesForGraph(GraphIdentifier graphIdentifier, CustomDiagram diagram) {
        GraphRewindableWithUUIDs graph = null;
        try {
            graph = databasePort.getGraphWithContext(graphIdentifier).getRdfGraph();
            graph.begin();

            var classUUIDs = diagram.getClasses().stream()
                                    .map(c -> c.getUuid().toString())
                                    .toList();
            var query = CIMQueries.getSpecifiedClassesQuery(
                      databasePort.getPrefixMapping(graphIdentifier.getDatasetName()),
                      graphIdentifier.getGraphUri(),
                      classUUIDs);

            return new CIMObjectFetcher(
                      graph,
                      graphIdentifier.getGraphUri(),
                      databasePort.getPrefixMapping(graphIdentifier.getDatasetName())
            ).fetchCIMClassList(query.build());
        } finally {
            if (graph != null) {
                graph.end();
            }
        }
    }
}

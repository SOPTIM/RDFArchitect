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

import org.apache.jena.graph.Graph;
import org.apache.jena.query.ReadWrite;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphContext;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.database.inmemory.diagrams.ClassInDiagram;
import org.rdfarchitect.database.inmemory.diagrams.CustomDiagram;
import org.rdfarchitect.models.cim.data.dto.CIMCollection;
import org.rdfarchitect.models.cim.rendering.GraphFilter;
import org.rdfarchitect.rdf.graph.GraphUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DiagramToCIMCollectionConverterService
        implements DiagramToCIMCollectionConverterUseCase {

    private final DatabasePort databasePort;

    private final GraphToCIMCollectionConverterService converter;

    private record GraphEntry(GraphIdentifier graphIdentifier, List<String> classUUIDs) {}

    private record CopiedGraph(GraphEntry entry, Graph graph) {}

    @Override
    public CIMCollection convert(GraphContext ctx, GraphIdentifier identifier, UUID diagramId) {
        var diagrams = ctx.getCustomDiagrams();
        if (!diagrams.containsKey(diagramId)) {
            throw new IllegalArgumentException(
                    "Diagram with ID "
                            + diagramId.toString()
                            + " not found in graph "
                            + identifier);
        }

        var diagram = diagrams.get(diagramId);
        var classUUIDs =
                diagram.getClasses().stream().map(cls -> cls.getUuid().toString()).toList();
        return converter.convert(ctx.getRdfGraph(), identifier, buildFilter(classUUIDs));
    }

    @Override
    public CIMCollection convert(String datasetName, UUID diagramId) {
        var diagram = getDiagram(datasetName, diagramId);
        var graphEntries = buildSortedGraphEntries(datasetName, diagram);
        var copiedGraphs = copyGraphsUnderLocks(graphEntries);
        return convertAndMerge(copiedGraphs);
    }

    private CustomDiagram getDiagram(String datasetName, UUID diagramId) {
        var diagrams = databasePort.getDatasetDiagrams(datasetName);
        if (!diagrams.containsKey(diagramId)) {
            throw new IllegalArgumentException(
                    "Diagram with ID "
                            + diagramId.toString()
                            + " not found in dataset "
                            + datasetName);
        }
        return diagrams.get(diagramId);
    }

    private List<GraphEntry> buildSortedGraphEntries(String datasetName, CustomDiagram diagram) {
        return diagram.getClasses().stream()
                .collect(Collectors.groupingBy(ClassInDiagram::getGraphUri))
                .entrySet()
                .stream()
                .sorted(Comparator.comparing(e -> e.getKey().toString()))
                .map(
                        e ->
                                new GraphEntry(
                                        new GraphIdentifier(datasetName, e.getKey().toString()),
                                        e.getValue().stream()
                                                .map(c -> c.getUuid().toString())
                                                .toList()))
                .toList();
    }

    private List<CopiedGraph> copyGraphsUnderLocks(List<GraphEntry> graphEntries) {
        var copiedGraphs = new ArrayList<CopiedGraph>(graphEntries.size());
        var contexts = new ArrayList<GraphContext>(graphEntries.size());
        try {
            for (var entry : graphEntries) {
                var ctx =
                        databasePort
                                .getGraphWithContext(entry.graphIdentifier())
                                .begin(ReadWrite.READ);
                contexts.add(ctx);
                copiedGraphs.add(new CopiedGraph(entry, GraphUtils.deepCopy(ctx.getRdfGraph())));
            }
        } finally {
            releaseAll(contexts);
        }
        return copiedGraphs;
    }

    private void releaseAll(List<GraphContext> contexts) {
        for (var ctx : contexts) {
            ctx.close();
        }
    }

    private CIMCollection convertAndMerge(List<CopiedGraph> copiedGraphs) {
        var mergedCollection = new CIMCollection();
        for (var copied : copiedGraphs) {
            var filter = buildFilter(copied.entry().classUUIDs());
            var partial =
                    converter.convert(copied.graph(), copied.entry().graphIdentifier(), filter);
            mergeInto(mergedCollection, partial);
        }
        return mergedCollection;
    }

    private GraphFilter buildFilter(List<String> classUUIDs) {
        var filter = new GraphFilter(true);
        filter.setIncludeRelationsToExternalPackages(false);
        filter.setAllowedUUIDs(classUUIDs);
        return filter;
    }

    private void mergeInto(CIMCollection target, CIMCollection source) {
        target.getPackages().addAll(source.getPackages());
        target.getClasses().addAll(source.getClasses());
        target.getEnums().addAll(source.getEnums());
        target.getAttributes().addAll(source.getAttributes());
        target.getEnumEntries().addAll(source.getEnumEntries());
        target.getAssociations().addAll(source.getAssociations());
    }
}

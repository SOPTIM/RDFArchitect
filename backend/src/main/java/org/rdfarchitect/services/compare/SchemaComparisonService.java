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

package org.rdfarchitect.services.compare;

import lombok.RequiredArgsConstructor;

import org.apache.jena.query.TxnType;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.models.changes.triplechanges.TriplePackageChange;
import org.rdfarchitect.rdf.graph.source.builder.implementations.GraphFileSourceBuilderImpl;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SchemaComparisonService implements SchemaComparisonUseCase {

    private static final String GRAPH_URI = "http://example.org/graph";
    private final DatabasePort databasePort;

    @Override
    public List<TriplePackageChange> compareSchemas(
            GraphIdentifier graphIdentifier, MultipartFile file) {
        var updatedGraph = databasePort.getGraphWithContext(graphIdentifier).getRdfGraph();
        var originalGraph =
                new GraphFileSourceBuilderImpl()
                        .setFile(file)
                        .setGraphName(GRAPH_URI)
                        .build()
                        .graph();
        List<TriplePackageChange> result;

        try {
            updatedGraph.begin(TxnType.READ);
            result = TripleChangeAnalyser.compareGraphs(originalGraph, updatedGraph);
        } finally {
            updatedGraph.end();
        }
        return result;
    }

    @Override
    public List<TriplePackageChange> compareSchemas(MultipartFile file1, MultipartFile file2) {
        var graph1 =
                new GraphFileSourceBuilderImpl()
                        .setFile(file1)
                        .setGraphName(GRAPH_URI + "/file1")
                        .build()
                        .graph();
        var graph2 =
                new GraphFileSourceBuilderImpl()
                        .setFile(file2)
                        .setGraphName(GRAPH_URI + "/file2")
                        .build()
                        .graph();

        return TripleChangeAnalyser.compareGraphs(graph1, graph2);
    }

    @Override
    public List<TriplePackageChange> compareSchemas(
            GraphIdentifier originalGraphIdentifier, GraphIdentifier updatedGraphIdentifier) {
        var originalGraph = databasePort.getGraphWithContext(originalGraphIdentifier).getRdfGraph();
        var updatedGraph = databasePort.getGraphWithContext(updatedGraphIdentifier).getRdfGraph();
        List<TriplePackageChange> result;

        if (originalGraphIdentifier.equals(updatedGraphIdentifier)) {
            return new ArrayList<>();
        }
        try {
            originalGraph.begin(TxnType.READ);
            try {
                updatedGraph.begin(TxnType.READ);
                result = TripleChangeAnalyser.compareGraphs(originalGraph, updatedGraph);
            } finally {
                updatedGraph.end();
            }
        } finally {
            originalGraph.end();
        }
        return result;
    }
}

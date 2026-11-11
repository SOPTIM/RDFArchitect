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

package org.rdfarchitect.services.schemamigration;

import lombok.RequiredArgsConstructor;

import org.apache.jena.graph.Graph;
import org.apache.jena.query.ReadWrite;
import org.rdfarchitect.context.MigrationSessionStore;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.rdf.graph.GraphUtils;
import org.rdfarchitect.rdf.graph.source.builder.implementations.GraphFileSourceBuilderImpl;
import org.rdfarchitect.services.compare.TripleChangeAnalyser;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Opens and closes a migration session: loads both schemas into the session context and computes
 * the triple and semantic diffs every later migration step builds on.
 */
@Service
@RequiredArgsConstructor
public class MigrationContextService
        implements SetMigrationContextUseCase, ClearMigrationContextUseCase {

    private static final String GRAPH_URI = "http://example.org/graph";

    private final MigrationSessionStore migrationSessionStore;
    private final DatabasePort databasePort;

    @Override
    public void setMigrationContext(
            MultipartFile originalSchema, GraphIdentifier updatedSchema, boolean ignorePrefixes) {
        initContext(loadGraph(originalSchema, GRAPH_URI), loadGraph(updatedSchema), ignorePrefixes);
    }

    @Override
    public void setMigrationContext(
            GraphIdentifier originalSchema, GraphIdentifier updatedSchema, boolean ignorePrefixes) {
        initContext(loadGraph(originalSchema), loadGraph(updatedSchema), ignorePrefixes);
    }

    @Override
    public void setMigrationContext(
            GraphIdentifier originalSchema, MultipartFile updatedSchema, boolean ignorePrefixes) {
        initContext(loadGraph(originalSchema), loadGraph(updatedSchema, GRAPH_URI), ignorePrefixes);
    }

    @Override
    public void setMigrationContext(
            MultipartFile originalSchema, MultipartFile updatedSchema, boolean ignorePrefixes) {
        initContext(
                loadGraph(originalSchema, GRAPH_URI + "/original"),
                loadGraph(updatedSchema, GRAPH_URI + "/updated"),
                ignorePrefixes);
    }

    @Override
    public void clearMigrationContext() {
        migrationSessionStore.clearContext();
    }

    private Graph loadGraph(GraphIdentifier schema) {
        try (var context = databasePort.getGraphWithContext(schema).begin(ReadWrite.READ)) {
            return GraphUtils.deepCopy(context.getRdfGraph());
        }
    }

    private Graph loadGraph(MultipartFile schema, String graphName) {
        return new GraphFileSourceBuilderImpl()
                .setFile(schema)
                .setGraphName(graphName)
                .build()
                .graph();
    }

    private void initContext(Graph originalGraph, Graph updatedGraph, boolean ignorePrefixes) {
        var context = migrationSessionStore.getContext();
        context.clear();
        context.setOriginalSchema(originalGraph);
        context.setUpdatedSchema(updatedGraph);
        context.setIgnorePrefixes(ignorePrefixes);
        var tripleChanges =
                TripleChangeAnalyser.compareGraphsDisregardingPackages(originalGraph, updatedGraph);
        context.setTripleDiff(tripleChanges);
        var semanticChanges = SemanticChangeAnalyser.getSemanticChanges(tripleChanges);
        context.setSemanticDiff(semanticChanges);
    }
}

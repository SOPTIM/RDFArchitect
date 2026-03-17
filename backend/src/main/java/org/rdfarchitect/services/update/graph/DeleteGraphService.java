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

package org.rdfarchitect.services.update.graph;

import lombok.RequiredArgsConstructor;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.rdf.graph.source.builder.implementations.GraphFileSourceBuilderImpl;
import org.rdfarchitect.services.dl.update.packagelayout.CreateDiagramLayoutUseCase;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class DeleteGraphService implements DeleteGraphUseCase, ReplaceGraphUseCase {

    private final DatabasePort databasePort;
    private final CreateDiagramLayoutUseCase createDiagramLayoutUseCase;

    @Override
    public void deleteGraph(GraphIdentifier graphIdentifier) {
        databasePort.deleteGraph(graphIdentifier);
    }

    @Override
    public void replaceGraph(GraphIdentifier graphIdentifier, MultipartFile file) {
        databasePort.deleteGraph(graphIdentifier);
        if (file == null || file.isEmpty()) {
            databasePort.createEmptyGraph(graphIdentifier);
        } else {
            var graph = new GraphFileSourceBuilderImpl()
                    .setFile(file)
                    .setGraphName(graphIdentifier.getGraphUri())
                    .build()
                    .graph();
            databasePort.createGraph(graphIdentifier, graph);
        }

        createDiagramLayoutUseCase.createDiagramLayout(graphIdentifier);
    }
}

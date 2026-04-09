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

package org.rdfarchitect.services.select.ontology;

import lombok.RequiredArgsConstructor;
import org.apache.jena.query.TxnType;
import org.apache.jena.rdf.model.ModelFactory;
import org.rdfarchitect.api.dto.ontology.OntologyEntry;
import org.rdfarchitect.models.cim.ontology.OntologyGeneratableEntriesBuilder;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.rdf.graph.wrapper.GraphRewindableWithUUIDs;
import org.rdfarchitect.services.ChangeLogUseCase;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GenerateOntologyEntriesService implements GenerateOntologyEntriesUseCase {

    private final DatabasePort databasePort;
    private final ChangeLogUseCase changeLogUseCase;

    @Override
    public List<OntologyEntry> generateOntologyEntries(GraphIdentifier graphIdentifier) {
        GraphRewindableWithUUIDs graph = null;
        try {
            graph = databasePort.getGraphWithContext(graphIdentifier).getRdfGraph();
            graph.begin(TxnType.READ);
            var model = ModelFactory.createModelForGraph(graph);
            model.setNsPrefixes(databasePort.getPrefixMapping(graphIdentifier.getDatasetName()));
            return new OntologyGeneratableEntriesBuilder(model)
                      .generateDCTModified(changeLogUseCase.listChanges(graphIdentifier))
                      .generateDCTIssued()
                      .build();
        } finally {
            if (graph != null) {
                graph.end();
            }
        }
    }
}

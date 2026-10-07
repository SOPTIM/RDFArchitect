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

import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.ModelFactory;
import org.rdfarchitect.api.dto.ChangeLogEntryMapper;
import org.rdfarchitect.api.dto.ontology.OntologyEntry;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.models.changelog.WorkspaceChangeLogEntry;
import org.rdfarchitect.models.cim.ontology.OntologyGeneratableEntriesBuilder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GenerateOntologyEntriesService implements GenerateOntologyEntriesUseCase {

    private final DatabasePort databasePort;

    private final ChangeLogEntryMapper changeLogEntryMapper;

    @Override
    public List<OntologyEntry> generateOntologyEntries(GraphIdentifier graphIdentifier) {
        try (var transaction =
                databasePort.beginTransaction(graphIdentifier.datasetName(), ReadWrite.READ)) {
            var ctx = transaction.graph(graphIdentifier.graphUri());
            var model = ModelFactory.createModelForGraph(ctx.getRdfGraph());
            model.setNsPrefixes(databasePort.getPrefixMapping(graphIdentifier.datasetName()));
            return new OntologyGeneratableEntriesBuilder(model)
                    .generateDCTModified(
                            changeLogEntryMapper.toDTOList(changesTouching(graphIdentifier)))
                    .generateDCTIssued()
                    .build();
        }
    }

    /**
     * The changes that touched this graph, newest first. The log is workspace-wide, but
     * dcterms:modified speaks for one graph: an edit in a neighbouring one must not restamp it.
     */
    private List<WorkspaceChangeLogEntry> changesTouching(GraphIdentifier graphIdentifier) {
        return databasePort.listChanges(graphIdentifier.datasetName()).stream()
                .filter(entry -> entry.affectedGraphUris().contains(graphIdentifier.graphUri()))
                .toList();
    }
}

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

package org.rdfarchitect.services.update.classes.associations;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.rdfarchitect.api.dto.association.AssociationPairDTO;
import org.rdfarchitect.api.dto.association.AssociationPairMapper;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.database.inmemory.InMemorySparqlExecutor;
import org.rdfarchitect.models.changelog.ChangeLogEntry;
import org.rdfarchitect.models.cim.queries.update.CIMUpdates;
import org.rdfarchitect.services.ChangeLogUseCase;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AssociationsService implements CreateAssociationUseCase, UpdateAssociationsUseCase {

    private final DatabasePort databasePort;
    private final AssociationPairMapper associationPairMapper;
    private final ChangeLogUseCase changeLogUseCase;

    public record AssociationUUIDs(UUID fromUUID, UUID toUUID) {}

    @Override
    public AssociationUUIDs createAssociation(
            GraphIdentifier graphIdentifier, AssociationPairDTO associationPair) {
        var cimAssociationPair = associationPairMapper.toCIMObject(associationPair);
        var from = cimAssociationPair.getFrom();
        var to = cimAssociationPair.getTo();
        if (from.getUuid() == null) {
            from.setUuid(UUID.randomUUID());
        }
        if (to.getUuid() == null) {
            to.setUuid(UUID.randomUUID());
        }
        var update =
                CIMUpdates.insertAssociation(
                        databasePort.getPrefixMapping(graphIdentifier.datasetName()),
                        graphIdentifier.graphUri(),
                        cimAssociationPair);

        var graph = databasePort.getGraphWithContext(graphIdentifier).getRdfGraph();
        InMemorySparqlExecutor.executeSingleUpdate(
                graph, update.build(), graphIdentifier.graphUri());
        changeLogUseCase.recordChange(
                graphIdentifier,
                new ChangeLogEntry(
                        "Created association from " + from.getUuid() + " to " + to.getUuid(),
                        graph.getLastDelta()));
        return new AssociationUUIDs(from.getUuid(), to.getUuid());
    }

    @Override
    public AssociationUUIDs replaceAssociation(
            GraphIdentifier graphIdentifier, AssociationPairDTO associationPair) {
        var cimAssociationPair = associationPairMapper.toCIMObject(associationPair);
        var update =
                CIMUpdates.replaceAssociation(
                        databasePort.getPrefixMapping(graphIdentifier.datasetName()),
                        graphIdentifier.graphUri(),
                        cimAssociationPair);

        var fromUUID = cimAssociationPair.getFrom().getUuid();
        var toUUID = cimAssociationPair.getTo().getUuid();
        var graph = databasePort.getGraphWithContext(graphIdentifier).getRdfGraph();
        InMemorySparqlExecutor.executeSingleUpdate(
                graph, update.build(), graphIdentifier.graphUri());
        changeLogUseCase.recordChange(
                graphIdentifier,
                new ChangeLogEntry(
                        "Replaced association from " + fromUUID + " to " + toUUID,
                        graph.getLastDelta()));
        return new AssociationUUIDs(fromUUID, toUUID);
    }

    @Override
    public void replaceAllAssociations(
            GraphIdentifier graphIdentifier,
            String classUUID,
            List<AssociationPairDTO> associationPairList) {
        var cimAssociationPairs = associationPairMapper.toCIMObjectList(associationPairList);
        var update =
                CIMUpdates.replaceAssociations(
                        databasePort.getPrefixMapping(graphIdentifier.datasetName()),
                        graphIdentifier.graphUri(),
                        classUUID,
                        cimAssociationPairs);
        var graph = databasePort.getGraphWithContext(graphIdentifier).getRdfGraph();
        InMemorySparqlExecutor.executeSingleUpdate(
                graph, update.build(), graphIdentifier.graphUri());
        changeLogUseCase.recordChange(
                graphIdentifier,
                new ChangeLogEntry(
                        "Replaced all associations for class " + classUUID, graph.getLastDelta()));
    }
}

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

import lombok.RequiredArgsConstructor;

import org.apache.jena.arq.querybuilder.UpdateBuilder;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.update.UpdateExecutionFactory;
import org.rdfarchitect.api.dto.association.AssociationPairDTO;
import org.rdfarchitect.api.dto.association.AssociationPairMapper;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphContext;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.database.inmemory.SessionDataStore;
import org.rdfarchitect.models.cim.data.dto.CIMAssociationPair;
import org.rdfarchitect.models.cim.queries.update.CIMUpdates;
import org.rdfarchitect.models.cim.relations.model.CIMResourceUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AssociationsService implements CreateAssociationUseCase, UpdateAssociationsUseCase {

    private final DatabasePort databasePort;
    private final AssociationPairMapper associationPairMapper;

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

        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.WRITE)) {
            executeOnGraph(ctx, graphIdentifier, update);
            ctx.commit(
                    buildAssociationMessage("Created", ctx, associationPair, cimAssociationPair));
        }
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

        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.WRITE)) {
            executeOnGraph(ctx, graphIdentifier, update);
            ctx.commit(
                    buildAssociationMessage("Replaced", ctx, associationPair, cimAssociationPair));
        }
        return new AssociationUUIDs(
                cimAssociationPair.getFrom().getUuid(), cimAssociationPair.getTo().getUuid());
    }

    @Override
    public void replaceAllAssociations(
            GraphIdentifier graphIdentifier,
            UUID classUUID,
            List<AssociationPairDTO> associationPairList) {
        var cimAssociationPairs = associationPairMapper.toCIMObjectList(associationPairList);
        var update =
                CIMUpdates.replaceAssociations(
                        databasePort.getPrefixMapping(graphIdentifier.datasetName()),
                        graphIdentifier.graphUri(),
                        classUUID,
                        cimAssociationPairs);

        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.WRITE)) {
            var classResource = CIMResourceUtils.findResourceForUuid(ctx.getRdfGraph(), classUUID);
            var classLabel = CIMResourceUtils.findLabelForResource(classResource);

            executeOnGraph(ctx, graphIdentifier, update);

            ctx.commit("Replaced all associations for class \"%s\"".formatted(classLabel));
        }
    }

    /**
     * Executes an update built for {@code graphIdentifier} on that graph. The builders in {@link
     * CIMUpdates} scope their operations with {@code WITH <graphUri>}, so the update has to run
     * against a dataset that knows the graph under that name — targeting the graph directly would
     * write into a named graph nobody holds and silently drop the change.
     */
    private void executeOnGraph(
            GraphContext ctx, GraphIdentifier graphIdentifier, UpdateBuilder update) {
        var dataset =
                SessionDataStore.wrapGraphInDataset(ctx.getRdfGraph(), graphIdentifier.graphUri());
        UpdateExecutionFactory.create(update.buildRequest(), dataset).execute();
    }

    private String buildAssociationMessage(
            String action,
            GraphContext session,
            AssociationPairDTO dto,
            CIMAssociationPair cimPair) {
        var from = cimPair.getFrom();
        var to = cimPair.getTo();
        var fromClassLabel =
                CIMResourceUtils.findLabelForResource(
                        CIMResourceUtils.findResourceForUri(
                                session.getRdfGraph(), dto.getFrom().getDomain()));
        var toClassLabel =
                CIMResourceUtils.findLabelForResource(
                        CIMResourceUtils.findResourceForUri(
                                session.getRdfGraph(), dto.getTo().getDomain()));
        return "%s association \"%s.%s\" → \"%s.%s\""
                .formatted(
                        action,
                        fromClassLabel,
                        from.getLabel().getValue(),
                        toClassLabel,
                        to.getLabel().getValue());
    }
}

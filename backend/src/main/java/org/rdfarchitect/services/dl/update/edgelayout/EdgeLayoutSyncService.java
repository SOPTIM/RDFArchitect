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

package org.rdfarchitect.services.dl.update.edgelayout;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.jena.graph.Graph;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Resource;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.dl.queries.select.DLObjectFetcher;
import org.rdfarchitect.dl.queries.update.DLUpdates;
import org.rdfarchitect.models.cim.rdf.resources.CIMS;
import org.rdfarchitect.models.cim.relations.model.CIMResourceUtils;
import org.rdfarchitect.rdf.graph.wrapper.DiagramLayoutDelta;
import org.rdfarchitect.services.dl.update.DiagramLayoutServiceUtils;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EdgeLayoutSyncService
        implements SyncEdgeCreatedUseCase, SyncEdgeDeletedUseCase, RenameEdgeLayoutDataUseCase {

    private final DatabasePort databasePort;

    @Override
    public void syncEdgeCreated(
            GraphIdentifier graphIdentifier,
            UUID fromClassUUID,
            UUID identifiedObjectUUID,
            String edgeName) {
        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.WRITE)) {
            var diagramLayout = ctx.getDiagramLayout();
            var packageUUID = resolvePackageUUID(ctx.getRdfGraph(), diagramLayout, fromClassUUID);
            DiagramLayoutServiceUtils.insertDiagramObject(
                    diagramLayout.getDiagramLayoutModel(),
                    packageUUID,
                    edgeName,
                    identifiedObjectUUID);
            ctx.commit("Created edge layout data for \"%s\"".formatted(edgeName));
        }

        syncCrossProfileEdge(graphIdentifier.datasetName(), identifiedObjectUUID, edgeName, true);
    }

    @Override
    public void syncEdgeDeleted(GraphIdentifier graphIdentifier, UUID identifiedObjectUUID) {
        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.WRITE)) {
            var model = ctx.getDiagramLayout().getDiagramLayoutModel();
            for (var diagramObject : DLObjectFetcher.fetchAllDOs(model, identifiedObjectUUID)) {
                DiagramLayoutServiceUtils.deleteEdgeLayoutData(model, diagramObject.getMRID());
            }
            ctx.commit("Deleted edge layout data");
        }

        syncCrossProfileEdge(graphIdentifier.datasetName(), identifiedObjectUUID, null, false);
    }

    @Override
    public void renameEdge(
            GraphIdentifier graphIdentifier, UUID identifiedObjectUUID, String newName) {
        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.WRITE)) {
            var model = ctx.getDiagramLayout().getDiagramLayoutModel();
            for (var diagramObject : DLObjectFetcher.fetchAllDOs(model, identifiedObjectUUID)) {
                DLUpdates.updateDiagramObjectName(model, diagramObject, newName);
            }
            ctx.commit("Renamed edge layout data to \"%s\"".formatted(newName));
        }
    }

    private void syncCrossProfileEdge(
            String datasetName, UUID identifiedObjectUUID, String edgeName, boolean create) {
        // TODO: sobald otherClass/Style existiert, hier über Merge-UUIDs analog
        // migrateLayoutToNewClassUri erst create/delete für das Cross-Profile-Diagramm ergänzen.
        // Fürs Erste bewusst ausgelassen (siehe Chat-Historie: proaktiv nur Package-Diagramm,
        // Cross-Profile ziehen wir hier noch nicht nach, weil merged-UUID-Auflösung fehlt).
    }

    private UUID resolvePackageUUID(Graph graph, DiagramLayoutDelta diagramLayout, UUID classUUID) {
        var classResource = CIMResourceUtils.findResourceForUuid(graph, classUUID);
        var categoryStmt = classResource.getProperty(CIMS.belongsToCategory);
        if (categoryStmt == null) {
            return diagramLayout.getDefaultPackageMRID().getUuid();
        }
        return CIMResourceUtils.findUuidForResource((Resource) categoryStmt.getObject());
    }
}

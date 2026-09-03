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

package org.rdfarchitect.services.dl.update.labellayout;

import lombok.RequiredArgsConstructor;

import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Model;
import org.rdfarchitect.api.dto.dl.LabelPositionDTO;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.dl.data.dto.DiagramObject;
import org.rdfarchitect.dl.data.dto.relations.DiagramObjectStyle;
import org.rdfarchitect.dl.data.dto.relations.MRID;
import org.rdfarchitect.dl.data.dto.relations.XYOffset;
import org.rdfarchitect.dl.queries.select.DLObjectFetcher;
import org.rdfarchitect.dl.queries.select.DLObjectFetcher.LabelKey;
import org.rdfarchitect.dl.queries.update.DLUpdates;
import org.rdfarchitect.services.dl.update.DiagramLayoutServiceUtils;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Stores the manual placement of movable diagram labels. A label is a {@code cim:DiagramObject}
 * whose style says what kind of label it is and which is anchored to the CIM resource whose text it
 * displays. It holds an offset relative to the class it is drawn at rather than a coordinate within
 * the diagram, so a label keeps its placement when its class is moved.
 */
@Service
@RequiredArgsConstructor
public class UpdateLabelLayoutService implements UpdateLabelPositionsUseCase {

    private final DatabasePort databasePort;

    @Override
    public void updateLabelPositions(
            GraphIdentifier graphIdentifier,
            UUID diagramUUID,
            List<LabelPositionDTO> labelPositionDTOList) {
        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.WRITE)) {
            var diagramLayout = ctx.getDiagramLayout();
            var resolvedDiagramUUID =
                    diagramUUID != null
                            ? diagramUUID
                            : diagramLayout.getDefaultPackageMRID().getUuid();

            applyLabelPositions(
                    diagramLayout.getDiagramLayoutModel(),
                    resolvedDiagramUUID,
                    labelPositionDTOList);
            ctx.commit();
        }
    }

    @Override
    public void updateLabelPositions(
            String datasetName, UUID diagramUUID, List<LabelPositionDTO> labelPositionDTOList) {
        applyLabelPositions(
                databasePort.getDatasetDiagramLayout(datasetName).getDiagramLayoutModel(),
                diagramUUID,
                labelPositionDTOList);
    }

    /**
     * Replaces each addressed label wholesale instead of patching its position, which keeps the
     * stored text in sync with the model and makes a reset (an entry without an offset) fall out of
     * the same code path.
     */
    private void applyLabelPositions(
            Model diagramLayoutModel, UUID diagramUUID, List<LabelPositionDTO> labelPositions) {
        if (labelPositions.isEmpty()) {
            return;
        }
        if (DLObjectFetcher.fetchDiagram(diagramLayoutModel, diagramUUID) == null) {
            DiagramLayoutServiceUtils.insertDiagram(diagramLayoutModel, diagramUUID, "");
        }
        var existingLabelsByKey = existingLabelsByKey(diagramLayoutModel, diagramUUID);
        for (var labelPosition : labelPositions) {
            var style = DiagramObjectStyle.byName(labelPosition.getKind());
            if (style == null || style == DiagramObjectStyle.CLASS) {
                continue;
            }
            var labelKey = new LabelKey(labelPosition.getIdentifiedObjectUUID(), style);
            var existing = existingLabelsByKey.get(labelKey);
            if (existing != null) {
                DLUpdates.deleteDiagramObjectCascade(diagramLayoutModel, existing.getMRID());
            }
            if (labelPosition.getXOffset() == null || labelPosition.getYOffset() == null) {
                continue;
            }
            insertLabel(diagramLayoutModel, diagramUUID, labelPosition, style);
        }
    }

    /**
     * Fetches every existing label of the diagram in one query instead of one {@code fetchLabelDO}
     * query per label being applied.
     */
    private Map<LabelKey, DiagramObject> existingLabelsByKey(
            Model diagramLayoutModel, UUID diagramUUID) {
        Map<LabelKey, DiagramObject> byKey = new HashMap<>();
        for (var label : DLObjectFetcher.fetchDiagramLabelDOs(diagramLayoutModel, diagramUUID)) {
            if (label.getStyle() == null) {
                continue;
            }
            byKey.put(
                    new LabelKey(label.getBelongsToIdentifiedObject().getUuid(), label.getStyle()),
                    label);
        }
        return byKey;
    }

    private void insertLabel(
            Model diagramLayoutModel,
            UUID diagramUUID,
            LabelPositionDTO labelPosition,
            DiagramObjectStyle style) {
        DLUpdates.insertDiagramObject(
                diagramLayoutModel,
                DiagramObject.builder()
                        .mRID(new MRID(UUID.randomUUID()))
                        .style(style)
                        .belongsToDiagram(new MRID(diagramUUID))
                        .belongsToIdentifiedObject(
                                new MRID(labelPosition.getIdentifiedObjectUUID()))
                        .offset(
                                new XYOffset(
                                        labelPosition.getXOffset(), labelPosition.getYOffset()))
                        .build());
    }
}

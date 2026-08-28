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

package org.rdfarchitect.services.rendering.svelteflow;

import lombok.experimental.UtilityClass;

import org.rdfarchitect.api.dto.dl.RenderingLayoutData;
import org.rdfarchitect.api.dto.rendering.svelteflow.sub.EdgeLabelDTO;
import org.rdfarchitect.api.dto.rendering.svelteflow.sub.EdgeLabelDTO.Anchor;
import org.rdfarchitect.api.dto.rendering.svelteflow.sub.PositionDTO;
import org.rdfarchitect.dl.data.dto.relations.DiagramObjectStyle;
import org.rdfarchitect.dl.queries.select.DLObjectFetcher.LabelKey;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Assembles the movable labels of an association end. A label is identified by the CIM resource it
 * belongs to plus its kind, which is what lets further kinds (role names, association names) be
 * added here without touching the layout storage.
 */
@UtilityClass
public class SvelteFlowLabels {

    /**
     * Builds the labels of an association edge. A multiplicity is rendered at the class its
     * association points to, so the multiplicity of the association leaving the source class ends
     * up at the target end and vice versa.
     *
     * @param sourceAssociation the UUID of the association end whose multiplicity sits at the
     *     source class
     * @param sourceMultiplicity the multiplicity text at the source class
     * @param targetAssociation the UUID of the association end whose multiplicity sits at the
     *     target class
     * @param targetMultiplicity the multiplicity text at the target class
     * @param layoutData the layout data holding manually placed label positions, may be null
     * @return the labels of the edge
     */
    public List<EdgeLabelDTO> forAssociation(
            UUID sourceAssociation,
            String sourceMultiplicity,
            UUID targetAssociation,
            String targetMultiplicity,
            RenderingLayoutData layoutData) {
        var labels = new ArrayList<EdgeLabelDTO>();
        addMultiplicity(labels, Anchor.SOURCE, sourceAssociation, sourceMultiplicity, layoutData);
        addMultiplicity(labels, Anchor.TARGET, targetAssociation, targetMultiplicity, layoutData);
        return labels;
    }

    private void addMultiplicity(
            List<EdgeLabelDTO> labels,
            Anchor anchor,
            UUID association,
            String multiplicity,
            RenderingLayoutData layoutData) {
        if (association == null || multiplicity == null || multiplicity.isBlank()) {
            return;
        }
        labels.add(
                EdgeLabelDTO.builder()
                        .anchor(anchor)
                        .identifiedObjectUUID(association)
                        .kind(DiagramObjectStyle.MULTIPLICITY.getStyleName())
                        .text(multiplicity)
                        .offset(
                                offsetFor(
                                        layoutData,
                                        new LabelKey(association, DiagramObjectStyle.MULTIPLICITY)))
                        .build());
    }

    private PositionDTO offsetFor(RenderingLayoutData layoutData, LabelKey key) {
        if (layoutData == null || layoutData.getLabelLayoutingData() == null) {
            return null;
        }
        var offset = layoutData.getLabelLayoutingData().get(key);
        if (offset == null) {
            return null;
        }
        return PositionDTO.builder().x(offset.x()).y(offset.y()).build();
    }
}

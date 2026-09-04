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
 * Assembles the movable labels of an association edge, a multiplicity and an association label per
 * end. A label is identified by the CIM resource it belongs to plus its kind, which is what lets
 * further kinds be added here without touching the layout storage.
 */
@UtilityClass
public class SvelteFlowLabels {

    /**
     * One end of an association edge: the association end whose labels are drawn at that class,
     * together with the texts of those labels. Both the multiplicity and the label of an
     * association end are drawn at the class its association points to, so the end at the source
     * class carries the association leaving the target class and vice versa.
     *
     * @param association the UUID of the association end
     * @param multiplicity the multiplicity text, may be null
     * @param label the label of the association end, may be null
     */
    public record AssociationEnd(UUID association, String multiplicity, String label) {}

    /**
     * Builds the labels of an association edge.
     *
     * @param source the association end whose labels sit at the source class
     * @param target the association end whose labels sit at the target class
     * @param layoutData the layout data holding manually placed label positions, may be null
     * @return the labels of the edge
     */
    public List<EdgeLabelDTO> forAssociation(
            AssociationEnd source, AssociationEnd target, RenderingLayoutData layoutData) {
        var labels = new ArrayList<EdgeLabelDTO>();
        addEndLabels(labels, Anchor.SOURCE, source, layoutData);
        addEndLabels(labels, Anchor.TARGET, target, layoutData);
        return labels;
    }

    private void addEndLabels(
            List<EdgeLabelDTO> labels,
            Anchor anchor,
            AssociationEnd end,
            RenderingLayoutData layoutData) {
        if (end == null || end.association() == null) {
            return;
        }
        addLabel(
                labels,
                anchor,
                end.association(),
                DiagramObjectStyle.MULTIPLICITY,
                end.multiplicity(),
                layoutData);
        addLabel(
                labels,
                anchor,
                end.association(),
                DiagramObjectStyle.ASSOCIATION_LABEL,
                end.label(),
                layoutData);
    }

    private void addLabel(
            List<EdgeLabelDTO> labels,
            Anchor anchor,
            UUID association,
            DiagramObjectStyle style,
            String text,
            RenderingLayoutData layoutData) {
        if (text == null || text.isBlank()) {
            return;
        }
        labels.add(
                EdgeLabelDTO.builder()
                        .anchor(anchor)
                        .identifiedObjectUUID(association)
                        .kind(style.getStyleName())
                        .text(text)
                        .offset(offsetFor(layoutData, new LabelKey(association, style)))
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

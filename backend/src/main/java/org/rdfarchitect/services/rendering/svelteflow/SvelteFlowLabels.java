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
import org.rdfarchitect.api.dto.rendering.svelteflow.sub.PositionDTO;
import org.rdfarchitect.dl.data.dto.relations.DiagramObjectStyle;
import org.rdfarchitect.dl.queries.select.DLObjectFetcher.LabelKey;

import java.util.UUID;
import java.util.function.Function;

/**
 * Assembles the movable labels of an association edge, a multiplicity and an association label per
 * end. A label is identified by the CIM resource it belongs to plus its kind, which is what lets
 * further kinds be added here without touching the layout storage.
 */
@UtilityClass
public class SvelteFlowLabels {

    public record AssociationEnd(UUID association, String multiplicity, String label) {}

    /** The four fixed label slots of an association edge. Any field may be {@code null}. */
    public record AssociationLabels(
            EdgeLabelDTO sourceMultiplicityLabel,
            EdgeLabelDTO targetMultiplicityLabel,
            EdgeLabelDTO sourceAssociationLabel,
            EdgeLabelDTO targetAssociationLabel) {}

    public AssociationLabels forAssociation(
            AssociationEnd source, AssociationEnd target, RenderingLayoutData layoutData) {
        return new AssociationLabels(
                labelFor(
                        source,
                        DiagramObjectStyle.MULTIPLICITY,
                        AssociationEnd::multiplicity,
                        layoutData),
                labelFor(
                        target,
                        DiagramObjectStyle.MULTIPLICITY,
                        AssociationEnd::multiplicity,
                        layoutData),
                labelFor(
                        source,
                        DiagramObjectStyle.ASSOCIATION_LABEL,
                        AssociationEnd::label,
                        layoutData),
                labelFor(
                        target,
                        DiagramObjectStyle.ASSOCIATION_LABEL,
                        AssociationEnd::label,
                        layoutData));
    }

    private EdgeLabelDTO labelFor(
            AssociationEnd end,
            DiagramObjectStyle style,
            Function<AssociationEnd, String> textExtractor,
            RenderingLayoutData layoutData) {
        if (end == null || end.association() == null) {
            return null;
        }
        var text = textExtractor.apply(end);
        if (text == null || text.isBlank()) {
            return null;
        }
        return EdgeLabelDTO.builder()
                .identifiedObjectUUID(end.association())
                .text(text)
                .position(positionFor(layoutData, new LabelKey(end.association(), style)))
                .build();
    }

    private PositionDTO positionFor(RenderingLayoutData layoutData, LabelKey key) {
        if (layoutData == null || layoutData.getLabelLayoutingData() == null) {
            return null;
        }
        var position = layoutData.getLabelLayoutingData().get(key);
        if (position == null) {
            return null;
        }
        return PositionDTO.builder()
                .x(position.getPosition().getX())
                .y(position.getPosition().getY())
                .build();
    }
}

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
import org.rdfarchitect.api.dto.rendering.svelteflow.sub.BendPointDTO;
import org.rdfarchitect.api.dto.rendering.svelteflow.sub.PositionDTO;
import org.rdfarchitect.dl.data.dto.relations.DiagramObjectStyle;
import org.rdfarchitect.dl.data.dto.relations.EdgeKey;
import org.rdfarchitect.dl.queries.select.DLObjectFetcher.EdgePoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Assembles the stored points of an edge, its bend points and the end points at the borders of its
 * classes, for inheritance and association edges alike.
 */
@UtilityClass
public class SvelteFlowEdgePoints {

    private static final String SOURCE_SIDE = "source";
    private static final String TARGET_SIDE = "target";

    /**
     * The stored points of an edge in the order the edge is rendered, from its source to its target
     * class. The id of a point is its mRID, which is what lets the frontend send the point back to
     * update it. An end point carries the side of the class it is glued to.
     *
     * @param layoutData the layout data of the diagram, may be null
     * @param style {@link DiagramObjectStyle#INHERITANCE} or {@link DiagramObjectStyle#ASSOCIATION}
     * @param sourceObject the sub class, or the association end the edge is rendered from
     * @param targetObject the super class, or the inverse association end
     * @param sourceClass the class the edge starts at
     * @param targetClass the class the edge ends at
     * @return the points of the edge, empty if it has none stored in the diagram
     */
    public List<BendPointDTO> forEdge(
            RenderingLayoutData layoutData,
            DiagramObjectStyle style,
            UUID sourceObject,
            UUID targetObject,
            UUID sourceClass,
            UUID targetClass) {
        if (layoutData == null || layoutData.getEdgeLayoutingData() == null) {
            return List.of();
        }
        var storedPoints =
                layoutData
                        .getEdgeLayoutingData()
                        .get(EdgeKey.of(style, sourceObject, targetObject));
        if (storedPoints == null) {
            return List.of();
        }

        var points = new ArrayList<>(storedPoints.points());
        if (!sourceObject.equals(storedPoints.identifiedObject().getUuid())) {
            Collections.reverse(points);
        }
        var lastIndex = points.size() - 1;
        var bendPoints = new ArrayList<BendPointDTO>();
        for (var index = 0; index < points.size(); index++) {
            var point = points.get(index);
            bendPoints.add(
                    BendPointDTO.builder()
                            .id(point.point().getMRID().getUuid().toString())
                            .position(
                                    PositionDTO.builder()
                                            .x(point.point().getPosition().getX())
                                            .y(point.point().getPosition().getY())
                                            .build())
                            .side(sideOf(point, index, lastIndex, sourceClass, targetClass))
                            .build());
        }
        return bendPoints;
    }

    /**
     * The side of an end point, taken from the class it is glued to. Where that is ambiguous, for
     * an edge from a class to itself or a glue point whose class is unknown, the first point is
     * taken as the source end point and the last one as the target end point.
     *
     * @return the side, or null for a bend point
     */
    private String sideOf(
            EdgePoint point, int index, int lastIndex, UUID sourceClass, UUID targetClass) {
        if (point.point().getBelongsToGluePoint() == null) {
            return null;
        }
        var gluedClass = point.gluedClass() != null ? point.gluedClass().getUuid() : null;
        if (gluedClass != null && !sourceClass.equals(targetClass)) {
            if (gluedClass.equals(sourceClass)) {
                return SOURCE_SIDE;
            }
            if (gluedClass.equals(targetClass)) {
                return TARGET_SIDE;
            }
        }
        if (index == 0) {
            return SOURCE_SIDE;
        }
        if (index == lastIndex) {
            return TARGET_SIDE;
        }
        return null;
    }
}

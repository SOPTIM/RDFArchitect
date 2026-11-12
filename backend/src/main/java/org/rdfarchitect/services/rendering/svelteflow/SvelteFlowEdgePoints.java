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

import org.rdfarchitect.api.dto.dl.EdgePointDTO;
import org.rdfarchitect.api.dto.dl.EdgePointDTO.Side;
import org.rdfarchitect.api.dto.dl.RenderingLayoutData;
import org.rdfarchitect.dl.data.dto.relations.DiagramObjectStyle;
import org.rdfarchitect.dl.data.dto.relations.EdgeKey;
import org.rdfarchitect.dl.queries.select.DLObjectFetcher.EdgePoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Assembles the stored edge points of an edge, its bend points and the docking points at the
 * borders of its classes, for inheritance and association edges alike.
 */
@UtilityClass
public class SvelteFlowEdgePoints {

    /**
     * The stored points of an edge in the order the edge is rendered, from its source to its target
     * class. The id of a point is its mRID, which is what lets the frontend send the point back to
     * update it. A docking point carries the side of the class it is pinned to.
     *
     * @param layoutData the layout data of the diagram, may be null
     * @param style {@link DiagramObjectStyle#INHERITANCE} or {@link DiagramObjectStyle#ASSOCIATION}
     * @param sourceObject the sub class, or the association end the edge is rendered from
     * @param targetObject the super class, or the inverse association end
     * @param sourceClass the class the edge starts at
     * @param targetClass the class the edge ends at
     * @return the points of the edge, empty if it has none stored in the diagram
     */
    public List<EdgePointDTO> forEdge(
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
        var edgePoints = new ArrayList<EdgePointDTO>();
        for (var index = 0; index < points.size(); index++) {
            var point = points.get(index);
            var position = point.point().getPosition();
            edgePoints.add(
                    new EdgePointDTO(
                            point.point().getMRID().getUuid().toString(),
                            position.getX(),
                            position.getY(),
                            sideOf(point, index, lastIndex, sourceClass, targetClass)));
        }
        return edgePoints;
    }

    /**
     * The side of a docking point, taken from the class it is glued to. Where that is ambiguous,
     * for an edge from a class to itself or a glue point whose class is unknown, the first point is
     * taken as the source docking point and the last one as the target docking point.
     *
     * @return the side, or null for a bend point
     */
    private Side sideOf(
            EdgePoint point, int index, int lastIndex, UUID sourceClass, UUID targetClass) {
        if (point.point().getBelongsToGluePoint() == null) {
            return null;
        }
        var gluedClass = point.gluedClass() != null ? point.gluedClass().getUuid() : null;
        if (gluedClass != null && !sourceClass.equals(targetClass)) {
            if (gluedClass.equals(sourceClass)) {
                return Side.SOURCE;
            }
            if (gluedClass.equals(targetClass)) {
                return Side.TARGET;
            }
        }
        if (index == 0) {
            return Side.SOURCE;
        }
        if (index == lastIndex) {
            return Side.TARGET;
        }
        return null;
    }
}

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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.rdfarchitect.api.dto.dl.EdgePointDTO;
import org.rdfarchitect.api.dto.dl.EdgePointDTO.Side;
import org.rdfarchitect.api.dto.dl.RenderingLayoutData;
import org.rdfarchitect.dl.data.dto.DiagramObjectPoint;
import org.rdfarchitect.dl.data.dto.relations.DiagramObjectStyle;
import org.rdfarchitect.dl.data.dto.relations.EdgeKey;
import org.rdfarchitect.dl.data.dto.relations.MRID;
import org.rdfarchitect.dl.data.dto.relations.XYZPosition;
import org.rdfarchitect.dl.queries.select.DLObjectFetcher.EdgePoint;
import org.rdfarchitect.dl.queries.select.DLObjectFetcher.EdgePoints;

import java.util.List;
import java.util.Map;
import java.util.UUID;

class SvelteFlowEdgePointsTest {

    private static final UUID SUB = UUID.randomUUID();
    private static final UUID SUPER = UUID.randomUUID();
    private static final UUID END = UUID.randomUUID();
    private static final UUID INVERSE_END = UUID.randomUUID();
    private static final MRID GLUE_POINT = new MRID(UUID.randomUUID());

    @Test
    void noLayoutData_orNoStoredPoints_giveAnEmptyList() {
        assertThat(inheritancePoints(null)).isEmpty();
        assertThat(inheritancePoints(RenderingLayoutData.builder().build())).isEmpty();
        assertThat(inheritancePoints(layoutData(Map.of()))).isEmpty();
    }

    @Test
    void storedPoints_areRenderedInOrderWithTheirMRIDsAndSides() {
        var source = dockingPoint(0, 0, SUB);
        var bend = bendPoint(50, 50);
        var target = dockingPoint(100, 100, SUPER);
        var layoutData =
                layoutData(
                        Map.of(
                                EdgeKey.inheritance(SUB, SUPER),
                                new EdgePoints(new MRID(SUB), List.of(source, bend, target))));

        var points = inheritancePoints(layoutData);

        assertThat(points)
                .extracting(EdgePointDTO::getId)
                .containsExactly(idOf(source), idOf(bend), idOf(target));
        assertThat(points)
                .extracting(EdgePointDTO::getSide)
                .containsExactly(Side.SOURCE, null, Side.TARGET);
        assertThat(points).extracting(EdgePointDTO::getX).containsExactly(0D, 50D, 100D);
    }

    @Test
    void anEdgeRenderedFromTheOtherSide_getsItsPointsReversed_andTheSidesOfItsOwnClasses() {
        var atTarget = dockingPoint(0, 0, SUPER);
        var bend = bendPoint(50, 50);
        var atSub = dockingPoint(100, 100, SUB);
        var layoutData =
                layoutData(
                        Map.of(
                                EdgeKey.association(END, INVERSE_END),
                                new EdgePoints(
                                        new MRID(INVERSE_END), List.of(atTarget, bend, atSub))));

        var points =
                SvelteFlowEdgePoints.forEdge(
                        layoutData, DiagramObjectStyle.ASSOCIATION, END, INVERSE_END, SUB, SUPER);

        assertThat(points)
                .extracting(EdgePointDTO::getId)
                .containsExactly(idOf(atSub), idOf(bend), idOf(atTarget));
        assertThat(points)
                .extracting(EdgePointDTO::getSide)
                .containsExactly(Side.SOURCE, null, Side.TARGET);
    }

    @Test
    void aLoneDockingPoint_getsTheSideOfTheClassItIsGluedTo() {
        var bend = bendPoint(50, 50);
        var target = dockingPoint(100, 100, SUPER);
        var layoutData =
                layoutData(
                        Map.of(
                                EdgeKey.inheritance(SUB, SUPER),
                                new EdgePoints(new MRID(SUB), List.of(target))));

        assertThat(inheritancePoints(layoutData))
                .extracting(EdgePointDTO::getSide)
                .containsExactly(Side.TARGET);
        assertThat(
                        inheritancePoints(
                                layoutData(
                                        Map.of(
                                                EdgeKey.inheritance(SUB, SUPER),
                                                new EdgePoints(
                                                        new MRID(SUB), List.of(bend, target))))))
                .extracting(EdgePointDTO::getSide)
                .containsExactly(null, Side.TARGET);
    }

    @Test
    void dockingPointsOfAnEdgeFromAClassToItself_takeTheirSideFromTheirPosition() {
        var first = dockingPoint(0, 0, SUB);
        var bend = bendPoint(50, 50);
        var last = dockingPoint(100, 100, SUB);
        var layoutData =
                layoutData(
                        Map.of(
                                EdgeKey.association(END, INVERSE_END),
                                new EdgePoints(new MRID(END), List.of(first, bend, last))));

        var points =
                SvelteFlowEdgePoints.forEdge(
                        layoutData, DiagramObjectStyle.ASSOCIATION, END, INVERSE_END, SUB, SUB);

        assertThat(points)
                .extracting(EdgePointDTO::getSide)
                .containsExactly(Side.SOURCE, null, Side.TARGET);
    }

    @Test
    void dockingPointsGluedToAnUnknownClass_takeTheirSideFromTheirPosition() {
        var first = dockingPoint(0, 0, null);
        var last = dockingPoint(100, 100, null);
        var layoutData =
                layoutData(
                        Map.of(
                                EdgeKey.inheritance(SUB, SUPER),
                                new EdgePoints(new MRID(SUB), List.of(first, last))));

        assertThat(inheritancePoints(layoutData))
                .extracting(EdgePointDTO::getSide)
                .containsExactly(Side.SOURCE, Side.TARGET);
    }

    private static List<EdgePointDTO> inheritancePoints(RenderingLayoutData layoutData) {
        return SvelteFlowEdgePoints.forEdge(
                layoutData, DiagramObjectStyle.INHERITANCE, SUB, SUPER, SUB, SUPER);
    }

    private static RenderingLayoutData layoutData(Map<EdgeKey, EdgePoints> edgePoints) {
        return RenderingLayoutData.builder().edgeLayoutingData(edgePoints).build();
    }

    private static EdgePoint dockingPoint(double x, double y, UUID gluedClass) {
        return new EdgePoint(
                point(x, y).belongsToGluePoint(GLUE_POINT).build(),
                gluedClass != null ? new MRID(gluedClass) : null);
    }

    private static EdgePoint bendPoint(double x, double y) {
        return new EdgePoint(point(x, y).build(), null);
    }

    private static DiagramObjectPoint.DiagramObjectPointBuilder point(double x, double y) {
        return DiagramObjectPoint.builder()
                .mRID(new MRID(UUID.randomUUID()))
                .position(new XYZPosition(x, y, 0));
    }

    private static String idOf(EdgePoint point) {
        return point.point().getMRID().getUuid().toString();
    }
}

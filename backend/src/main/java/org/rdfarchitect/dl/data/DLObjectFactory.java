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

package org.rdfarchitect.dl.data;

import lombok.experimental.UtilityClass;

import org.apache.jena.query.QuerySolution;
import org.apache.jena.rdf.model.Resource;
import org.rdfarchitect.dl.data.dto.Diagram;
import org.rdfarchitect.dl.data.dto.DiagramObject;
import org.rdfarchitect.dl.data.dto.DiagramObjectGluePoint;
import org.rdfarchitect.dl.data.dto.DiagramObjectPoint;
import org.rdfarchitect.dl.data.dto.relations.DiagramObjectStyle;
import org.rdfarchitect.dl.data.dto.relations.MRID;
import org.rdfarchitect.dl.data.dto.relations.OrientationKind;
import org.rdfarchitect.dl.data.dto.relations.XYZPosition;
import org.rdfarchitect.dl.queries.DLQuerySolutionParser;
import org.rdfarchitect.dl.queries.DLQueryVars;
import org.rdfarchitect.dl.rdf.resources.CIM;
import org.rdfarchitect.dl.rdf.resources.DL;

/**
 * Factory class that provides static methods for creating CGMES DiagramLayout Profile objects from
 * queries
 */
@UtilityClass
public class DLObjectFactory {

    /**
     * Creates a {@link Diagram} from a given query solution.
     *
     * @param querySolution {@link QuerySolution} with results bound to variables from {@link
     *     DLQueryVars}.
     * @return {@link Diagram}
     */
    public static Diagram createDiagram(QuerySolution querySolution) {
        var parser = new DLQuerySolutionParser(querySolution);
        return Diagram.builder()
                .mRID(parser.getMRID(DLQueryVars.DIAGRAM_MRID))
                .name(parser.getName(DLQueryVars.DIAGRAM_NAME))
                .orientation(OrientationKind.NEGATIVE)
                .build();
    }

    /**
     * Creates a {@link DiagramObject} from a given query solution.
     *
     * @param querySolution {@link QuerySolution} with results bound to variables from {@link
     *     DLQueryVars}.
     * @return {@link DiagramObject}
     */
    public static DiagramObject createDiagramObject(QuerySolution querySolution) {
        var parser = new DLQuerySolutionParser(querySolution);
        return DiagramObject.builder()
                .mRID(parser.getMRID(DLQueryVars.DO_MRID))
                .name(parser.getName(DLQueryVars.DO_NAME))
                .belongsToDiagramObjectStyle(
                        DiagramObjectStyle.byName(parser.getName(DLQueryVars.STYLE_NAME)))
                .belongsToDiagram(parser.getMRID(DLQueryVars.DIAGRAM_MRID))
                .belongsToIdentifiedObject(parser.getMRID(DLQueryVars.IO_MRID))
                .otherClass(parser.getMRID(DLQueryVars.OTHER_CLASS_MRID))
                .build();
    }

    /**
     * Creates a {@link DiagramObject} from the statements of its resource, like {@link
     * #createDiagramObject(QuerySolution)} for a query that selects the mRID, the name and the
     * style name of a diagram object.
     *
     * @param diagramObject the resource of the diagram object, with a name and a style
     * @param diagram the diagram the object belongs to
     * @param identifiedObject the identified object the object belongs to
     * @return {@link DiagramObject}
     */
    public static DiagramObject createDiagramObject(
            Resource diagramObject, MRID diagram, MRID identifiedObject) {
        var styleName =
                diagramObject
                        .getPropertyResourceValue(DL.belongsToDiagramObjectStyle)
                        .getProperty(CIM.ioName);
        return DiagramObject.builder()
                .mRID(new MRID(DLUtils.extractUUIDFromMRID(diagramObject.getURI())))
                .name(diagramObject.getProperty(CIM.ioName).getString())
                .belongsToDiagramObjectStyle(
                        DiagramObjectStyle.byName(styleName != null ? styleName.getString() : null))
                .belongsToDiagram(diagram)
                .belongsToIdentifiedObject(identifiedObject)
                .build();
    }

    /**
     * Creates a {@link DiagramObjectPoint} from a given query solution.
     *
     * @param querySolution {@link QuerySolution} with results bound to variables from {@link
     *     DLQueryVars}.
     * @return {@link DiagramObjectPoint}
     */
    public static DiagramObjectPoint createDiagramObjectPoint(QuerySolution querySolution) {
        var parser = new DLQuerySolutionParser(querySolution);
        return DiagramObjectPoint.builder()
                .mRID(parser.getMRID(DLQueryVars.DOP_MRID))
                .position(parser.getXYZPosition())
                .belongsToDiagramObject(parser.getMRID(DLQueryVars.DO_MRID))
                .sequenceNumber(parser.getSequenceNumber())
                .belongsToGluePoint(parser.getMRID(DLQueryVars.GLUE_POINT_MRID))
                .build();
    }

    /**
     * Creates a {@link DiagramObjectPoint} from the statements of its resource, with the same
     * defaults as {@link #createDiagramObjectPoint(QuerySolution)}: a missing z position is 0, a
     * missing sequence number or glue point is null.
     *
     * @param point the resource of the point, with an x and a y position
     * @param diagramObject the diagram object the point belongs to
     * @return {@link DiagramObjectPoint}
     */
    public static DiagramObjectPoint createDiagramObjectPoint(Resource point, MRID diagramObject) {
        var zPosition = point.getProperty(DL.zPosition);
        var sequenceNumber = point.getProperty(DL.sequenceNumber);
        var gluePoint = point.getPropertyResourceValue(DL.belongsToGluePoint);
        return DiagramObjectPoint.builder()
                .mRID(new MRID(DLUtils.extractUUIDFromMRID(point.getURI())))
                .position(
                        new XYZPosition(
                                point.getProperty(DL.xPosition).getFloat(),
                                point.getProperty(DL.yPosition).getFloat(),
                                zPosition != null ? zPosition.getInt() : 0))
                .belongsToDiagramObject(diagramObject)
                .sequenceNumber(sequenceNumber != null ? sequenceNumber.getInt() : null)
                .belongsToGluePoint(
                        gluePoint != null
                                ? new MRID(DLUtils.extractUUIDFromMRID(gluePoint.getURI()))
                                : null)
                .build();
    }

    /**
     * Creates a {@link DiagramObjectGluePoint} from a given query solution.
     *
     * @param querySolution {@link QuerySolution} with results bound to variables from {@link
     *     DLQueryVars}.
     * @return {@link DiagramObjectGluePoint}
     */
    public static DiagramObjectGluePoint createDiagramObjectGluePoint(QuerySolution querySolution) {
        var parser = new DLQuerySolutionParser(querySolution);
        return DiagramObjectGluePoint.builder()
                .mRID(parser.getMRID(DLQueryVars.GLUE_POINT_MRID))
                .build();
    }
}

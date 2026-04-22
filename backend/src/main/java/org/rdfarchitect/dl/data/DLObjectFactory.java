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
import org.rdfarchitect.dl.data.dto.Diagram;
import org.rdfarchitect.dl.data.dto.DiagramObject;
import org.rdfarchitect.dl.data.dto.DiagramObjectPoint;
import org.rdfarchitect.dl.data.dto.relations.OrientationKind;
import org.rdfarchitect.dl.queries.DLQuerySolutionParser;
import org.rdfarchitect.dl.queries.DLQueryVars;

/**
 * Factory class that provides static methods for creating CGMES DiagramLayout Profile objects from queries
 */
@UtilityClass
public class DLObjectFactory {

    /**
     * Creates a {@link Diagram} from a given query solution.
     *
     * @param querySolution {@link QuerySolution} with results bound to variables from {@link DLQueryVars}.
     *
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
     * @param querySolution {@link QuerySolution} with results bound to variables from {@link DLQueryVars}.
     *
     * @return {@link DiagramObject}
     */
    public static DiagramObject createDiagramObject(QuerySolution querySolution) {
        var parser = new DLQuerySolutionParser(querySolution);
        return DiagramObject.builder()
                            .mRID(parser.getMRID(DLQueryVars.DO_MRID))
                            .name(parser.getName(DLQueryVars.DO_NAME))
                            .belongsToDiagram(parser.getMRID(DLQueryVars.DIAGRAM_MRID))
                            .belongsToIdentifiedObject(parser.getMRID(DLQueryVars.IO_MRID))
                            .build();
    }

    /**
     * Creates a {@link DiagramObjectPoint} from a given query solution.
     *
     * @param querySolution {@link QuerySolution} with results bound to variables from {@link DLQueryVars}.
     *
     * @return {@link DiagramObjectPoint}
     */
    public static DiagramObjectPoint createDiagramObjectPoint(QuerySolution querySolution) {
        var parser = new DLQuerySolutionParser(querySolution);
        return DiagramObjectPoint.builder()
                                 .mRID(parser.getMRID(DLQueryVars.DOP_MRID))
                                 .position(parser.getXYZPosition())
                                 .belongsToDiagramObject(parser.getMRID(DLQueryVars.DO_MRID))
                                 .build();
    }
}

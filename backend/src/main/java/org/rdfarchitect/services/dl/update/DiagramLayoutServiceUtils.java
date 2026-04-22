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

package org.rdfarchitect.services.dl.update;

import lombok.experimental.UtilityClass;
import org.apache.jena.rdf.model.Model;
import org.rdfarchitect.dl.data.dto.Diagram;
import org.rdfarchitect.dl.data.dto.DiagramObject;
import org.rdfarchitect.dl.data.dto.DiagramObjectPoint;
import org.rdfarchitect.dl.data.dto.relations.MRID;
import org.rdfarchitect.dl.data.dto.relations.OrientationKind;
import org.rdfarchitect.dl.data.dto.relations.XYZPosition;
import org.rdfarchitect.dl.queries.update.DLUpdates;
import org.rdfarchitect.dl.rdf.resources.DL;

import java.util.UUID;

/**
 * Utility class with helper methods for the DiagramLayout update services
 */
@UtilityClass
public class DiagramLayoutServiceUtils {

    /**
     * Helper method for creating and inserting a {@link Diagram} into a given model.
     *
     * @param diagramLayoutModel the model into which the diagram is inserted
     * @param packageUUID        the UUID of the package used for the diagram's mRID
     * @param packageName        the name of the package used for the diagram
     *
     */
    public void insertDiagram(Model diagramLayoutModel, UUID packageUUID, String packageName) {
        var diagram = Diagram.builder()
                             .mRID(new MRID(packageUUID))
                             .name(packageName)
                             .orientation(OrientationKind.NEGATIVE)
                             .build();
        DLUpdates.insertDiagram(diagramLayoutModel, diagram);
    }

    /**
     * Helper method for creating and inserting a {@link DiagramObject} into a given model.
     *
     * @param diagramLayoutModel the model into which the diagram object is inserted
     * @param packageUUID        the UUID of the package whose diagram the object belongs to
     * @param className          the name of the class represented by the diagram object
     * @param classUUID          the UUID of the class represented by the diagram object
     *
     * @return the mRID of the created diagram object, used for creating diagram object points
     *
     */
    public MRID insertDiagramObject(Model diagramLayoutModel, UUID packageUUID, String className, UUID classUUID) {
        var diagramObjectMRID = new MRID(UUID.randomUUID());

        var diagramObject = DiagramObject.builder()
                                         .mRID(diagramObjectMRID)
                                         .name(className)
                                         .belongsToDiagram(new MRID(packageUUID))
                                         .belongsToIdentifiedObject(new MRID(classUUID))
                                         .build();
        DLUpdates.insertDiagramObject(diagramLayoutModel, diagramObject);

        return diagramObjectMRID;
    }

    /**
     * Helper method for creating and inserting a {@link DiagramObjectPoint} into a given model.
     *
     * @param diagramLayoutModel the model into which the diagram object point is inserted
     * @param diagramObjectMRID  the mRID of the diagram object the point belongs to
     */
    public void insertDiagramObjectPoint(Model diagramLayoutModel, MRID diagramObjectMRID) {
        var maxZPosition = diagramLayoutModel.listObjectsOfProperty(DL.zPosition)
                                             .toSet()
                                             .stream()
                                             .max((o1, o2) -> {
                                                  var z1 = o1.asLiteral().getInt();
                                                  var z2 = o2.asLiteral().getInt();
                                                  return Integer.compare(z1, z2);
                                             }).map(o -> o.asLiteral().getInt())
                                             .orElse(0);
        var diagramObjectPoint = DiagramObjectPoint.builder()
                                                   .mRID(new MRID(UUID.randomUUID()))
                                                   .position(new XYZPosition(0, 0, maxZPosition + 1))
                                                   .belongsToDiagramObject(diagramObjectMRID)
                                                   .build();
        DLUpdates.insertDiagramObjectPoint(diagramLayoutModel, diagramObjectPoint);
    }
}

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

package org.rdfarchitect.dl.queries.update;

import lombok.experimental.UtilityClass;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.vocabulary.RDF;
import org.rdfarchitect.dl.data.dto.Diagram;
import org.rdfarchitect.dl.data.dto.DiagramObject;
import org.rdfarchitect.dl.data.dto.DiagramObjectPoint;
import org.rdfarchitect.dl.data.dto.relations.MRID;
import org.rdfarchitect.dl.queries.select.DLObjectFetcher;
import org.rdfarchitect.dl.rdf.resources.CIM;
import org.rdfarchitect.dl.rdf.resources.DL;

@UtilityClass
public class DLUpdates {

    public void insertDiagram(Model model, Diagram diagram) {
        var newDiagram = model.createResource(diagram.getMRID().getFullMRID());

        newDiagram.addProperty(RDF.type, DL.diagramType);
        newDiagram.addProperty(CIM.ioName, ResourceFactory.createPlainLiteral(diagram.getName()));
        newDiagram.addProperty(DL.orientation, DL.negativeOrientation);

        model.add(newDiagram.listProperties());
    }

    public void replaceDiagram(Model model, MRID diagramMRID, Diagram diagram) {
        deleteDiagram(model, diagramMRID);
        insertDiagram(model, diagram);
    }

    public void deleteDiagram(Model model, MRID diagramMRID) {
        deleteBase(model, diagramMRID);
    }

    public void insertDiagramObject(Model model, DiagramObject diagramObject) {
        var newDiagramObject = model.createResource(diagramObject.getMRID().getFullMRID());

        newDiagramObject.addProperty(RDF.type, DL.diagramObjectType);
        newDiagramObject.addProperty(CIM.ioName, ResourceFactory.createPlainLiteral(diagramObject.getName()));
        newDiagramObject.addProperty(DL.belongsToDiagram, ResourceFactory.createResource(diagramObject.getBelongsToDiagram().getFullMRID()));
        newDiagramObject.addProperty(DL.belongsToIdentifiedObject, ResourceFactory.createResource(diagramObject.getBelongsToIdentifiedObject().getFullMRID()));

        model.add(newDiagramObject.listProperties());
    }

    public void updateDiagramObjectName(Model model, DiagramObject diagramObject, String name) {
        var resource = model.getResource(diagramObject.getMRID().getFullMRID());

        model.removeAll(resource, CIM.ioName, null);

        resource.addProperty(CIM.ioName, name);
    }

    public void deleteDiagramObjectCascade(Model model, MRID doMRID) {
        DiagramObjectPoint dop = DLObjectFetcher.fetchDOPForDO(model, doMRID);
        deleteDiagramObject(model, doMRID);
        deleteDiagramObjectPoint(model, dop.getMRID());
    }

    public void deleteDiagramObject(Model model, MRID doMRID) {
        deleteBase(model, doMRID);
    }

    public void insertDiagramObjectPoint(Model model, DiagramObjectPoint diagramObjectPoint) {
        var newDiagramObjectPoint = model.createResource(diagramObjectPoint.getMRID().getFullMRID());

        newDiagramObjectPoint.addProperty(RDF.type, DL.diagramObjectPointType);
        newDiagramObjectPoint.addProperty(DL.xPosition, ResourceFactory.createPlainLiteral(String.valueOf(diagramObjectPoint.getPosition().getX())));
        newDiagramObjectPoint.addProperty(DL.yPosition, ResourceFactory.createPlainLiteral(String.valueOf(diagramObjectPoint.getPosition().getY())));
        newDiagramObjectPoint.addProperty(DL.zPosition, ResourceFactory.createPlainLiteral(String.valueOf(diagramObjectPoint.getPosition().getZ())));
        newDiagramObjectPoint.addProperty(DL.belongsToDiagramObject, ResourceFactory.createResource(diagramObjectPoint.getBelongsToDiagramObject().getFullMRID()));

        model.add(newDiagramObjectPoint.listProperties());
    }

    public void deleteDiagramObjectPoint(Model model, MRID dopMRID) {
        deleteBase(model, dopMRID);
    }

    /**
     * Helper method for deleting all triples with the provided mRID as subject from the given model.
     *
     * @param model the model from which the triples are removed
     * @param mRID  the mRID used as the subject of the triples to be deleted
     */

    private void deleteBase(Model model, MRID mRID) {
        var resource = model.getResource(mRID.getFullMRID());
        model.removeAll(resource, null, null);
    }
}

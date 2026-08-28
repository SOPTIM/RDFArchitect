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
import org.rdfarchitect.dl.data.dto.relations.DiagramObjectStyle;
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

    /**
     * Inserts a diagram object together with the style that says what it stands for. Objects
     * without a name or without an offset are written without those triples, which is what keeps
     * the different kinds of diagram object queryable apart.
     *
     * @param model the model into which the diagram object is inserted
     * @param diagramObject the diagram object to insert
     */
    public void insertDiagramObject(Model model, DiagramObject diagramObject) {
        insertDiagramObjectStyle(model, diagramObject.getStyle());

        var newDiagramObject = model.createResource(diagramObject.getMRID().getFullMRID());

        newDiagramObject.addProperty(RDF.type, DL.diagramObjectType);
        newDiagramObject.addProperty(
                DL.diagramObjectStyle,
                ResourceFactory.createResource(diagramObject.getStyle().getMRID().getFullMRID()));
        newDiagramObject.addProperty(
                DL.belongsToDiagram,
                ResourceFactory.createResource(diagramObject.getBelongsToDiagram().getFullMRID()));
        newDiagramObject.addProperty(
                DL.belongsToIdentifiedObject,
                ResourceFactory.createResource(
                        diagramObject.getBelongsToIdentifiedObject().getFullMRID()));

        if (diagramObject.getName() != null) {
            newDiagramObject.addProperty(
                    CIM.ioName, ResourceFactory.createPlainLiteral(diagramObject.getName()));
        }
        if (diagramObject.getOffset() != null) {
            newDiagramObject.addProperty(
                    DL.offsetX,
                    ResourceFactory.createPlainLiteral(
                            String.valueOf(diagramObject.getOffset().x())));
            newDiagramObject.addProperty(
                    DL.offsetY,
                    ResourceFactory.createPlainLiteral(
                            String.valueOf(diagramObject.getOffset().y())));
        }

        model.add(newDiagramObject.listProperties());
    }

    /**
     * Inserts a style unless the model already holds it. Styles are shared by every diagram object
     * of their kind and are addressed by an mRID derived from their name, so inserting one twice
     * would only repeat the triples it already has.
     *
     * @param model the model into which the style is inserted
     * @param style the style to insert
     */
    public void insertDiagramObjectStyle(Model model, DiagramObjectStyle style) {
        var styleResource = model.getResource(style.getMRID().getFullMRID());
        if (model.contains(styleResource, RDF.type, DL.diagramObjectStyleType)) {
            return;
        }
        styleResource.addProperty(RDF.type, DL.diagramObjectStyleType);
        styleResource.addProperty(
                CIM.ioName, ResourceFactory.createPlainLiteral(style.getStyleName()));

        model.add(styleResource.listProperties());
    }

    public void updateDiagramObjectName(Model model, DiagramObject diagramObject, String name) {
        var resource = model.getResource(diagramObject.getMRID().getFullMRID());

        model.removeAll(resource, CIM.ioName, null);

        resource.addProperty(CIM.ioName, name);
    }

    /**
     * Deletes a diagram object together with its point, if it has one. Objects that are placed by
     * an offset carry no point, so the point is optional here.
     *
     * @param model the model from which the diagram object is removed
     * @param doMRID the mRID of the diagram object to remove
     */
    public void deleteDiagramObjectCascade(Model model, MRID doMRID) {
        DiagramObjectPoint dop = DLObjectFetcher.fetchDOPForDO(model, doMRID);
        deleteDiagramObject(model, doMRID);
        if (dop != null) {
            deleteDiagramObjectPoint(model, dop.getMRID());
        }
    }

    public void deleteDiagramObject(Model model, MRID doMRID) {
        deleteBase(model, doMRID);
    }

    public void insertDiagramObjectPoint(Model model, DiagramObjectPoint diagramObjectPoint) {
        var newDiagramObjectPoint =
                model.createResource(diagramObjectPoint.getMRID().getFullMRID());

        newDiagramObjectPoint.addProperty(RDF.type, DL.diagramObjectPointType);
        newDiagramObjectPoint.addProperty(
                DL.xPosition,
                ResourceFactory.createPlainLiteral(
                        String.valueOf(diagramObjectPoint.getPosition().getX())));
        newDiagramObjectPoint.addProperty(
                DL.yPosition,
                ResourceFactory.createPlainLiteral(
                        String.valueOf(diagramObjectPoint.getPosition().getY())));
        newDiagramObjectPoint.addProperty(
                DL.zPosition,
                ResourceFactory.createPlainLiteral(
                        String.valueOf(diagramObjectPoint.getPosition().getZ())));
        newDiagramObjectPoint.addProperty(
                DL.belongsToDiagramObject,
                ResourceFactory.createResource(
                        diagramObjectPoint.getBelongsToDiagramObject().getFullMRID()));

        model.add(newDiagramObjectPoint.listProperties());
    }

    public void deleteDiagramObjectPoint(Model model, MRID dopMRID) {
        deleteBase(model, dopMRID);
    }

    /**
     * Helper method for deleting all triples with the provided mRID as subject from the given
     * model.
     *
     * @param model the model from which the triples are removed
     * @param mRID the mRID used as the subject of the triples to be deleted
     */
    private void deleteBase(Model model, MRID mRID) {
        var resource = model.getResource(mRID.getFullMRID());
        model.removeAll(resource, null, null);
    }
}

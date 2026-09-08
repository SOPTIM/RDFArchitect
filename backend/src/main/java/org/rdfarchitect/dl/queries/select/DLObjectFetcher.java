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

package org.rdfarchitect.dl.queries.select;

import lombok.experimental.UtilityClass;

import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.rdf.model.Model;
import org.rdfarchitect.dl.data.DLObjectFactory;
import org.rdfarchitect.dl.data.dto.Diagram;
import org.rdfarchitect.dl.data.dto.DiagramObject;
import org.rdfarchitect.dl.data.dto.DiagramObjectPoint;
import org.rdfarchitect.dl.data.dto.relations.DiagramObjectStyle;
import org.rdfarchitect.dl.data.dto.relations.MRID;
import org.rdfarchitect.dl.data.dto.relations.XYOffset;
import org.rdfarchitect.dl.queries.DLQuerySolutionParser;
import org.rdfarchitect.dl.queries.DLQueryVars;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Utility class for fetching diagram layout objects from a provided model */
@UtilityClass
public class DLObjectFetcher {

    /**
     * Fetches the {@link Diagram} corresponding to the provided package UUID
     *
     * @param diagramLayout the model from where the object(s) will be fetched
     * @param packageUUID the package UUID identifying the diagram
     * @return {@link Diagram}
     */
    public Diagram fetchDiagram(Model diagramLayout, UUID packageUUID) {
        var diagramMRID = new MRID(packageUUID);
        var query =
                """
                  PREFIX  rdf:    <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                  PREFIX  cim:    <http://iec.ch/TC57/CIM100#>

                  SELECT ?diagramName
                  WHERE {
                      ?diagramMRID rdf:type cim:Diagram ;
                                    cim:IdentifiedObject.name ?diagramName .

                      FILTER(STR(?diagramMRID) = "DIAGRAM_MRID")
                  }
                  """
                        .replace("DIAGRAM_MRID", diagramMRID.getFullMRID());

        try (var qexec = QueryExecutionFactory.create(query, diagramLayout)) {
            var results = qexec.execSelect();

            if (results.hasNext()) {
                var querySolution = results.next();

                var diagram = DLObjectFactory.createDiagram(querySolution);
                diagram.setMRID(diagramMRID);

                return diagram;
            }
            return null;
        }
    }

    /**
     * Fetches a map which maps class UUIDs to the {@link DiagramObjectPoint} which make up its
     * position in a diagram
     *
     * @param diagramLayout the model from where the object(s) will be fetched
     * @param packageUUID the package UUID identifying the diagram
     * @return a map mapping class UUIDs to the corresponding {@link DiagramObjectPoint} in the
     *     diagram
     */
    public Map<UUID, DiagramObjectPoint> fetchDiagramDOPPerClass(
            Model diagramLayout, UUID packageUUID) {
        var diagramMRID = new MRID(packageUUID).getFullMRID();
        Map<UUID, DiagramObjectPoint> resultMap = new HashMap<>();

        var query =
                """
                  PREFIX  rdf:    <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                  PREFIX  cim:    <http://iec.ch/TC57/CIM100#>

                  SELECT ?ioMRID ?dopMRID ?doMRID ?xPosition ?yPosition ?zPosition
                  WHERE {
                      ?diagramMRID rdf:type cim:Diagram .

                      ?doMRID rdf:type cim:DiagramObject ;
                                     cim:DiagramObject.DiagramObjectStyle ?styleMRID ;
                                     cim:DiagramObject.IdentifiedObject ?ioMRID ;
                                     cim:DiagramObject.Diagram ?diagramMRID .

                      ?dopMRID rdf:type cim:DiagramObjectPoint ;
                                      cim:DiagramObjectPoint.DiagramObject ?doMRID ;
                                      cim:DiagramObjectPoint.xPosition ?xPosition ;
                                      cim:DiagramObjectPoint.yPosition ?yPosition .
                      OPTIONAL {
                        ?dopMRID cim:DiagramObjectPoint.zPosition ?zPosition
                      }

                      FILTER(STR(?diagramMRID) = "DIAGRAM_MRID")
                      STYLE_FILTER
                  }
                  """
                        .replace("DIAGRAM_MRID", diagramMRID)
                        .replace("STYLE_FILTER", classStyleFilter(true));

        try (var qexec = QueryExecutionFactory.create(query, diagramLayout)) {
            var results = qexec.execSelect();

            while (results.hasNext()) {
                var querySolution = results.next();

                var dop = DLObjectFactory.createDiagramObjectPoint(querySolution);

                var ioUUID =
                        new DLQuerySolutionParser(querySolution)
                                .getMRID(DLQueryVars.IO_MRID)
                                .getUuid();
                resultMap.put(ioUUID, dop);
            }

            return resultMap;
        }
    }

    /**
     * Fetches the {@link DiagramObjectPoint} for a given diagram object MRID
     *
     * @param diagramLayout the model from where the object(s) will be fetched
     * @param doMRID the diagram object MRID used for fetching
     * @return {@link DiagramObjectPoint}
     */
    public DiagramObjectPoint fetchDOPForDO(Model diagramLayout, MRID doMRID) {
        var query =
                """
                  PREFIX  rdf:    <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                  PREFIX  cim:    <http://iec.ch/TC57/CIM100#>

                  SELECT ?dopMRID ?xPosition ?yPosition ?zPosition
                  WHERE {
                      ?dopMRID rdf:type cim:DiagramObjectPoint ;
                            cim:DiagramObjectPoint.DiagramObject ?doMRID ;
                            cim:DiagramObjectPoint.xPosition ?xPosition ;
                            cim:DiagramObjectPoint.yPosition ?yPosition .
                      OPTIONAL {
                        ?dopMRID cim:DiagramObjectPoint.zPosition ?zPosition
                      }

                      FILTER(STR(?doMRID) = "DO_MRID")
                  }
                  """
                        .replace("DO_MRID", doMRID.getFullMRID());

        try (var qexec = QueryExecutionFactory.create(query, diagramLayout)) {
            var results = qexec.execSelect();

            if (results.hasNext()) {
                var querySolution = results.next();
                var diagramObjectPoint = DLObjectFactory.createDiagramObjectPoint(querySolution);
                diagramObjectPoint.setBelongsToDiagramObject(doMRID);
                return diagramObjectPoint;
            }
            return null;
        }
    }

    /**
     * Fetches a list of all {@link DiagramObject DiagramObjects} in a diagram
     *
     * @param diagramLayout the model from where the object(s) will be fetched
     * @param diagramMRID the MRID of the diagram from which the objects will be fetched
     * @return a list of {@link DiagramObject DiagramObjects}
     */
    public List<DiagramObject> fetchDiagramDOs(Model diagramLayout, MRID diagramMRID) {
        var query =
                """
                  PREFIX  rdf:    <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                  PREFIX  cim:    <http://iec.ch/TC57/CIM100#>

                  SELECT ?doMRID ?doName ?ioMRID ?styleName
                  WHERE {
                      ?diagramMRID rdf:type cim:Diagram .

                      ?doMRID rdf:type cim:DiagramObject ;
                            cim:DiagramObject.DiagramObjectStyle ?styleMRID ;
                            cim:IdentifiedObject.name ?doName ;
                            cim:DiagramObject.Diagram ?diagramMRID ;
                            cim:DiagramObject.IdentifiedObject ?ioMRID .
                      STYLE_NAME_JOIN

                      FILTER(STR(?diagramMRID) = "DIAGRAM_MRID")
                      STYLE_FILTER
                  }
                  """
                        .replace("DIAGRAM_MRID", diagramMRID.getFullMRID())
                        .replace("STYLE_FILTER", classStyleFilter(true))
                        .replace("STYLE_NAME_JOIN", STYLE_NAME_JOIN);

        try (var qexec = QueryExecutionFactory.create(query, diagramLayout)) {
            var results = qexec.execSelect();

            List<DiagramObject> diagramObjects = new ArrayList<>();

            while (results.hasNext()) {
                var querySolution = results.next();

                var diagramObject = DLObjectFactory.createDiagramObject(querySolution);

                diagramObject.setBelongsToDiagram(diagramMRID);

                diagramObjects.add(diagramObject);
            }
            return diagramObjects;
        }
    }

    /**
     * Fetches a list of all {@link DiagramObject DiagramObjects} for a given class UUID
     *
     * @param diagramLayout the model from where the object(s) will be fetched
     * @param classUUID the UUID of the class for which the diagram objects will be fetched
     * @return a list of {@link DiagramObject DiagramObjects}
     */
    public List<DiagramObject> fetchAllDOs(Model diagramLayout, UUID classUUID) {
        var ioMRID = new MRID(classUUID).getFullMRID();

        var query =
                """
                  PREFIX  rdf:    <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                  PREFIX  cim:    <http://iec.ch/TC57/CIM100#>

                  SELECT ?doMRID ?doName ?diagramMRID ?styleName
                  WHERE {
                      ?doMRID rdf:type cim:DiagramObject ;
                            cim:DiagramObject.DiagramObjectStyle ?styleMRID ;
                            cim:IdentifiedObject.name ?doName ;
                            cim:DiagramObject.Diagram ?diagramMRID ;
                            cim:DiagramObject.IdentifiedObject ?ioMRID .
                      STYLE_NAME_JOIN

                      FILTER(STR(?ioMRID) = "IO_MRID")
                      STYLE_FILTER
                  }
                  """
                        .replace("IO_MRID", ioMRID)
                        .replace("STYLE_FILTER", classStyleFilter(true))
                        .replace("STYLE_NAME_JOIN", STYLE_NAME_JOIN);

        try (var qexec = QueryExecutionFactory.create(query, diagramLayout)) {
            var results = qexec.execSelect();

            List<DiagramObject> diagramObjects = new ArrayList<>();

            while (results.hasNext()) {
                var querySolution = results.next();

                var diagramObject = DLObjectFactory.createDiagramObject(querySolution);

                diagramObject.setBelongsToIdentifiedObject(new MRID(classUUID));

                diagramObjects.add(diagramObject);
            }
            return diagramObjects;
        }
    }

    /**
     * Fetches the {@link DiagramObject} in a specific diagram for a specific class UUID
     *
     * @param diagramLayout the model from where the object(s) will be fetched
     * @param packageUUID the package UUID identifying the diagram
     * @param classUUID the UUID of the class for which the diagram object will be fetched
     * @return {@link DiagramObject}
     */
    public DiagramObject fetchDiagramDOForClass(
            Model diagramLayout, UUID packageUUID, UUID classUUID) {
        var diagramMRID = new MRID(packageUUID).getFullMRID();
        var ioMRID = new MRID(classUUID).getFullMRID();
        var query =
                """
                  PREFIX  rdf:    <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                  PREFIX  cim:    <http://iec.ch/TC57/CIM100#>

                  SELECT ?doMRID ?doName ?styleName
                  WHERE {
                      ?diagramMRID rdf:type cim:Diagram .

                      ?doMRID rdf:type cim:DiagramObject ;
                            cim:DiagramObject.DiagramObjectStyle ?styleMRID ;
                            cim:IdentifiedObject.name ?doName ;
                            cim:DiagramObject.Diagram ?diagramMRID ;
                            cim:DiagramObject.IdentifiedObject ?ioMRID .
                      STYLE_NAME_JOIN

                      FILTER(STR(?diagramMRID) = "DIAGRAM_MRID")
                      FILTER(STR(?ioMRID) = "IO_MRID")
                      STYLE_FILTER
                  }
                  """
                        .replace("IO_MRID", ioMRID)
                        .replace("DIAGRAM_MRID", diagramMRID)
                        .replace("STYLE_FILTER", classStyleFilter(true))
                        .replace("STYLE_NAME_JOIN", STYLE_NAME_JOIN);

        try (var qexec = QueryExecutionFactory.create(query, diagramLayout)) {
            var results = qexec.execSelect();

            if (results.hasNext()) {
                var querySolution = results.next();

                var diagramObject = DLObjectFactory.createDiagramObject(querySolution);

                diagramObject.setBelongsToDiagram(new MRID(packageUUID));
                diagramObject.setBelongsToIdentifiedObject(new MRID(classUUID));

                return diagramObject;
            }
            return null;
        }
    }

    /**
     * Key of a movable label in a diagram. A label belongs to the CIM resource whose text it
     * displays, an association end for multiplicities, and is distinguished from other labels of
     * that same resource by its style.
     *
     * @param identifiedObjectUUID the UUID of the CIM resource the label belongs to
     * @param style the style of the label
     */
    public record LabelKey(UUID identifiedObjectUUID, DiagramObjectStyle style) {}

    /**
     * Fetches the offsets of all manually placed labels of a diagram. A label is a diagram object
     * whose style is not the one classes carry. Its placement is an offset relative to the class
     * the label is anchored to, not a coordinate within the diagram, which is why it is held in
     * {@code DiagramObject.offsetX/offsetY} instead of in a {@code DiagramObjectPoint}.
     *
     * @param diagramLayout the model from where the offsets will be fetched
     * @param diagramUUID the diagram whose labels are fetched
     * @return a map from label key to the offset of that label
     */
    public Map<LabelKey, XYOffset> fetchLabelOffsets(Model diagramLayout, UUID diagramUUID) {
        var query =
                """
                  PREFIX  rdf:    <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                  PREFIX  cim:    <http://iec.ch/TC57/CIM100#>

                  SELECT ?ioMRID ?styleName ?offsetX ?offsetY
                  WHERE {
                      ?doMRID rdf:type cim:DiagramObject ;
                            cim:DiagramObject.DiagramObjectStyle ?styleMRID ;
                            cim:DiagramObject.IdentifiedObject ?ioMRID ;
                            cim:DiagramObject.Diagram ?diagramMRID ;
                            cim:DiagramObject.offsetX ?offsetX ;
                            cim:DiagramObject.offsetY ?offsetY .

                      ?styleMRID cim:IdentifiedObject.name ?styleName .

                      FILTER(STR(?diagramMRID) = "DIAGRAM_MRID")
                      STYLE_FILTER
                  }
                  """
                        .replace("DIAGRAM_MRID", new MRID(diagramUUID).getFullMRID())
                        .replace("STYLE_FILTER", classStyleFilter(false));

        try (var qexec = QueryExecutionFactory.create(query, diagramLayout)) {
            var results = qexec.execSelect();

            Map<LabelKey, XYOffset> resultMap = new HashMap<>();
            while (results.hasNext()) {
                var querySolution = results.next();
                var parser = new DLQuerySolutionParser(querySolution);
                var style = DiagramObjectStyle.byName(parser.getName(DLQueryVars.STYLE_NAME));
                if (style == null) {
                    continue;
                }
                resultMap.put(
                        new LabelKey(parser.getMRID(DLQueryVars.IO_MRID).getUuid(), style),
                        parser.getXYOffset());
            }
            return resultMap;
        }
    }

    /**
     * Fetches the diagram object of one label, so it can be replaced or deleted.
     *
     * @param diagramLayout the model from where the object will be fetched
     * @param diagramUUID the diagram the label belongs to
     * @param labelKey the key of the label
     * @return the {@link DiagramObject} of the label, or null if the label has no stored placement
     */
    public DiagramObject fetchLabelDO(Model diagramLayout, UUID diagramUUID, LabelKey labelKey) {
        var query =
                """
                  PREFIX  rdf:    <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                  PREFIX  cim:    <http://iec.ch/TC57/CIM100#>

                  SELECT ?doMRID ?styleName
                  WHERE {
                      ?doMRID rdf:type cim:DiagramObject ;
                            cim:DiagramObject.DiagramObjectStyle ?styleMRID ;
                            cim:DiagramObject.IdentifiedObject ?ioMRID ;
                            cim:DiagramObject.Diagram ?diagramMRID .
                      STYLE_NAME_JOIN

                      FILTER(STR(?diagramMRID) = "DIAGRAM_MRID")
                      FILTER(STR(?ioMRID) = "IO_MRID")
                      FILTER(STR(?styleMRID) = "STYLE_MRID")
                  }
                  """
                        .replace("DIAGRAM_MRID", new MRID(diagramUUID).getFullMRID())
                        .replace("IO_MRID", new MRID(labelKey.identifiedObjectUUID()).getFullMRID())
                        .replace("STYLE_MRID", labelKey.style().getMRID().getFullMRID())
                        .replace("STYLE_NAME_JOIN", STYLE_NAME_JOIN);

        try (var qexec = QueryExecutionFactory.create(query, diagramLayout)) {
            var results = qexec.execSelect();
            if (!results.hasNext()) {
                return null;
            }
            return DLObjectFactory.createDiagramObject(results.next());
        }
    }

    /**
     * Fetches the labels of one diagram, so they can be dropped together with it.
     *
     * @param diagramLayout the model from where the objects will be fetched
     * @param diagramUUID the diagram whose labels are fetched
     * @return a list of the label {@link DiagramObject DiagramObjects} of that diagram
     */
    public List<DiagramObject> fetchDiagramLabelDOs(Model diagramLayout, UUID diagramUUID) {
        var diagramMRID = new MRID(diagramUUID);
        return fetchAllLabelDOs(diagramLayout).stream()
                .filter(label -> diagramMRID.equals(label.getBelongsToDiagram()))
                .toList();
    }

    /**
     * Fetches every label of the model, across all diagrams. Unlike {@link #fetchLabelOffsets} this
     * cannot be keyed on the label, because the same label may be placed in more than one diagram.
     *
     * @param diagramLayout the model from where the objects will be fetched
     * @return a list of the label {@link DiagramObject DiagramObjects}
     */
    public List<DiagramObject> fetchAllLabelDOs(Model diagramLayout) {
        var query =
                """
                  PREFIX  rdf:    <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                  PREFIX  cim:    <http://iec.ch/TC57/CIM100#>

                  SELECT ?doMRID ?ioMRID ?diagramMRID ?styleName
                  WHERE {
                      ?doMRID rdf:type cim:DiagramObject ;
                            cim:DiagramObject.DiagramObjectStyle ?styleMRID ;
                            cim:DiagramObject.Diagram ?diagramMRID ;
                            cim:DiagramObject.IdentifiedObject ?ioMRID .
                      STYLE_NAME_JOIN

                      STYLE_FILTER
                  }
                  """
                        .replace("STYLE_FILTER", classStyleFilter(false))
                        .replace("STYLE_NAME_JOIN", STYLE_NAME_JOIN);

        try (var qexec = QueryExecutionFactory.create(query, diagramLayout)) {
            var results = qexec.execSelect();

            List<DiagramObject> diagramObjects = new ArrayList<>();
            while (results.hasNext()) {
                diagramObjects.add(DLObjectFactory.createDiagramObject(results.next()));
            }
            return diagramObjects;
        }
    }

    /**
     * Joins a diagram object's style resource to its name, so {@link
     * org.rdfarchitect.dl.data.DLObjectFactory#createDiagramObject} can resolve a {@link
     * DiagramObjectStyle}. Optional because the style-vs-not-style filters below only need the bare
     * {@code ?styleMRID}, and some legacy layout data may not have the name triple.
     */
    private static final String STYLE_NAME_JOIN =
            "OPTIONAL { ?styleMRID cim:IdentifiedObject.name ?styleName . }";

    /** A filter restricting {@code ?styleMRID} to (or, negated, away from) the class style. */
    private static String classStyleFilter(boolean isClassStyle) {
        return "FILTER(STR(?styleMRID) %s \"%s\")"
                .formatted(
                        isClassStyle ? "=" : "!=",
                        DiagramObjectStyle.CLASS.getMRID().getFullMRID());
    }
}

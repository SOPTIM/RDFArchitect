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
import org.rdfarchitect.dl.data.dto.relations.MRID;
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

                  SELECT ?ioMRID ?dopMRID ?doMRID ?xPosition ?yPosition
                  WHERE {
                      ?diagramMRID rdf:type cim:Diagram .

                      ?doMRID rdf:type cim:DiagramObject ;
                                     cim:DiagramObject.IdentifiedObject ?ioMRID ;
                                     cim:DiagramObject.Diagram ?diagramMRID .

                      ?dopMRID rdf:type cim:DiagramObjectPoint ;
                                      cim:DiagramObjectPoint.DiagramObject ?doMRID ;
                                      cim:DiagramObjectPoint.xPosition ?xPosition ;
                                      cim:DiagramObjectPoint.yPosition ?yPosition .

                      FILTER(STR(?diagramMRID) = "DIAGRAM_MRID")
                  }
                  """
                        .replace("DIAGRAM_MRID", diagramMRID);

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

                  SELECT ?dopMRID ?xPosition ?yPosition
                  WHERE {
                      ?dopMRID rdf:type cim:DiagramObjectPoint ;
                            cim:DiagramObjectPoint.DiagramObject ?doMRID ;
                            cim:DiagramObjectPoint.xPosition ?xPosition ;
                            cim:DiagramObjectPoint.yPosition ?yPosition .

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

                  SELECT ?doMRID ?doName ?ioMRID
                  WHERE {
                      ?diagramMRID rdf:type cim:Diagram .

                      ?doMRID rdf:type cim:DiagramObject ;
                            cim:IdentifiedObject.name ?doName ;
                            cim:DiagramObject.Diagram ?diagramMRID ;
                            cim:DiagramObject.IdentifiedObject ?ioMRID .

                      FILTER(STR(?diagramMRID) = "DIAGRAM_MRID")
                  }
                  """
                        .replace("DIAGRAM_MRID", diagramMRID.getFullMRID());

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

                  SELECT ?doMRID ?doName ?diagramMRID
                  WHERE {
                      ?doMRID rdf:type cim:DiagramObject ;
                            cim:IdentifiedObject.name ?doName ;
                            cim:DiagramObject.Diagram ?diagramMRID ;
                            cim:DiagramObject.IdentifiedObject ?ioMRID .

                      FILTER(STR(?ioMRID) = "IO_MRID")
                  }
                  """
                        .replace("IO_MRID", ioMRID);

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

                  SELECT ?doMRID ?doName
                  WHERE {
                      ?diagramMRID rdf:type cim:Diagram .

                      ?doMRID rdf:type cim:DiagramObject ;
                            cim:IdentifiedObject.name ?doName ;
                            cim:DiagramObject.Diagram ?diagramMRID ;
                            cim:DiagramObject.IdentifiedObject ?ioMRID .

                      FILTER(STR(?diagramMRID) = "DIAGRAM_MRID")
                      FILTER(STR(?ioMRID) = "IO_MRID")
                  }
                  """
                        .replace("IO_MRID", ioMRID)
                        .replace("DIAGRAM_MRID", diagramMRID);

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
}

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

package org.rdfarchitect.shacl;

import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.rdf.model.Model;
import org.rdfarchitect.cim.rdf.resources.RDFA;
import org.rdfarchitect.cim.relations.CIMClassRelationFinder;
import org.rdfarchitect.shacl.dto.PropertyShapesWrapper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@SuppressWarnings("java:S1192")
public class PropertyShapeToClassAssigner {

    private final Dataset dataset;

    private static final String SHACL_GRAPH_URI = "https://example.com/shacl";

    private static final String ONTOLOGY_GRAPH_URI = "https://example.com/ontology";

    public PropertyShapeToClassAssigner(Model shaclModel, Model ontologyModel) {
        dataset = DatasetFactory.create();
        dataset.addNamedModel(SHACL_GRAPH_URI, shaclModel);
        dataset.addNamedModel(ONTOLOGY_GRAPH_URI, ontologyModel);
    }

    /**
     * Creates a List of property shapes of a class.
     *
     * @param classUUID the uuid of the class
     * @return a string representation of the property shapes of the class
     */
    public List<PropertyShapesWrapper> getPropertyShapes(UUID classUUID) {
        var resultList = new ArrayList<PropertyShapesWrapper>();
        resultList.addAll(getAttributePropertyShapes(classUUID));
        resultList.addAll(getAssociationPropertyShapes(classUUID));
        resultList.addAll(getSparqlConstraints(classUUID));
        addRemainingPropertyShapes(classUUID, resultList);


        resultList.sort(Comparator.comparing(PropertyShapesWrapper::getLabel));
        resultList.sort(Comparator.comparing(propertyShapesWrapper -> {
            if (propertyShapesWrapper.getPropertyShapes().isEmpty()) {
                return 0.0;
            }
            return propertyShapesWrapper.getPropertyShapes().get(0).getOrder();
        }));
        return resultList;
    }

    public List<PropertyShapesWrapper> getDerivedPropertyShapesOfClass(UUID classUUID) {
        var superClasses = new CIMClassRelationFinder(dataset.getNamedModel(ONTOLOGY_GRAPH_URI)).findSuperClasses(classUUID);
        var propertyShapes = new ArrayList<PropertyShapesWrapper>();
        for (var superClass : superClasses) {
            propertyShapes.addAll(getPropertyShapes(superClass.getUuid()));
        }
        return propertyShapes;
    }

    private List<PropertyShapesWrapper> getAttributePropertyShapes(UUID classUUID) {
        var query = """
                PREFIX  rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                PREFIX  sh:   <http://www.w3.org/ns/shacl#>
                PREFIX  rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                PREFIX  cims: <http://iec.ch/TC57/1999/rdf-schema-extensions-19990926#>
                
                SELECT DISTINCT ?Property
                WHERE {
                    GRAPH <ONTOLOGY_GRAPH_URI> {
                        ?classUri   <RDFA_UUID> "CLASS_UUID".
                        ?Property   rdf:type rdf:Property;
                                    rdfs:domain ?classUri;
                                    cims:stereotype <http://iec.ch/TC57/NonStandard/UML#attribute>.
                    }
                    GRAPH <SHACL_GRAPH_URI> {
                        ?PropertyShape  rdf:type    sh:PropertyShape.
                        {
                            ?PropertyShape  sh:path ?Property.
                        }
                        UNION
                        {
                            ?PropertyShape  sh:path ?list.
                            ?list (rdf:rest*/rdf:first) ?Property.
                        }
                    }
                }
                """.replace("CLASS_UUID", classUUID.toString())
                .replace("RDFA_UUID", RDFA.uuid.getURI())
                .replace("SHACL_GRAPH_URI", SHACL_GRAPH_URI)
                .replace("ONTOLOGY_GRAPH_URI", ONTOLOGY_GRAPH_URI);
        try (var qExec = QueryExecutionFactory.create(query, dataset)) {
            var results = qExec.execSelect();
            var propertyShapes = new ArrayList<PropertyShapesWrapper>();
            while (results.hasNext()) {
                var querySolution = results.next();
                var propertyUri = querySolution.getResource("?Property").getURI();
                var propertyShapesOfProperty = new SHACLShapesFetcher(dataset.getNamedModel(SHACL_GRAPH_URI)).getPropertyShapesOfProperty(dataset.getNamedModel(ONTOLOGY_GRAPH_URI), propertyUri);
                var propertyShapeWrapper = PropertyShapesWrapper.builder()
                        .domain(classUUID)
                        .propertyType("attribute")
                        .label(propertyUri.split("#", 2)[1])
                        .propertyShapes(propertyShapesOfProperty)
                        .build();
                propertyShapeWrapper.setLabel(propertyUri.split("#", 2)[1]);
                propertyShapeWrapper.setPropertyShapes(propertyShapesOfProperty);
                propertyShapes.add(propertyShapeWrapper);
            }
            return propertyShapes;
        }
    }

    private List<PropertyShapesWrapper> getAssociationPropertyShapes(UUID classUUID) {
        // fetch properties that have propertyShapes
        var query = """
                PREFIX  rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                PREFIX  sh:   <http://www.w3.org/ns/shacl#>
                PREFIX  rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                PREFIX  cims: <http://iec.ch/TC57/1999/rdf-schema-extensions-19990926#>
                
                SELECT DISTINCT ?Property
                WHERE {
                    {
                        GRAPH <ONTOLOGY_GRAPH_URI> {
                            {
                                ?classUri   <RDFA_UUID> "CLASS_UUID".
                                ?Property   rdf:type rdf:Property;
                                            rdfs:domain ?classUri;
                                            cims:inverseRoleName ?inverseProperty.
                                FILTER NOT EXISTS {
                                    ?Property   cims:stereotype <http://iec.ch/TC57/NonStandard/UML#attribute>.
                                }
                            }
                        }
                    }
                    GRAPH <SHACL_GRAPH_URI> {
                        ?PropertyShape  rdf:type  sh:PropertyShape.
                        {
                            ?PropertyShape  sh:path ?Property.
                        }
                        UNION
                        {
                            ?PropertyShape  sh:path ?list.
                            ?list (rdf:rest*/rdf:first) ?Property.
                        }
                        UNION
                        {
                            ?PropertyShape  sh:path [ sh:inversePath ?inverseProperty  ].
                        }
                    }
                }
                """.replace("CLASS_UUID", classUUID.toString())
                .replace("RDFA_UUID", RDFA.uuid.getURI())
                .replace("SHACL_GRAPH_URI", SHACL_GRAPH_URI)
                .replace("ONTOLOGY_GRAPH_URI", ONTOLOGY_GRAPH_URI);
        try (var quexec = QueryExecutionFactory.create(query, dataset)) {
            var results = quexec.execSelect();
            var propertyShapes = new ArrayList<PropertyShapesWrapper>();
            while (results.hasNext()) {
                var querySolution = results.next();
                var propertyUri = querySolution.getResource("?Property").getURI();
                var propertyShapeFetcher = new SHACLShapesFetcher(dataset.getNamedModel(SHACL_GRAPH_URI));
                var propertyShapeWrapper = PropertyShapesWrapper.builder()
                        .domain(classUUID)
                        .propertyType("association")
                        .label(propertyUri.split("#", 2)[1])
                        .propertyShapes(propertyShapeFetcher.getPropertyShapesOfProperty(dataset.getNamedModel(ONTOLOGY_GRAPH_URI), propertyUri))
                        .build();
                propertyShapes.add(propertyShapeWrapper);
            }
            return propertyShapes;
        }
    }

    private List<PropertyShapesWrapper> getSparqlConstraints(UUID classUUID) {
        var query = """
                PREFIX  rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                PREFIX  sh:   <http://www.w3.org/ns/shacl#>
                PREFIX  rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                PREFIX  cims: <http://iec.ch/TC57/1999/rdf-schema-extensions-19990926#>
                
                SELECT DISTINCT ?PropertyShape
                WHERE {
                    GRAPH <ONTOLOGY_GRAPH_URI> {
                        ?classUri   <RDFA_UUID> "CLASS_UUID".
                    }
                    GRAPH <SHACL_GRAPH_URI> {
                        ?NodeShape      sh:targetClass ?classUri;
                                        rdf:type sh:NodeShape;
                                        sh:property ?PropertyShape.
                        ?PropertyShape  rdf:type sh:PropertyShape;
                                        sh:sparql ?sparql.
                    }
                }
                """.replace("CLASS_UUID", classUUID.toString())
                .replace("RDFA_UUID", RDFA.uuid.getURI())
                .replace("SHACL_GRAPH_URI", SHACL_GRAPH_URI)
                .replace("ONTOLOGY_GRAPH_URI", ONTOLOGY_GRAPH_URI);
        try (var quexec = QueryExecutionFactory.create(query, dataset)) {
            var results = quexec.execSelect();
            var propertyShapes = new ArrayList<PropertyShapesWrapper>();
            while (results.hasNext()) {
                var querySolution = results.next();
                var propertyShapeUri = querySolution.getResource("?PropertyShape").getURI();
                var propertyShapeFetcher = new SHACLShapesFetcher(dataset.getNamedModel(SHACL_GRAPH_URI));
                var propertyShapesOfProperty = propertyShapeFetcher.getPropertyShape(propertyShapeUri);
                var propertyShapeWrapper = PropertyShapesWrapper.builder()
                        .domain(classUUID)
                        .propertyType("sparql")
                        .label(propertyShapeUri.split("#", 2)[1])
                        .propertyShapes(new ArrayList<>(List.of(propertyShapesOfProperty)))
                        .build();
                propertyShapes.add(propertyShapeWrapper);
            }
            return propertyShapes;
        }
    }

    /**
     * Adds the remaining property shapes to the list of property shapes.
     *
     * @param classUUID                 the uuid of the class.
     * @param propertyShapesWrapperList the list of known property shapes.
     */
    private void addRemainingPropertyShapes(UUID classUUID, List<PropertyShapesWrapper> propertyShapesWrapperList) {
        var querySb = new StringBuilder("""
                PREFIX  rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                PREFIX  sh:   <http://www.w3.org/ns/shacl#>
                PREFIX  rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                PREFIX  cims: <http://iec.ch/TC57/1999/rdf-schema-extensions-19990926#>
                
                SELECT DISTINCT ?propertyShape
                WHERE {
                    GRAPH <ONTOLOGY_GRAPH_URI> {
                        ?classUri   <RDFA_UUID> "CLASS_UUID".
                    }
                    GRAPH <SHACL_GRAPH_URI> {
                        ?NodeShape  sh:targetClass ?classUri;
                                    sh:property ?propertyShape.
                """);
        for (var propertyShapesWrapper : propertyShapesWrapperList) {
            for (var propertyShape : propertyShapesWrapper.getPropertyShapes()) {
                querySb.append("       FILTER(?PropertyShape != <")
                        .append(propertyShape.getId())
                        .append(">)\n");
            }
        }
        querySb.append("""
                    }
                }
                """);
        var query = querySb.toString()
                .replace("CLASS_UUID", classUUID.toString())
                .replace("RDFA_UUID", RDFA.uuid.getURI())
                .replace("SHACL_GRAPH_URI", SHACL_GRAPH_URI)
                .replace("ONTOLOGY_GRAPH_URI", ONTOLOGY_GRAPH_URI);
        var superClasses = new CIMClassRelationFinder(dataset.getNamedModel(ONTOLOGY_GRAPH_URI))
                .findSuperClasses(classUUID);
        try (var qexec = QueryExecutionFactory.create(query, dataset)) {
            var results = qexec.execSelect();
            while (results.hasNext()) {
                var querySolution = results.next();
                var propertyShapeUri = querySolution.getResource("propertyShape").getURI();
                // Check if the property shape is a super class attribute
                String localName = propertyShapeUri.split("#", 2)[1];
                boolean isSuperClassAttribute = superClasses.stream()
                        .map(sc -> sc.getLabel().getValue())
                        .anyMatch(localName::startsWith);
                if (isSuperClassAttribute) {
                    continue;
                }
                var propertyShapeFetcher = new SHACLShapesFetcher(dataset.getNamedModel(SHACL_GRAPH_URI));
                var propertyShapesOfProperty = propertyShapeFetcher.getPropertyShape(propertyShapeUri);
                var propertyShapeWrapper = PropertyShapesWrapper.builder()
                        .domain(classUUID)
                        .propertyType("other")
                        .label(propertyShapeUri.split("#", 2)[1])
                        .propertyShapes(new ArrayList<>(List.of(propertyShapesOfProperty)))
                        .build();
                propertyShapesWrapperList.add(propertyShapeWrapper);
            }
        }
    }


}

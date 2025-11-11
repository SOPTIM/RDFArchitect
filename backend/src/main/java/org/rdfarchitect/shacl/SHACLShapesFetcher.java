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

import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.shacl.vocabulary.SHACL;
import org.rdfarchitect.shacl.dto.NodeShape;
import org.rdfarchitect.shacl.dto.PropertyShape;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

public class SHACLShapesFetcher {

    private final Model shaclModel;

    public SHACLShapesFetcher(Model shaclModel) {
        this.shaclModel = shaclModel;
    }


    /**
     * Returns a string representation of the SHACL shapes referencing a given property.
     *
     * @param propertyUri the uri of the property
     * @return a List of {@link PropertyShape} objects containing the string representation of the SHACL shapes referencing the property
     */
    public List<PropertyShape> getPropertyShapesOfProperty(Model ontology, String propertyUri) {
        var query = """
                PREFIX  rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                PREFIX  sh:   <http://www.w3.org/ns/shacl#>
                PREFIX cims:    <http://iec.ch/TC57/1999/rdf-schema-extensions-19990926#>
                
                SELECT DISTINCT ?propertyShape
                WHERE {
                    {
                        ?propertyShape  sh:path <PROPERTY_URI>.
                    }
                    UNION
                    {
                        ?propertyShape  sh:path ?list.
                        ?list (rdf:rest*/rdf:first) <PROPERTY_URI>.
                    }
                    UNION
                    {
                        ?propertyShape  sh:path [ sh:inversePath ?inverseRoleName  ].
                        ?inverseRoleName cims:inverseRoleName <PROPERTY_URI> .
                    }
                    ?propertyShape  rdf:type  sh:PropertyShape.
                }
                """.replace("PROPERTY_URI", propertyUri);
        var shaclModelWithOntology = ModelFactory.createUnion(shaclModel, ontology);
        try (var qexec = QueryExecutionFactory.create(query, shaclModelWithOntology)) {
            var results = qexec.execSelect();
            var propertyShapes = new ArrayList<PropertyShape>();
            results.forEachRemaining(querySolution -> {
                var propertyShapesModel = ModelFactory.createDefaultModel();
                propertyShapesModel.setNsPrefixes(shaclModel);
                copySHACLShapeToNewModel(shaclModel, propertyShapesModel, querySolution.get("?propertyShape"));
                var propertyShape = PropertyShape.builder()
                        .id(querySolution.get("?propertyShape").toString())
                        .triples(modelToTtlString(propertyShapesModel, propertyUri, false))
                        .order(getPropertyShapeOrder(propertyShapesModel))
                        .build();
                propertyShapes.add(propertyShape);
            });
            return propertyShapes;
        }
    }

    /**
     * Returns the order of a property shape. In this context we assume that maximum one order is defined.
     *
     * @param propertyShapeModel the model containing the property shape
     * @return the order of the property shape, if no order exists return 0
     */
    private double getPropertyShapeOrder(Model propertyShapeModel) {
        var order = propertyShapeModel.listObjectsOfProperty(ResourceFactory.createProperty(SHACL.order.getURI())).toList();
        if (order.isEmpty()) {
            return 0;
        }
        return order.get(0).asLiteral().getInt();
    }

    /**
     * Returns a string representation a propertyShape and its constraints.
     *
     * @param propertyShapeUri the uri of the property shape
     * @return a string representation of the property shape and its constraints
     */
    public PropertyShape getPropertyShape(String propertyShapeUri) {
        var model = ModelFactory.createDefaultModel();
        copySHACLShapeToNewModel(shaclModel, model, shaclModel.getResource(propertyShapeUri));
        model.setNsPrefixes(shaclModel);
        return PropertyShape.builder()
                .id(propertyShapeUri)
                .triples(modelToTtlString(model, propertyShapeUri, false))
                .order(getPropertyShapeOrder(model))
                .build();
    }

    /**
     * Returns a string representation of the SHACL shapes referencing a given association.
     *
     * @param classUri the uri of the association
     * @return a List of {@link PropertyShape} objects containing the string representation of the SHACL shapes referencing the association
     */
    public List<NodeShape> getNodeShapesOfClass(String classUri) {
        var query = """
                PREFIX  rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                PREFIX  sh:   <http://www.w3.org/ns/shacl#>
                
                SELECT DISTINCT ?nodeShape
                WHERE {
                    ?nodeShape  rdf:type  sh:NodeShape;
                                sh:targetClass <CLASS_URI>.
                }
                """.replace("CLASS_URI", classUri);
        try (var qexec = QueryExecutionFactory.create(query, shaclModel)) {
            var results = qexec.execSelect();
            var nodeShapes = new ArrayList<NodeShape>();
            results.forEachRemaining(querySolution -> {
                var nodeShapeModel = ModelFactory.createDefaultModel();
                nodeShapeModel.setNsPrefixes(shaclModel);
                copySHACLShapeToNewModel(shaclModel, nodeShapeModel, querySolution.get("?nodeShape"));
                var nodeShape = NodeShape.builder()
                        .id(querySolution.get("?nodeShape").toString())
                        .triples(modelToTtlString(nodeShapeModel, classUri, false))
                        .build();
                nodeShapes.add(nodeShape);
            });
            return nodeShapes;
        }
    }

    /**
     * formats the model to a string representation
     *
     * @param model               the model to convert
     * @param referencedRessource the uri of the ressource the SHACL shapes are referencing
     * @param includePrefixes     if true, the prefixes will be included in the string representation
     * @return the string representation of the model
     */
    private String modelToTtlString(Model model, String referencedRessource, boolean includePrefixes) {
        if (model.isEmpty()) {
            return "No property shapes found " + (referencedRessource != null ? "for <" + referencedRessource + ">" : "") + "!";
        }
        var outputStream = new ByteArrayOutputStream();
        model.write(outputStream, "TURTLE");
        if (includePrefixes) {
            return outputStream.toString();
        }
        String regex = "(?m)^PREFIX.*\\n";
        return outputStream.toString().replaceAll(regex, "").trim();
    }

    /**
     * Copies the SHACL shape and its constraints to a new model.
     *
     * @param originalModel the original model containing the SHACL shapes
     * @param newModel      the new model to copy the SHACL shapes to
     * @param subject       the subject/uri of the SHACL shape
     */
    private void copySHACLShapeToNewModel(Model originalModel, Model newModel, RDFNode subject) {
        var sub = subject.asResource();
        var stmtIterator = originalModel.listStatements(sub, null, (RDFNode) null);
        while (stmtIterator.hasNext()) {
            var stmt = stmtIterator.nextStatement();
            newModel.add(stmt);
            var object = stmt.getObject();
            if (object.isAnon() || stmt.getPredicate().toString().equals(SHACL.sparql.getURI())) {
                copySHACLShapeToNewModel(originalModel, newModel, object);
            }
        }
    }
}

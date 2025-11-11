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

package org.rdfarchitect.shacl.generator.property.shapebuilder;

import lombok.Setter;
import lombok.experimental.Accessors;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.graph.Node;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.riot.system.PrefixEntry;
import org.apache.jena.shacl.vocabulary.SHACL;
import org.apache.jena.vocabulary.RDF;
import org.rdfarchitect.cim.data.dto.relations.uri.URI;

@Setter
@Accessors(chain = true)
public class HasTypePropertyShapeBuilder {

    private static final String DESCRIPTION_TEXT = "This constraint validates that an association points to a node with an rdf:type.";
    private static final String MESSAGE_TEXT = "The referenced node is missing an rdf:type, therefore a type validation is not possible.";

    private static final Literal DESCRIPTION = ResourceFactory.createPlainLiteral(DESCRIPTION_TEXT);
    private static final Literal MESSAGE = ResourceFactory.createPlainLiteral(MESSAGE_TEXT);

    // Set by user
    private PrefixEntry prefixEntry;
    private String associationUri;
    private double order;
    private String propertyGroupUri;

    // Internal state
    private final Model shaclModel;

    public HasTypePropertyShapeBuilder(Model resultModel) {
        this.shaclModel = resultModel;
    }

    public Resource build() {
        var associationLabel = new URI(associationUri).getSuffix();
        var propertyShapeName = shaclModel.createLiteral(associationLabel + "-hasType");
        var shapeUri = prefixEntry.getUri() + propertyShapeName.getString();
        var propertyShape = shaclModel.createResource(shapeUri);

        // Create the main PropertyShape
        propertyShape.addProperty(RDF.type, shaclModel.asRDFNode(SHACL.PropertyShape));
        propertyShape.addProperty(asProperty(SHACL.description), DESCRIPTION);
        propertyShape.addProperty(asProperty(SHACL.group), shaclModel.createResource(propertyGroupUri));
        propertyShape.addProperty(asProperty(SHACL.message), MESSAGE);
        propertyShape.addProperty(asProperty(SHACL.name), propertyShapeName);
        propertyShape.addLiteral(asProperty(SHACL.order), shaclModel.createTypedLiteral(order, XSDDatatype.XSDdecimal));
        propertyShape.addProperty(asProperty(SHACL.path), shaclModel.createResource(associationUri));
        propertyShape.addProperty(asProperty(SHACL.severity), shaclModel.asRDFNode(SHACL.Warning));

        // Create the nested NodeShape as blank node
        var nestedNodeShape = shaclModel.createResource();
        nestedNodeShape.addProperty(RDF.type, shaclModel.asRDFNode(SHACL.NodeShape));

        // Create the nested PropertyShape for rdf:type validation
        var nestedPropertyShape = shaclModel.createResource();
        nestedPropertyShape.addProperty(asProperty(SHACL.path), RDF.type);
        nestedPropertyShape.addLiteral(asProperty(SHACL.minCount), shaclModel.createTypedLiteral(1, XSDDatatype.XSDinteger));

        // Connect nested PropertyShape to nested NodeShape
        nestedNodeShape.addProperty(asProperty(SHACL.property), nestedPropertyShape);

        // Connect nested NodeShape to main PropertyShape
        propertyShape.addProperty(asProperty(SHACL.node), nestedNodeShape);

        return propertyShape;
    }

    private Property asProperty(Node predicate) {
        return shaclModel.createProperty(predicate.getURI());
    }
}
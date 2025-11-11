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
import org.apache.jena.datatypes.RDFDatatype;
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

import java.util.Collection;

@Setter
@Accessors(chain = true)
public class DatatypePropertyShapeBuilder {

    private static final String DESCRIPTION_TEXT = "This constraint validates the datatype of the property (attribute).";
    private static final String MESSAGE_TEXT_PRIMITIVE = "The datatype is not literal or it violates the xsd datatype.";
    private static final String MESSAGE_TEXT_ENUMERATION = "The datatype is not an IRI (Internationalized Resource Identifier) or its enumerated value is not part of the profile.";

    private static final Literal DESCRIPTION = ResourceFactory.createPlainLiteral(DESCRIPTION_TEXT);
    private static final Literal MESSAGE_PRIMITIVE = ResourceFactory.createPlainLiteral(MESSAGE_TEXT_PRIMITIVE);
    private static final Literal MESSAGE_ENUMERATION = ResourceFactory.createPlainLiteral(MESSAGE_TEXT_ENUMERATION);

    // Set by user
    private PrefixEntry prefixEntry;
    private String attributeUri;
    private double order;
    private String propertyGroupUri;
    private RDFDatatype primitiveDatatype;
    private Collection<String> datatypeUris;

    // Internal state
    private final Model shaclModel;

    public DatatypePropertyShapeBuilder(Model resultModel) {
        this.shaclModel = resultModel;
    }

    public Resource build() {
        var attributeLabel = new URI(attributeUri).getSuffix();
        var propertyShapeName = shaclModel.createLiteral(attributeLabel + "-datatype");
        var shapeUri = prefixEntry.getUri() + propertyShapeName.getString();
        var propertyShape = shaclModel.createResource(shapeUri);

        propertyShape.addProperty(RDF.type, shaclModel.asRDFNode(SHACL.PropertyShape));
        propertyShape.addProperty(asProperty(SHACL.description), DESCRIPTION);
        propertyShape.addProperty(asProperty(SHACL.group), shaclModel.createResource(propertyGroupUri));
        propertyShape.addProperty(asProperty(SHACL.name), propertyShapeName);
        propertyShape.addLiteral(asProperty(SHACL.order), shaclModel.createTypedLiteral(order, XSDDatatype.XSDdecimal));
        propertyShape.addProperty(asProperty(SHACL.path), shaclModel.createResource(attributeUri));
        propertyShape.addProperty(asProperty(SHACL.severity), shaclModel.asRDFNode(SHACL.Violation));

        if (primitiveDatatype != null) {
            propertyShape.addProperty(asProperty(SHACL.datatype), shaclModel.createResource(primitiveDatatype.getURI()));
            propertyShape.addProperty(asProperty(SHACL.message), MESSAGE_PRIMITIVE);
            propertyShape.addProperty(asProperty(SHACL.nodeKind), shaclModel.asRDFNode(SHACL.Literal));
        } else if (datatypeUris != null) {
            var datatypes = datatypeUris.stream()
                    .map(shaclModel::createResource)
                    .iterator();
            propertyShape.addProperty(asProperty(SHACL.in), shaclModel.createList(datatypes));
            propertyShape.addProperty(asProperty(SHACL.message), MESSAGE_ENUMERATION);
            propertyShape.addProperty(asProperty(SHACL.nodeKind), shaclModel.asRDFNode(SHACL.IRI));
        }

        return propertyShape;
    }

    private Property asProperty(Node predicate) {
        return shaclModel.createProperty(predicate.getURI());
    }
}

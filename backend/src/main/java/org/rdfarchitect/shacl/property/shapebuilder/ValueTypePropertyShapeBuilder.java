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

package org.rdfarchitect.shacl.property.shapebuilder;

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
import org.rdfarchitect.models.cim.data.dto.relations.uri.URI;

import java.util.Collection;

@Setter
@Accessors(chain = true)
public class ValueTypePropertyShapeBuilder {

    private static final String DESCRIPTION_TEXT =
            "This constraint validates the value type of the association at the used direction.";
    private static final String MESSAGE_TEXT_MULTIPLE_CLASSES =
            "One of the following occurs: 1) The value type is not IRI; 2) The value type is not the right class.";

    private static final Literal DESCRIPTION = ResourceFactory.createPlainLiteral(DESCRIPTION_TEXT);
    private static final Literal MESSAGE =
            ResourceFactory.createPlainLiteral(MESSAGE_TEXT_MULTIPLE_CLASSES);

    // set by user
    private PrefixEntry prefixEntry;
    private String propertyGroupUri;
    private String propertyUri;
    private double order;
    private Collection<String> valueTypeUris;

    // Internal state
    private final Model shaclModel;

    public ValueTypePropertyShapeBuilder(Model resultModel) {
        this.shaclModel = resultModel;
    }

    public ValueTypePropertyShapeBuilder setPrefixEntry(PrefixEntry prefixEntry) {
        this.prefixEntry = prefixEntry;
        return this;
    }

    public Resource build() {
        var valueTypes = valueTypeUris.stream().map(shaclModel::createResource).iterator();

        var attributeLabel = new URI(propertyUri).getSuffix();
        var propertyShapeName = shaclModel.createLiteral(attributeLabel + "-valueType");
        var shapeUri = prefixEntry.getUri() + propertyShapeName.getString();
        var propertyShape = shaclModel.createResource(shapeUri);

        propertyShape.addProperty(RDF.type, shaclModel.asRDFNode(SHACL.PropertyShape));
        propertyShape.addProperty(asProperty(SHACL.description), DESCRIPTION);
        propertyShape.addProperty(
                asProperty(SHACL.group), shaclModel.createResource(propertyGroupUri));
        propertyShape.addProperty(asProperty(SHACL.in), shaclModel.createList(valueTypes));
        propertyShape.addProperty(asProperty(SHACL.message), MESSAGE);
        propertyShape.addProperty(asProperty(SHACL.name), propertyShapeName);
        propertyShape.addProperty(asProperty(SHACL.nodeKind), shaclModel.asRDFNode(SHACL.IRI));
        propertyShape.addProperty(
                asProperty(SHACL.order),
                shaclModel.createTypedLiteral(order, XSDDatatype.XSDdecimal));
        propertyShape.addProperty(
                asProperty(SHACL.path),
                shaclModel.createList(shaclModel.createResource(propertyUri), RDF.type));
        propertyShape.addProperty(
                asProperty(SHACL.severity), shaclModel.asRDFNode(SHACL.Violation));

        return propertyShape;
    }

    private Property asProperty(Node predicate) {
        return shaclModel.createProperty(predicate.getURI());
    }
}

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
import org.apache.jena.rdf.model.AnonId;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.system.PrefixEntry;
import org.apache.jena.shacl.vocabulary.SHACL;
import org.apache.jena.vocabulary.RDF;
import org.rdfarchitect.models.cim.data.dto.relations.uri.URI;

@Setter
@Accessors(chain = true)
public class InverseCardinalityPropertyShapeBuilder {

    private static final String DESCRIPTION_TEMPLATE =
            "This constraint validates the cardinality of the association at the inverse direction.";
    private static final String MESSAGE_REQUIRED_TEMPLATE =
            "Missing required property (association).";
    private static final String MESSAGE_UPPER_BOUND_N_TEMPLATE =
            "Cardinality violation (association). Upper bound shall be %s.";
    private static final String MESSAGE_GENERIC_TEMPLATE =
            "Cardinality violation %d..%d (association).";

    // Set by user
    private PrefixEntry prefixEntry;
    private String propertyUri;
    private String inversePropertyUri;
    private String propertyGroupUri;
    private double order;
    private Integer lowerBound;
    private Integer upperBound;

    // Internal state
    private final Model shaclModel;

    public InverseCardinalityPropertyShapeBuilder(Model resultModel) {
        this.shaclModel = resultModel;
    }

    public Resource build() {
        if ((lowerBound == null || lowerBound == 0)
                && upperBound
                        == null) { // cardinality is set to be unbounded, so we don't need a shape
            return null;
        }

        var label = new URI(propertyUri).getSuffix();
        var shapeName = shaclModel.createLiteral(label + "-InverseCardinality");
        var shapeUri = prefixEntry.getUri() + shapeName.getString();
        var propertyShape = shaclModel.createResource(shapeUri);

        propertyShape.addProperty(RDF.type, shaclModel.asRDFNode(SHACL.PropertyShape));
        propertyShape.addProperty(asProperty(SHACL.description), description());
        propertyShape.addProperty(
                asProperty(SHACL.group), shaclModel.createResource(propertyGroupUri));
        propertyShape.addProperty(asProperty(SHACL.name), shapeName);
        propertyShape.addLiteral(
                asProperty(SHACL.order),
                shaclModel.createTypedLiteral(order, XSDDatatype.XSDdecimal));
        propertyShape.addProperty(asProperty(SHACL.path), path());
        propertyShape.addProperty(
                asProperty(SHACL.severity), shaclModel.asRDFNode(SHACL.Violation));

        if (lowerBound != null && lowerBound > 0) {
            propertyShape.addLiteral(
                    asProperty(SHACL.minCount),
                    shaclModel.createTypedLiteral(lowerBound, XSDDatatype.XSDinteger));
        }
        if (upperBound != null) {
            propertyShape.addLiteral(
                    asProperty(SHACL.maxCount),
                    shaclModel.createTypedLiteral(upperBound, XSDDatatype.XSDinteger));
        }
        propertyShape.addProperty(asProperty(SHACL.message), message());

        return propertyShape;
    }

    private Resource path() {
        var anonId = AnonId.create();
        var blankNode = shaclModel.createResource(anonId);
        blankNode.addProperty(
                shaclModel.createProperty(SHACL.inversePath.getURI()),
                shaclModel.createResource(inversePropertyUri));
        return blankNode;
    }

    private Property asProperty(Node predicate) {
        return shaclModel.createProperty(predicate.getURI());
    }

    private Literal description() {
        return shaclModel.createLiteral(DESCRIPTION_TEMPLATE);
    }

    private Literal message() {
        if ((lowerBound == null || lowerBound == 0) && upperBound == null) {
            throw new IllegalArgumentException("Cardinality is set to be unbounded.");
        }
        if (lowerBound == null) {
            throw new IllegalArgumentException("Lower bound is not defined.");
        }
        if (lowerBound == 0) {
            return shaclModel.createLiteral(
                    String.format(MESSAGE_UPPER_BOUND_N_TEMPLATE, upperBound));
        }
        if (upperBound == null || (lowerBound.equals(upperBound) && upperBound == 1)) {
            return shaclModel.createLiteral(String.format(MESSAGE_REQUIRED_TEMPLATE));
        }
        return shaclModel.createLiteral(
                String.format(MESSAGE_GENERIC_TEMPLATE, lowerBound, upperBound));
    }
}

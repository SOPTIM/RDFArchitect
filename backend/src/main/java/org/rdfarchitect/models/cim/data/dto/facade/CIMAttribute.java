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

package org.rdfarchitect.models.cim.data.dto.facade;

import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.vocabulary.RDFS;
import org.rdfarchitect.models.cim.data.dto.relations.CIMSIsDefault;
import org.rdfarchitect.models.cim.data.dto.relations.CIMSIsFixed;
import org.rdfarchitect.models.cim.data.dto.relations.CIMSMultiplicity;
import org.rdfarchitect.models.cim.data.dto.relations.CIMSStereotype;
import org.rdfarchitect.models.cim.data.dto.relations.uri.URI;
import org.rdfarchitect.models.cim.rdf.resources.CIMS;
import java.util.UUID;

public class CIMAttribute extends CIMResource implements ICIMAttribute {

    private static final Property LITERAL_WRAPPER_PROPERTY =
            ResourceFactory.createProperty(RDFS.Literal.getURI());

    public CIMAttribute(String graphUri, Model model, UUID uuid) {
        super(graphUri, model, uuid);
    }

    public CIMAttribute(String graphUri, Model model, Resource resource) {
        super(graphUri, model, resource);
    }

    @Override
    public ICIMClass getDomain() {
        var domain = getRequiredUniqueJenaProperty(RDFS.domain);
        return CIMClass.fromResource(getGraphUri(), getModel(), domain);
    }

    @Override
    public CIMSMultiplicity getMultiplicity() {
        var multiplicity = getRequiredUniqueJenaProperty(CIMS.multiplicity);
        return new CIMSMultiplicity(multiplicity.getURI());
    }

    @Override
    public ICIMClass getDataType() {
        var dataType = getUniqueJenaProperty(CIMS.datatype);
        if(dataType == null){
            dataType = getUniqueJenaProperty(RDFS.range);
        }
        if(dataType == null){
            throw new IllegalStateException("No " + CIMS.datatype + " or " + RDFS.range + " found for attribute with UUID " + getUuid() + ".");
        }
        return CIMClass.fromResource(getGraphUri(), getModel(), dataType);
    }

    @Override
    public CIMSStereotype getStereotype() {
        var stereotypes = getStereotypeList();
        if(stereotypes.isEmpty()){
            throw new IllegalStateException("Required property " + CIMS.stereotype + " not found for attribute with UUID " + getUuid() + ".");
        }
        return stereotypes.getFirst();
    }

    @Override
    public CIMSIsFixed getFixed() {
        var valueNode = readValueNode(CIMS.isFixed);
        if(valueNode == null){
            return null;
        }
        return new CIMSIsFixed(valueNode.value(), valueNode.dataType(), valueNode.blankNode());
    }

    @Override
    public CIMSIsDefault getDefault() {
        var valueNode = readValueNode(CIMS.isDefault);
        if(valueNode == null){
            return null;
        }
        return new CIMSIsDefault(valueNode.value(), valueNode.dataType(), valueNode.blankNode());
    }

    private record ValueNode(String value, URI dataType, boolean blankNode) {}

    private ValueNode readValueNode(Property property) {
        var node = getUniqueJenaPropertyNode(property);
        if(node == null){
            return null;
        }
        if(node.isLiteral()){
            return toValueNode(node.asLiteral(), false);
        }
        if(node.isAnon()){
            var inner = node.asResource().getProperty(LITERAL_WRAPPER_PROPERTY);
            if(inner != null && inner.getObject().isLiteral()){
                return toValueNode(inner.getObject().asLiteral(), true);
            }
        }
        return null;
    }

    private static ValueNode toValueNode(Literal literal, boolean blankNode) {
        var dataTypeUri = literal.getDatatypeURI();
        var dataType = dataTypeUri == null || dataTypeUri.isEmpty() ? null : new URI(dataTypeUri);
        return new ValueNode(literal.getLexicalForm(), dataType, blankNode);
    }
}

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

package org.rdfarchitect.services.update.classes.attributes;

import lombok.experimental.UtilityClass;
import org.apache.jena.datatypes.BaseDatatype;
import org.apache.jena.query.TxnType;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.ResIterator;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.XSD;
import org.rdfarchitect.cim.data.dto.CIMAttribute;
import org.rdfarchitect.cim.data.dto.relations.AttributeValueNode;
import org.rdfarchitect.cim.data.dto.relations.datatype.CIMSDataType;
import org.rdfarchitect.cim.data.dto.relations.uri.URI;
import org.rdfarchitect.cim.rdf.resources.CIMS;
import org.rdfarchitect.cim.rdf.resources.CIMStereotypes;
import org.rdfarchitect.cim.rdf.resources.RDFA;
import org.rdfarchitect.config.AttributeValueConfig;
import org.rdfarchitect.rdf.graph.wrapper.GraphRewindableWithUUIDs;
import org.rdfarchitect.shacl.XSDDatatypeMapper;

import java.util.List;
import java.util.UUID;

@UtilityClass
public class AttributeFixedDefaultResolver {

    public void apply(GraphRewindableWithUUIDs graph, CIMAttribute attribute) {
        if (graph == null) {
            return;
        }
        var startedReadTx = false;
        if (!graph.isInTransaction()) {
            graph.begin(TxnType.READ);
            startedReadTx = true;
        }
        try {
            var model = ModelFactory.createModelForGraph(graph);
            apply(model, attribute);
        } finally {
            if (startedReadTx) {
                graph.end();
            }
        }
    }

    public void apply(GraphRewindableWithUUIDs graph, List<CIMAttribute> attributes) {
        if (graph == null) {
            return;
        }
        var startedReadTx = false;
        if (!graph.isInTransaction()) {
            graph.begin(TxnType.READ);
            startedReadTx = true;
        }
        try {
            var model = ModelFactory.createModelForGraph(graph);
            apply(model, attributes);
        } finally {
            if (startedReadTx) {
                graph.end();
            }
        }
    }

    public void apply(Model model, CIMAttribute attribute) {
        if (model == null || attribute == null) {
            return;
        }
        if (attribute.getFixedValue() != null) {
            applyValueMetadata(model, attribute, attribute.getFixedValue(), CIMS.isFixed);
        }
        if (attribute.getDefaultValue() != null) {
            applyValueMetadata(model, attribute, attribute.getDefaultValue(), CIMS.isDefault);
        }
    }

    public void apply(Model model, List<CIMAttribute> attributes) {
        if (model == null || attributes == null || attributes.isEmpty()) {
            return;
        }
        for (var attribute : attributes) {
            apply(model, attribute);
        }
    }

    private void applyValueMetadata(Model model, CIMAttribute attribute, AttributeValueNode valueNode, org.apache.jena.rdf.model.Property predicate) {
        var metadata = readExistingValueMetadata(model, attribute.getUuid(), predicate);
        if (metadata != null) {
            valueNode.setBlankNode(metadata.blankNode());
            valueNode.setUriValue(metadata.uriValue());
            valueNode.setBlankNodePredicate(metadata.blankNodePredicate());
        } else {
            applyNewValueDefaults(valueNode);
        }
        if (valueNode.isUriValue()) {
            valueNode.setDataType(null);
            return;
        }
        valueNode.setDataType(resolveFixedDefaultDatatype(model, attribute.getDataType()));
    }

    private void applyNewValueDefaults(AttributeValueNode valueNode) {
        if (valueNode == null) {
            return;
        }
        if (AttributeValueConfig.isNewValuesBlankNode() && !valueNode.isBlankNode()) {
            valueNode.setBlankNode(true);
        }
        if (valueNode.isBlankNode() && valueNode.getBlankNodePredicate() == null) {
            valueNode.setBlankNodePredicate(new URI(RDFS.Literal.getURI()));
        }
    }

    private record ExistingValueMetadata(boolean blankNode, boolean uriValue, URI blankNodePredicate) {
    }

    private ExistingValueMetadata readExistingValueMetadata(Model model, UUID attributeUuid, org.apache.jena.rdf.model.Property predicate) {
        var attributeResource = findAttributeResource(model, attributeUuid);
        if (attributeResource == null) {
            return null;
        }
        Statement stmt = attributeResource.getProperty(predicate);
        if (stmt == null) {
            return null;
        }
        RDFNode object = stmt.getObject();
        if (object.isAnon()) {
            var propertyIt = object.asResource().listProperties();
            try {
                if (propertyIt.hasNext()) {
                    var innerStmt = propertyIt.next();
                    var uriValue = innerStmt.getObject().isURIResource();
                    var blankNodePredicate = new URI(innerStmt.getPredicate().getURI());
                    return new ExistingValueMetadata(true, uriValue, blankNodePredicate);
                }
            } finally {
                propertyIt.close();
            }
            return new ExistingValueMetadata(true, false, null);
        }
        if (object.isURIResource()) {
            return new ExistingValueMetadata(false, true, null);
        }
        return new ExistingValueMetadata(false, false, null);
    }

    private Resource findAttributeResource(Model model, UUID attributeUuid) {
        if (attributeUuid == null) {
            return null;
        }
        ResIterator it = model.listSubjectsWithProperty(RDFA.uuid, model.createLiteral(attributeUuid.toString()));
        try {
            return it.hasNext() ? it.nextResource() : null;
        } finally {
            it.close();
        }
    }

    private URI resolveFixedDefaultDatatype(Model model, CIMSDataType dataType) {
        if (dataType == null || dataType.getUri() == null) {
            return defaultXsdString();
        }
        var dataTypeUri = dataType.getUri().toString();
        if (dataTypeUri.startsWith(XSD.getURI())) {
            return new URI(dataTypeUri);
        }
        var datatypeResource = model.getResource(dataTypeUri);
        if (datatypeResource.hasProperty(CIMS.stereotype, model.createLiteral(CIMStereotypes.primitiveString))) {
            return mapLabelToXsd(extractLabel(datatypeResource));
        }
        if (datatypeResource.hasProperty(CIMS.stereotype, model.createLiteral(CIMStereotypes.cimDatatypeString))) {
            var valueDatatypeResource = findValueDatatypeResource(model, datatypeResource);
            return mapLabelToXsd(extractLabel(valueDatatypeResource));
        }
        return mapLabelToXsd(extractLabel(datatypeResource));
    }

    private Resource findValueDatatypeResource(Model model, Resource datatypeResource) {
        if (datatypeResource == null) {
            return null;
        }
        ResIterator it = model.listResourcesWithProperty(RDFS.domain, datatypeResource);
        try {
            while (it.hasNext()) {
                var valueAttribute = it.next();
                if (!isValueAttribute(valueAttribute)) {
                    continue;
                }
                var datatype = valueAttribute.getPropertyResourceValue(CIMS.datatype);
                if (datatype == null) {
                    datatype = valueAttribute.getPropertyResourceValue(RDFS.range);
                }
                if (datatype != null) {
                    return datatype;
                }
            }
        } finally {
            it.close();
        }
        return null;
    }

    private boolean isValueAttribute(Resource resource) {
        if (resource == null) {
            return false;
        }
        var labels = resource.listProperties(RDFS.label);
        try {
            while (labels.hasNext()) {
                var labelNode = labels.next().getObject();
                if (labelNode.isLiteral() && "value".equalsIgnoreCase(labelNode.asLiteral().getString())) {
                    return true;
                }
            }
        } finally {
            labels.close();
        }
        return false;
    }

    private String extractLabel(Resource resource) {
        if (resource == null) {
            return null;
        }
        var labelStmt = resource.getProperty(RDFS.label);
        if (labelStmt != null && labelStmt.getObject().isLiteral()) {
            return labelStmt.getObject().asLiteral().getString();
        }
        var uri = resource.getURI();
        if (uri == null) {
            return null;
        }
        return new URI(uri).getSuffix();
    }

    private URI mapLabelToXsd(String label) {
        if (label == null || label.isBlank()) {
            return defaultXsdString();
        }
        var rdfDatatype = XSDDatatypeMapper.classLabelToDatatype(label);
        if (rdfDatatype.getClass().equals(BaseDatatype.class)) {
            return defaultXsdString();
        }
        return new URI(rdfDatatype.getURI());
    }

    private URI defaultXsdString() {
        return new URI(XSD.xstring.getURI());
    }
}

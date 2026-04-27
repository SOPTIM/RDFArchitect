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

import org.apache.jena.datatypes.TypeMapper;
import org.apache.jena.query.TxnType;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.ResIterator;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.XSD;
import org.rdfarchitect.models.cim.ValueNodeParser;
import org.rdfarchitect.models.cim.data.dto.CIMAttribute;
import org.rdfarchitect.models.cim.data.dto.relations.AttributeValueNode;
import org.rdfarchitect.models.cim.data.dto.relations.datatype.CIMSDataType;
import org.rdfarchitect.models.cim.data.dto.relations.uri.URI;
import org.rdfarchitect.models.cim.rdf.resources.CIMS;
import org.rdfarchitect.models.cim.rdf.resources.CIMStereotypes;
import org.rdfarchitect.models.cim.rdf.resources.RDFA;
import org.rdfarchitect.rdf.graph.wrapper.GraphRewindableWithUUIDs;
import org.rdfarchitect.shacl.XSDDatatypeMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Resolves the {@code blankNode} flag and the XSD {@code dataType} for an attribute's fixed/default
 * value before it is written to the graph.
 *
 * <p>Resolution rules:
 *
 * <ul>
 *   <li>If the attribute already exists in the graph and its existing fixed/default value is a
 *       blank-node wrapper, the new value keeps {@code blankNode = true} regardless of the
 *       configured default. Symmetrically, an existing direct literal stays a literal.
 *   <li>For new attributes (or attributes with no existing value of the matching predicate), the
 *       {@code blankNode} flag defaults to the value of {@code attributes.newValuesBlankNode}.
 *   <li>The XSD datatype is derived from the attribute's {@link CIMSDataType} (direct XSD,
 *       primitive stereotype, CIMDatatype {@code value} attribute) with a fallback to {@code
 *       xsd:string}.
 * </ul>
 *
 * <p>Reading from the graph requires a Jena transaction. {@link #resolve} self-manages a {@link
 * TxnType#READ} transaction when no transaction is currently open, and reads inline otherwise.
 */
@Service
public class AttributeFixedDefaultResolver {

    private final boolean newValuesBlankNode;

    public AttributeFixedDefaultResolver(
            @Value("${attributes.newValuesBlankNode:false}") boolean newValuesBlankNode) {
        this.newValuesBlankNode = newValuesBlankNode;
    }

    public void resolve(GraphRewindableWithUUIDs graph, CIMAttribute attribute) {
        if (graph == null || attribute == null) {
            return;
        }
        runInReadTxn(graph, model -> resolveAttribute(model, attribute));
    }

    public void resolve(GraphRewindableWithUUIDs graph, List<CIMAttribute> attributes) {
        if (graph == null || attributes == null || attributes.isEmpty()) {
            return;
        }
        runInReadTxn(
                graph,
                model -> {
                    for (var attribute : attributes) {
                        resolveAttribute(model, attribute);
                    }
                });
    }

    /**
     * Runs {@code action} against the model backed by {@code graph}, opening a READ transaction
     * when none is currently active.
     */
    private void runInReadTxn(
            GraphRewindableWithUUIDs graph, java.util.function.Consumer<Model> action) {
        if (graph.isInTransaction()) {
            action.accept(ModelFactory.createModelForGraph(graph));
            return;
        }
        try {
            graph.begin(TxnType.READ);
            action.accept(ModelFactory.createModelForGraph(graph));
        } finally {
            graph.end();
        }
    }

    private void resolveAttribute(Model model, CIMAttribute attribute) {
        if (attribute.getFixedValue() != null) {
            resolveValueMetadata(model, attribute, attribute.getFixedValue(), CIMS.isFixed);
        }
        if (attribute.getDefaultValue() != null) {
            resolveValueMetadata(model, attribute, attribute.getDefaultValue(), CIMS.isDefault);
        }
    }

    private void resolveValueMetadata(
            Model model, CIMAttribute attribute, AttributeValueNode value, Property predicate) {
        var existingBlankNode = readExistingBlankNodeFlag(model, attribute.getUuid(), predicate);
        if (existingBlankNode.isPresent()) {
            value.setBlankNode(existingBlankNode.get());
        } else if (newValuesBlankNode) {
            value.setBlankNode(true);
        }
        value.setDataType(resolveFixedDefaultDatatype(model, attribute.getDataType()));
    }

    /**
     * Returns whether the attribute identified by {@code attributeUuid} currently has a blank-node
     * wrapper for {@code predicate} in the graph, or {@link Optional#empty()} if there is no such
     * attribute / no such value triple. Delegates blank-node validation to {@link ValueNodeParser}
     * so the shape rules stay in a single place.
     */
    private Optional<Boolean> readExistingBlankNodeFlag(
            Model model, UUID attributeUuid, Property predicate) {
        var resource = findAttributeResource(model, attributeUuid);
        if (resource == null) {
            return Optional.empty();
        }
        var stmt = resource.getProperty(predicate);
        if (stmt == null) {
            return Optional.empty();
        }
        return Optional.of(ValueNodeParser.parse(stmt.getObject(), model).blankNode());
    }

    private Resource findAttributeResource(Model model, UUID attributeUuid) {
        if (attributeUuid == null) {
            return null;
        }
        ResIterator it =
                model.listSubjectsWithProperty(
                        RDFA.uuid, model.createLiteral(attributeUuid.toString()));
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
            return mapLabelToXsd(new URI(dataTypeUri).getSuffix());
        }
        var datatypeResource = model.getResource(dataTypeUri);
        if (datatypeResource.hasProperty(
                CIMS.stereotype, model.createLiteral(CIMStereotypes.primitiveString))) {
            return mapLabelToXsd(extractLabel(datatypeResource));
        }
        if (datatypeResource.hasProperty(
                CIMS.stereotype, model.createLiteral(CIMStereotypes.cimDatatypeString))) {
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
                if (labelNode.isLiteral()
                        && "value".equalsIgnoreCase(labelNode.asLiteral().getString())) {
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
        if (TypeMapper.getInstance().getTypeByName(rdfDatatype.getURI()) == null) {
            return defaultXsdString();
        }
        return new URI(rdfDatatype.getURI());
    }

    private URI defaultXsdString() {
        return new URI(XSD.xstring.getURI());
    }
}

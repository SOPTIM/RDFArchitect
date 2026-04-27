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

package org.rdfarchitect.models.cim.relations.model.properties;

import lombok.experimental.UtilityClass;

import org.apache.jena.datatypes.RDFDatatype;
import org.apache.jena.datatypes.TypeMapper;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.rdfarchitect.models.cim.rdf.resources.CIMS;
import org.rdfarchitect.models.cim.rdf.resources.CIMStereotypes;
import org.rdfarchitect.shacl.XSDDatatypeMapper;

import java.util.List;
import java.util.Set;

@UtilityClass
public class CIMAttributeUtils {

    private static final String PRIMITIVE_DATATYPE_QUERY =
            """
              PREFIX cims:    <http://iec.ch/TC57/1999/rdf-schema-extensions-19990926#>
              PREFIX rdf:     <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
              PREFIX rdfs:    <http://www.w3.org/2000/01/rdf-schema#>

              SELECT DISTINCT ?valueDatatype
              WHERE {
                  <ATTRIBUTE_URI> cims:dataType ?datatype .
                  ?valueAttribute  rdfs:domain ?datatype;
                              rdfs:label "value"@en;
                              cims:dataType ?valueDatatype.
              }
              """;

    /**
     * Checks whether a property is a primitive attribute or not.
     *
     * @param attribute the attribute to check
     * @return true if it's a primitive attribute, false if not
     */
    public boolean hasPrimitiveDatatype(Resource attribute) {
        if (!CIMPropertyUtils.isAttribute(attribute) || !attribute.hasProperty(CIMS.datatype)) {
            return false;
        }
        var datatype = attribute.getProperty(CIMS.datatype).getResource();
        return datatype.hasProperty(
                CIMS.stereotype,
                attribute.getModel().createLiteral(CIMStereotypes.primitiveString));
    }

    /**
     * Checks whether an attribute has a CIMDatatype or not
     *
     * @param attribute the attribute
     * @return true, if the datatype is a CIMDatatype, false if it's an enum
     */
    public boolean hasCIMDatatype(Resource attribute) {
        if (!CIMPropertyUtils.isAttribute(attribute) || !attribute.hasProperty(CIMS.datatype)) {
            return false;
        }
        var datatype = attribute.getProperty(CIMS.datatype).getResource();
        return datatype.hasProperty(
                CIMS.stereotype,
                attribute.getModel().createLiteral(CIMStereotypes.cimDatatypeString));
    }

    /**
     * Checks whether an attribute has an XSDDatatype as its datatype
     *
     * @param attribute the attribute to check
     */
    public boolean hasXSDDatatype(Resource attribute) {
        if (!CIMPropertyUtils.isAttribute(attribute) || !attribute.hasProperty(CIMS.datatype)) {
            return false;
        }
        var datatype = attribute.getProperty(CIMS.datatype).getResource().getURI();
        return TypeMapper.getInstance().getTypeByName(datatype) != null;
    }

    /**
     * Checks whether an attribute has an enum as its datatype.
     *
     * @param attribute the attribute to check
     * @return true if the attribute has an enum as its datatype, false if not
     */
    public boolean hasEnumAttribute(Resource attribute) {
        if (!CIMPropertyUtils.isAttribute(attribute)
                || !(attribute.hasProperty(RDFS.range) || attribute.hasProperty(CIMS.datatype))) {
            return false;
        }
        var datatype = attribute.getProperty(RDFS.range).getResource();
        return datatype.hasProperty(CIMS.stereotype, CIMStereotypes.enumeration);
    }

    public boolean isStatementAttribute(Resource attribute) {
        if (!CIMPropertyUtils.isAttribute(attribute) || !attribute.hasProperty(CIMS.datatype)) {
            return false;
        }
        var datatype = attribute.getProperty(CIMS.datatype).getResource();
        return datatype.hasProperty(CIMS.stereotype, CIMStereotypes.rdf);
    }

    /**
     * fetches the primitive datatype of an attribute, Allows for primitive mappable datatypes and
     * CIMDatatypes containing a value attribute.
     *
     * @param attribute the attribute
     * @return the primitive datatype of the attribute
     */
    public RDFDatatype getPrimitiveDatatype(Resource attribute) {
        var ontology = attribute.getModel();
        if (hasPrimitiveDatatype(attribute)) {
            var datatype =
                    attribute
                            .getProperty(ontology.createProperty(CIMS.datatype.getURI()))
                            .getResource();
            var label = datatype.getProperty(RDFS.label).getObject().asLiteral().getString();
            return XSDDatatypeMapper.classLabelToDatatype(label);
        }
        if (hasCIMDatatype(attribute)) {
            var query = PRIMITIVE_DATATYPE_QUERY.replace("ATTRIBUTE_URI", attribute.getURI());
            try (var quexec = QueryExecutionFactory.create(query, ontology)) {
                var resultSet = quexec.execSelect();
                if (resultSet.hasNext()) {
                    var resultLabel =
                            resultSet
                                    .next()
                                    .getResource("valueDatatype")
                                    .getProperty(RDFS.label)
                                    .getObject()
                                    .asLiteral()
                                    .getString();
                    return XSDDatatypeMapper.classLabelToDatatype(resultLabel);
                }
            }
            return XSDDatatype.XSDstring; // default value, but should never happen
        }
        if (hasXSDDatatype(attribute)) {
            var datatypeUri = attribute.getProperty(CIMS.datatype).getResource().getURI();
            return TypeMapper.getInstance().getTypeByName(datatypeUri);
        }
        throw new IllegalArgumentException("Property is not a primitive attribute or CIMDatatype");
    }

    /**
     * List all possible values an attribute, with an enum as its type, can have
     *
     * @param attribute the attribute
     * @return a set containing the allowed enum entry uris
     */
    public Set<Resource> listEnumDatatypeEntries(Resource attribute) {
        if (!CIMPropertyUtils.isAttribute(attribute) || !hasEnumAttribute(attribute)) {
            throw new IllegalArgumentException(
                    "Property is not an attribute or does not have a datatype");
        }
        var ontology = attribute.getModel();
        var enumClass = attribute.getProperty(RDFS.range).getResource();
        return ontology.listResourcesWithProperty(RDF.type, enumClass).toSet();
    }

    /**
     * Lists all attributes that use a given class as their datatype (via {@code cims:datatype} or
     * {@code rdfs:range}).
     *
     * @param classResource the class resource used as datatype
     * @return a list of attribute resources that reference the class as their datatype
     */
    public List<Resource> listAttributesWithClassAsDatatype(Resource classResource) {
        var model = classResource.getModel();
        var byDatatype = model.listSubjectsWithProperty(CIMS.datatype, classResource);
        var byRange = model.listSubjectsWithProperty(RDFS.range, classResource);
        return byDatatype
                .andThen(byRange)
                .filterKeep(CIMPropertyUtils::isAttribute)
                .toList()
                .stream()
                .distinct()
                .toList();
    }
}

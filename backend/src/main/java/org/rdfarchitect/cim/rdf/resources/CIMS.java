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

package org.rdfarchitect.cim.rdf.resources;

import lombok.experimental.UtilityClass;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.vocabulary.RDF;
import org.rdfarchitect.cim.data.dto.CIMAssociation;

/**
 * Class containing CIM RDF-predicates as {@link Property properties}.
 */
@UtilityClass
public class CIMS {

    public final String namespace = "http://iec.ch/TC57/1999/rdf-schema-extensions-19990926#";

    //predicates
    /**
     * If set defines the fixed value of the resource. If not set the value of the resource is not fixed.
     */
    public final Property isFixed = ResourceFactory.createProperty(namespace + "isFixed");

    /**
     * If set defines the default value of the resource. If not set the value of the resource has no custom default value.
     */
    public final Property isDefault = ResourceFactory.createProperty(namespace + "isDefault");

    /**
     * Defines the stereotypes of a resource.
     */
    public final Property stereotype = ResourceFactory.createProperty(namespace + "stereotype");

    /**
     * Defines the data type of a resource.
     */
    public final Property datatype = ResourceFactory.createProperty(namespace + "dataType");

    /**
     * Defines the Multiplicity of a resource.
     */
    public final Property multiplicity = ResourceFactory.createProperty(namespace + "multiplicity");

    /**
     * Defines whether a {@link CIMAssociation CIMAssociation} points to its counterpart or
     * if it's only used as a reference for another {@link CIMAssociation CIMAssociation}.
     */
    public final Property associationUsed = ResourceFactory.createProperty(namespace + "AssociationUsed");

    /**
     * Defines the resource uri of the inverse association of an association.
     */
    public final Property inverseRoleName = ResourceFactory.createProperty(namespace + "inverseRoleName");

    /**
     * Defines the {@link #classCategory} a resource belongs to.
     */
    public final Property belongsToCategory = ResourceFactory.createProperty(namespace + "belongsToCategory");

    //objects
    /**
     * If a resource is of {@link RDF#type} {@link #classCategory} it is a classCategory/Package.
     */
    public final Resource classCategory = ResourceFactory.createResource(namespace + "ClassCategory");


    public final Literal yes = ResourceFactory.createPlainLiteral("Yes");

    public final Literal no = ResourceFactory.createPlainLiteral("No");
}

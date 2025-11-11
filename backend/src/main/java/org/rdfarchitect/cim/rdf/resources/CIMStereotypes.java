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
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;

/**
 * Class containing stereotype {@link Resource resources} for CIM.
 */
@UtilityClass
public class CIMStereotypes {

    public final String attributeString = "http://iec.ch/TC57/NonStandard/UML#attribute";

    public final Resource attribute = ResourceFactory.createResource(attributeString);

    public final String enumerationString = "http://iec.ch/TC57/NonStandard/UML#enumeration";

    public final Resource enumeration = ResourceFactory.createResource(enumerationString);

    public final String concreteString = "http://iec.ch/TC57/NonStandard/UML#concrete";

    public final Resource concrete = ResourceFactory.createResource(concreteString);

    public final String cimDatatypeString = "CIMDatatype";

    public final Literal cimDataType = ResourceFactory.createPlainLiteral(cimDatatypeString);

    public final String descriptionString = "Description";

    public final Literal description = ResourceFactory.createPlainLiteral(descriptionString);

    public final String primitiveString = "Primitive";

    public final Literal primitive = ResourceFactory.createPlainLiteral(primitiveString);

    public final String enumLiteralString = "enum";

    public final Literal enumLiteral = ResourceFactory.createPlainLiteral(enumLiteralString);

    public final String rdfString = "rdf";

    public final Literal rdf = ResourceFactory.createPlainLiteral(rdfString);
}

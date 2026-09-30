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

package org.rdfarchitect.services.validation.workspace.rule;

import lombok.experimental.UtilityClass;

import org.rdfarchitect.models.cim.data.dto.facade.ICIMAttribute;
import org.rdfarchitect.models.cim.data.dto.facade.ICIMClass;
import org.rdfarchitect.models.cim.data.dto.relations.CIMSStereotype;
import org.rdfarchitect.models.cim.rdf.resources.CIMStereotypes;

import java.util.List;
import java.util.Set;

@UtilityClass
public class DatatypeStereotypes {

    private final Set<String> DATATYPE_STEREOTYPES =
            Set.of(
                    CIMStereotypes.cimDatatypeString,
                    CIMStereotypes.primitiveString,
                    CIMStereotypes.compoundString,
                    CIMStereotypes.enumerationString);

    /**
     * @return the datatype stereotypes of the class, sorted, empty for an ordinary class
     */
    public List<String> datatypeStereotypesOf(ICIMClass cimClass) {
        return stereotypesOf(cimClass).stream()
                .filter(DATATYPE_STEREOTYPES::contains)
                .sorted()
                .toList();
    }

    /**
     * @return true when the class is a datatype, that is a CIMDatatype, Primitive, Compound or
     *     enumeration
     */
    public boolean isDatatype(ICIMClass cimClass) {
        return !datatypeStereotypesOf(cimClass).isEmpty();
    }

    /**
     * @return true when the attribute belongs to a datatype, such as {@code ActivePower.value}
     */
    public boolean isDatatypeAttribute(ICIMAttribute attribute) {
        return isDatatype(attribute.getDomain());
    }

    /**
     * @return true when the class carries the concrete stereotype, so instances of it exist
     */
    public boolean isConcrete(ICIMClass cimClass) {
        return stereotypesOf(cimClass).contains(CIMStereotypes.concreteString);
    }

    private List<String> stereotypesOf(ICIMClass cimClass) {
        return cimClass.getStereotypes().stream().map(CIMSStereotype::getStereotype).toList();
    }
}

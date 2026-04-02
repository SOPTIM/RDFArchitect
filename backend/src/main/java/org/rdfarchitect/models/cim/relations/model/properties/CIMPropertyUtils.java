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
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.rdfarchitect.models.cim.data.dto.relations.uri.URI;
import org.rdfarchitect.models.cim.rdf.resources.CIMS;
import org.rdfarchitect.models.cim.rdf.resources.CIMStereotypes;

@UtilityClass
public class CIMPropertyUtils {

    /**
     * Checks whether a resource is a property
     *
     * @param resource the resource to check
     *
     * @return true if it's a property, otherwise false
     */
    public boolean isNotProperty(Resource resource) {
        return !resource.hasProperty(RDF.type, RDF.Property);
    }

    /**
     * Checks whether a property is an association or not
     *
     * @param property the property to check
     *
     * @return true if it's an association, otherwise false
     */
    public boolean isAssociation(Resource property) {
        if (isNotProperty(property)) {
            return false;
        }
        return property.hasProperty(CIMS.inverseRoleName) &&
                  property.hasProperty(CIMS.associationUsed);
    }

    /**
     * Checks whether a property is an attribute or not
     *
     * @param property the property to check
     *
     * @return true if it's an attribute, otherwise false
     */
    public boolean isAttribute(Resource property) {
        if (isNotProperty(property)) {
            return false;
        }
        return property.hasProperty(CIMS.stereotype, CIMStereotypes.attribute);
    }

    /**
     * resolves the multiplicity of a property and returns a {@link Multiplicity} object containing the upper and lower bound
     *
     * @param property the property to resolve the multiplicity for
     *
     * @return {@link Multiplicity}
     */
    public Multiplicity resolveMultiplicity(Resource property) {
        var multiplicityString = property.getProperty(CIMS.multiplicity).getResource().getURI();
        var multiplicity = new URI(multiplicityString).getSuffix();
        return resolveMultiplicity(multiplicity);
    }

    /**
     * resolves the multiplicity of a property based only on the multiplicity string and returns a {@link Multiplicity} object containing the upper and lower bound
     *
     * @param multiplicityString the string representing the multiplicity
     *
     * @return {@link Multiplicity}
     */
    public Multiplicity resolveMultiplicity(String multiplicityString) {
        var value = multiplicityString.substring(2); //crop "M:" from start
        if (value.contains("..")) {
            var parts = value.split("\\.\\.");
            var lowerBound = parseMultiplicityBound(parts[0]);
            var upperBound = parseMultiplicityBound(parts[1]);
            return new Multiplicity(lowerBound, upperBound);
        } else {
            var lowerBound = parseMultiplicityBound(value);
            return new Multiplicity(lowerBound, lowerBound);
        }
    }

    /**
     * Checks whether a property is optional or required
     *
     * @param attribute the attribute
     *
     * @return true if the attribute is optional, false if required
     */
    public boolean isOptional(Resource attribute) {
        var multiplicity = CIMPropertyUtils.resolveMultiplicity(attribute);
        return isOptional(multiplicity);
    }

    /**
     * Checks whether a Multiplicity is optional or required
     *
     * @param multiplicity the Multiplicity
     *
     * @return true if the attribute is optional, false if required
     */
    public boolean isOptional(Multiplicity multiplicity) {
        return multiplicity.lowerBound() == null || multiplicity.lowerBound() == 0;
    }

    public record Multiplicity(Integer lowerBound, Integer upperBound){}

    private Integer parseMultiplicityBound(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException | NullPointerException _) {
            return null;
        }
    }
}

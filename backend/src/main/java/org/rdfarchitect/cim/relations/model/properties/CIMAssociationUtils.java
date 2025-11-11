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

package org.rdfarchitect.cim.relations.model.properties;

import lombok.experimental.UtilityClass;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDFS;
import org.rdfarchitect.cim.rdf.resources.CIMS;
import org.rdfarchitect.cim.relations.model.CIMClassUtils;

import java.util.Set;
import java.util.stream.Collectors;

@UtilityClass
public class CIMAssociationUtils {
    /**
     * Checks whether an association is used or not
     *
     * @param association the association to check
     * @return true if it's a used association, false if not
     */
    public boolean isUsedAssociation(Resource association) {
        return CIMPropertyUtils.isAssociation(association) &&
               association.hasProperty(CIMS.associationUsed, CIMS.yes);
    }

    /**
     * Lists all datatypes for an association property.
     * @param property the association property to list datatypes for
     * @return a set of resources representing the datatypes
     */
    public Set<Resource> listAssociationDatatypes(Resource property) {
        var targetClass = property.getModel()
                .getProperty(property, RDFS.range)
                .getResource();
        var instantiableDerivingClasses = CIMClassUtils.findDerivingClasses(targetClass)
                .stream()
                .filter(CIMClassUtils::isInstantiableClass)
                .collect(Collectors.toSet());
        instantiableDerivingClasses.add(targetClass); // include the class itself
        return instantiableDerivingClasses;
    }

}

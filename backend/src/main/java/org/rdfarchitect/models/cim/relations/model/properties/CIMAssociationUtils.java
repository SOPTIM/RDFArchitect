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

import org.apache.jena.graph.Graph;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDFS;
import org.rdfarchitect.models.cim.rdf.resources.CIMS;
import org.rdfarchitect.models.cim.relations.model.CIMClassUtils;
import org.rdfarchitect.models.cim.relations.model.CIMResourceUtils;

import java.util.List;
import java.util.Set;
import java.util.UUID;
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
        return CIMPropertyUtils.isAssociation(association)
                && association.hasProperty(CIMS.associationUsed, CIMS.yes);
    }

    /**
     * Lists all datatypes for an association property.
     *
     * @param property the association property to list datatypes for
     * @return a set of resources representing the datatypes
     */
    public Set<Resource> listAssociationDatatypes(Resource property) {
        var targetClass = property.getModel().getProperty(property, RDFS.range).getResource();
        var instantiableDerivingClasses =
                CIMClassUtils.findDerivingClasses(targetClass).stream()
                        .filter(CIMClassUtils::isInstantiableClass)
                        .collect(Collectors.toSet());
        instantiableDerivingClasses.add(targetClass); // include the class itself
        return instantiableDerivingClasses;
    }

    /**
     * Lists all associations that reference a given class via {@code rdfs:domain}.
     *
     * @param classResource the class resource to find referencing associations for
     * @return a list of association resources referencing the class
     */
    public List<Resource> listAssociationsReferencingClass(Resource classResource) {
        return classResource
                .getModel()
                .listSubjectsWithProperty(RDFS.domain, classResource)
                .filterKeep(CIMPropertyUtils::isAssociation)
                .toList();
    }

    /**
     * Returns the target (range) resource of an association.
     *
     * @param associationResource the association resource
     * @return the range resource of the association
     * @throws IllegalStateException if the association has no range or has a literal as range
     */
    public Resource getAssociationTarget(Resource associationResource) {
        var rangeStatement = associationResource.getProperty(RDFS.range);
        if (rangeStatement == null) {
            throw new IllegalStateException(
                    "Association " + associationResource + " does not have a range.");
        }
        if (rangeStatement.getObject().isLiteral()) {
            throw new IllegalStateException(
                    "Association "
                            + associationResource
                            + " has a literal as range, which is not supported.");
        }
        return rangeStatement.getObject().asResource();
    }

    /**
     * * The UUIDs of both ends of an association: its own UUID and the UUID of its inverse role.
     * Used * to know which resources a label may be anchored to for this association. * * @param
     * uuid the UUID of the association itself * @param inverseUuid the UUID of its inverse role
     */
    public record AssociationEndUuids(UUID uuid, UUID inverseUuid) {}

    /**
     * * Finds the {@link AssociationEndUuids} of every association whose domain is the given class.
     * * Used to diff a class's associations before and after a save, to detect added/removed *
     * associations. * * @param rdfGraph the graph to search in * @param classUUID the UUID of the
     * class whose outgoing associations are collected * @return the end UUIDs of every association
     * referencing the class as its domain
     */
    public Set<AssociationEndUuids> associationEndUuidsForClass(Graph rdfGraph, UUID classUUID) {
        var classResource = CIMResourceUtils.findResourceForUuid(rdfGraph, classUUID);
        return listAssociationsReferencingClass(classResource).stream()
                .map(
                        association ->
                                new AssociationEndUuids(
                                        CIMResourceUtils.findUuidForResource(association),
                                        CIMResourceUtils.findUuidForResource(
                                                association.getPropertyResourceValue(
                                                        CIMS.inverseRoleName))))
                .collect(Collectors.toSet());
    }
}

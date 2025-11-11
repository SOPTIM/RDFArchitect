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

package org.rdfarchitect.cim.relations.model;

import lombok.experimental.UtilityClass;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.rdfarchitect.cim.rdf.resources.CIMS;
import org.rdfarchitect.cim.rdf.resources.CIMStereotypes;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

@UtilityClass
public class CIMClassUtils {

    /**
     * Finds all deriving classes to a given class
     *
     * @param classResource the class resource to find deriving classes for
     * @return a set of classes that derive from the given class
     */
    public Set<Resource> findDerivingClasses(Resource classResource) {
        var queue = new LinkedList<Resource>();
        var uniqueClassUriSet = new HashSet<Resource>();
        queue.add(classResource);
        while (!queue.isEmpty()) {
            var current = queue.pop();
            var directlyDerivingClassUris = findDirectlyDerivingClasses(current);
            for (var derivingClassUri : directlyDerivingClassUris) {
                if (uniqueClassUriSet.add(derivingClassUri)) {
                    queue.add(derivingClassUri);
                }
            }
        }
        return uniqueClassUriSet;
    }

    /**
     * Finds all directly deriving classes of a given class URI.
     *
     * @param classResource class resource to find deriving classes for
     * @return a set of directly deriving classes
     */
    private Set<Resource> findDirectlyDerivingClasses(Resource classResource) {
        var ontology = classResource.getModel();
        return ontology.listResourcesWithProperty(RDFS.subClassOf, classResource)
                .mapWith(RDFNode::asResource)
                .toSet();
    }

    /**
     * returns a list of all superClasses of a specified class.
     *
     * @param classResource the class to find superClasses for
     * @return a set of all superClasses of the specified class
     */
    public Set<Resource> listSuperClasses(Resource classResource) {
        var queue = new LinkedList<Resource>();
        var classes = new HashSet<Resource>();
        queue.add(classResource);
        while (!queue.isEmpty()) {
            var superClasses = findDirectSuperClasses(queue.pop());
            for (var superClass : superClasses) {
                if (classes.add(superClass.asResource())) {
                    queue.add(superClass.asResource());
                }
            }
        }
        return classes;
    }

    /**
     * Finds all direct super classes of a given class URI.
     *
     * @param classResource the class resource to find direct super classes for
     * @return a set of direct super classes
     */
    public Set<Resource> findDirectSuperClasses(Resource classResource) {
        var ontology = classResource.getModel();
        return ontology.listObjectsOfProperty(classResource, RDFS.subClassOf)
                .mapWith(RDFNode::asResource)
                .toSet();
    }

    /**
     * List all properties belonging to a specified class. Even those through inheritance
     *
     * @param classResource the class
     * @return a set of Resources
     */
    public Set<Resource> listAllProperties(Resource classResource) {
        var ontology = classResource.getModel();
        var properties = ontology.listSubjectsWithProperty(RDFS.domain, classResource).toSet();
        var classes = CIMClassUtils.listSuperClasses(classResource);
        classes.add(classResource); // include the class itself
        classes.forEach(
                superClass -> properties.addAll(ontology.listSubjectsWithProperty(RDFS.domain, superClass).toSet())
        );
        return properties;
    }

    /**
     * Checks whether a class is instantiable.
     * @param classResource the class resource to check
     * @return true if the class is instantiable, false otherwise
     */
    public boolean isInstantiableClass(Resource classResource) {
        //check if the classResource is a valid resource
        if(classResource == null || !classResource.isURIResource()) {
            return false;
        }
        return classResource.hasProperty(RDF.type, RDFS.Class) &&                            // is a class
               classResource.hasProperty(CIMS.stereotype, CIMStereotypes.concrete) &&        // is concrete
               !classResource.hasProperty(CIMS.stereotype, CIMStereotypes.description) &&    // not a description
               !classResource.hasProperty(CIMS.stereotype, CIMStereotypes.enumeration) &&    // not an enumeration
               !classResource.hasProperty(CIMS.stereotype, CIMStereotypes.cimDataType);      // not a CIM datatype
    }

}

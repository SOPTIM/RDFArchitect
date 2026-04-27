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

package org.rdfarchitect.dl.rdf.resources;

import lombok.experimental.UtilityClass;

import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;

/**
 * Class containing CGMES 3.0 DiagramLayout Profile {@link Resource resources}, {@link Property
 * properties} and namespace prefixes and strings
 */
@UtilityClass
public class DL {

    // PROPERTIES
    public final Property orientation =
            ResourceFactory.createProperty(constructDLNamespacedTerm("Diagram.orientation"));

    public final Property belongsToDiagram =
            ResourceFactory.createProperty(constructDLNamespacedTerm("DiagramObject.Diagram"));

    public final Property belongsToIdentifiedObject =
            ResourceFactory.createProperty(
                    constructDLNamespacedTerm("DiagramObject.IdentifiedObject"));

    public final Property belongsToDiagramObject =
            ResourceFactory.createProperty(
                    constructDLNamespacedTerm("DiagramObjectPoint.DiagramObject"));

    public final Property xPosition =
            ResourceFactory.createProperty(
                    constructDLNamespacedTerm("DiagramObjectPoint.xPosition"));

    public final Property yPosition =
            ResourceFactory.createProperty(
                    constructDLNamespacedTerm("DiagramObjectPoint.yPosition"));

    // OBJECTS
    public final Resource diagramType =
            ResourceFactory.createResource(constructDLNamespacedTerm("Diagram"));

    public final Resource diagramObjectType =
            ResourceFactory.createResource(constructDLNamespacedTerm("DiagramObject"));

    public final Resource diagramObjectPointType =
            ResourceFactory.createResource(constructDLNamespacedTerm("DiagramObjectPoint"));

    public final Resource negativeOrientation =
            ResourceFactory.createResource(constructDLNamespacedTerm("OrientationKind.negative"));

    public final Resource positiveOrientation =
            ResourceFactory.createResource(constructDLNamespacedTerm("OrientationKind.positive"));

    /**
     * Prepends the namespace URL to a given term, constructing a namespaced term
     *
     * @param term the term to be put into the namespace
     * @return the namespaced term
     */
    private static String constructDLNamespacedTerm(String term) {
        return CIM.NAMESPACE + term;
    }
}

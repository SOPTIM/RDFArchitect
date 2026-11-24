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

package org.rdfarchitect.shacl.property;

import lombok.experimental.UtilityClass;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.rdfarchitect.models.cim.data.dto.relations.uri.URI;
import org.rdfarchitect.models.cim.rdf.resources.CIMS;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Locale;

@UtilityClass
public class CIMPropertySHACLUtils {

    /**
     * The {@code sh:order} of a property's shapes: its position among the used properties of its
     * class, sorted case-insensitively and counted from 1. The properties of {@code
     * IdentifiedObject} come first, at 0.1.
     *
     * <p>Read from the graph rather than by a query: the generator asks this for every shape of
     * every property, and parsing and running a query each time took most of the time of generating
     * a large profile.
     *
     * @param ontology the ontology
     * @param propertyUri the property to get the order for
     * @return the order
     */
    public double getOrder(Model ontology, String propertyUri) {
        var property = ontology.createResource(propertyUri);
        var siblings = new ArrayList<String>();
        for (var domain : ontology.listObjectsOfProperty(property, RDFS.domain).toList()) {
            if (!domain.isURIResource()) {
                continue;
            }
            if (new URI(domain.asResource().getURI()).getSuffix().equals("IdentifiedObject")
                    && isUsed(property)) {
                return 0.1;
            }
            ontology.listSubjectsWithProperty(RDFS.domain, domain)
                    .filterKeep(Resource::isURIResource)
                    .filterKeep(CIMPropertySHACLUtils::isUsed)
                    .forEach(sibling -> siblings.add(sibling.getURI()));
        }
        siblings.sort(Comparator.comparing(uri -> uri.toLowerCase(Locale.ROOT)));
        return siblings.indexOf(propertyUri) + 1.0;
    }

    /** A property, and not an association end marked as unused. */
    private boolean isUsed(Resource property) {
        return property.hasProperty(RDF.type, RDF.Property)
                && !property.hasProperty(CIMS.associationUsed, "No");
    }
}

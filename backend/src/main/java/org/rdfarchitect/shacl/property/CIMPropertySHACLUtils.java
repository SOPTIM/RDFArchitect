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
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.rdf.model.Model;
import org.rdfarchitect.models.cim.data.dto.relations.uri.URI;

import java.util.ArrayList;

@UtilityClass
public class CIMPropertySHACLUtils {

    /**
     * fetches the shacl order of a specified property
     *
     * @param ontology    the ontology
     * @param propertyUri the property to get the order for
     * @return the order
     */
    public double getOrder(Model ontology, String propertyUri) {
        var query = """
                PREFIX cims:    <http://iec.ch/TC57/1999/rdf-schema-extensions-19990926#>
                PREFIX rdf:     <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                PREFIX rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                
                SELECT ?property ?class
                WHERE {
                    <PROPERTY_URI> rdfs:domain ?class.
                    ?property   a rdf:Property;
                                rdfs:domain ?class.
                    FILTER NOT EXISTS {
                        ?property cims:AssociationUsed "No".
                    }
                }ORDER BY LCASE(STR(?property))
                """.replace("PROPERTY_URI", propertyUri);
        var propertyList = new ArrayList<String>();
        try (var quexec = QueryExecutionFactory.create(query, ontology)) {
            var resultSet = quexec.execSelect();
            while (resultSet.hasNext()) {
                var result = resultSet.next();
                var resultUri = result.getResource("property").getURI();
                var resultClass = result.getResource("class").getURI();
                if (resultUri.equals(propertyUri) && new URI(resultClass).getSuffix().equals("IdentifiedObject")) {
                    return 0.1; //special for identified Object class
                }
                propertyList.add(resultUri);
            }
            return propertyList.indexOf(propertyUri) + 1.0;
        }
    }
}

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

package org.rdfarchitect.models.cim.umladapted;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import org.apache.jena.graph.Graph;
import org.apache.jena.query.ResultSet;
import org.apache.jena.shared.PrefixMapping;
import org.rdfarchitect.models.cim.data.CIMObjectFactory;
import org.rdfarchitect.models.cim.data.CIMObjectFetcher;
import org.rdfarchitect.models.cim.data.dto.relations.CIMSStereotype;
import org.rdfarchitect.models.cim.queries.CIMQueryVars;
import org.rdfarchitect.models.cim.queries.select.CIMQueries;
import org.rdfarchitect.models.cim.umladapted.data.CIMClassUMLAdapted;

import java.util.ArrayList;
import java.util.List;

/** Factory class that provides static methods for creating CIMUMLObjects from queries */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class CIMUMLObjectFactory {

    /**
     * Creates a {@link CIMClassUMLAdapted} from a given graph, prefixMapping, graphURI and
     * classURI.
     *
     * @param graph {@link Graph}
     * @param prefixMapping {@link PrefixMapping}
     * @param classUUID The UUID of the class.
     * @return {@link CIMClassUMLAdapted}
     */
    public static CIMClassUMLAdapted createCIMClassUMLAdapted(
            Graph graph, String graphUri, PrefixMapping prefixMapping, String classUUID) {
        var objectFetcher = new CIMObjectFetcher(graph, graphUri, prefixMapping);
        // fetch class
        var classObject = new CIMClassUMLAdapted(objectFetcher.fetchCIMClass(classUUID));

        // if enum, then fetch enum entries
        if (classObject
                .getStereotypes()
                .contains(new CIMSStereotype("http://iec.ch/TC57/NonStandard/UML#enumeration"))) {
            var enumEntriesQuery =
                    CIMQueries.getEnumEntriesQuery(prefixMapping, graphUri, classUUID).build();
            var enumEntries = objectFetcher.fetchCIMEnumEntryList(enumEntriesQuery);
            classObject.setEnumEntries(enumEntries);
        }

        // fetch attributes
        var attributeQuery =
                CIMQueries.getAttributesQuery(prefixMapping, classUUID, graphUri).build();
        var attributes = objectFetcher.fetchCIMAttributeList(attributeQuery);
        classObject.setAttributes(attributes);

        // fetch associations
        var associationPairsQuery =
                CIMQueries.getAssociationPairsQuery(prefixMapping, classUUID, graphUri).build();
        var associationPairs = objectFetcher.fetchCIMAssociationPairsList(associationPairsQuery);
        classObject.setAssociationPairs(associationPairs);
        return classObject;
    }

    /**
     * Creates a List of {@link CIMClassUMLAdapted CIMClasses} .
     *
     * @param classQueryResultSet {@link ResultSet} with results bound to variables from {@link
     *     CIMQueryVars CIMQueryVars}.
     * @return List of {@link CIMClassUMLAdapted CIMClasses}
     */
    public static List<CIMClassUMLAdapted> createCIMClassUMLAdaptedList(
            ResultSet classQueryResultSet) {
        var classObjectList = new ArrayList<CIMClassUMLAdapted>();
        while (classQueryResultSet.hasNext()) {
            classObjectList.add(
                    new CIMClassUMLAdapted(
                            CIMObjectFactory.createCIMClass(classQueryResultSet.next())));
        }
        return classObjectList;
    }
}

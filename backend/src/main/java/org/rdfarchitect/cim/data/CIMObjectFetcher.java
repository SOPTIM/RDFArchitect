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

package org.rdfarchitect.cim.data;

import org.apache.jena.graph.Graph;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.shared.PrefixMapping;
import org.rdfarchitect.cim.data.dto.CIMAssociation;
import org.rdfarchitect.cim.data.dto.CIMAssociationPair;
import org.rdfarchitect.cim.data.dto.CIMAttribute;
import org.rdfarchitect.cim.data.dto.CIMClass;
import org.rdfarchitect.cim.data.dto.CIMEnumEntry;
import org.rdfarchitect.cim.data.dto.relations.CIMSStereotype;
import org.rdfarchitect.cim.queries.select.CIMQueries;
import org.rdfarchitect.database.inmemory.SessionDataStore;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class CIMObjectFetcher {

    private final String graphURI;

    private final PrefixMapping prefixMapping;

    private final Dataset dataset;

    public CIMObjectFetcher(Graph graph, String graphURI, PrefixMapping prefixMapping) {
        this.graphURI = graphURI;
        this.prefixMapping = prefixMapping;
        this.dataset = SessionDataStore.wrapGraphInDataset(graph, graphURI);
        dataset.getPrefixMapping().setNsPrefixes(prefixMapping);
    }

    /**
     * Executes a SPARQL query and processes the complete result set.
     * This method is used when multiple results are expected and need to be processed together.
     *
     * @param query           The SPARQL query to execute.
     * @param resultProcessor Function to process the complete result set.
     * @param <T>             The type of object to return.
     *
     * @return The processed result of the query.
     */
    private <T> T executeQueryForList(Query query, Function<ResultSet, T> resultProcessor) {
        try (QueryExecution exec = QueryExecutionFactory.create(query, dataset)) {
            ResultSet resultSet = exec.execSelect();
            return resultProcessor.apply(resultSet);
        }
    }

    /**
     * Executes a SPARQL query and processes only the first result.
     * This method is used when a single object is expected from the query.
     *
     * @param query           The SPARQL query to execute.
     * @param resultProcessor Function to process a single query solution.
     * @param <T>             The type of object to return.
     *
     * @return The processed single result, or null if no results found.
     */
    private <T> T executeQueryForSingleObject(Query query, Function<QuerySolution, T> resultProcessor) {
        try (QueryExecution exec = QueryExecutionFactory.create(query, dataset)) {
            ResultSet resultSet = exec.execSelect();
            if (resultSet.hasNext()) {
                return resultProcessor.apply(resultSet.next());
            }
            return null;
        }
    }

    /**
     * Fetches a {@link CIMClass} from a given class UUID
     *
     * @param classUUID The UUID of the class.
     *
     * @return {@link CIMClass}
     */
    public CIMClass fetchCIMClass(String classUUID) {
        //fetch class
        var classQuery = CIMQueries.getClassQuery(prefixMapping, graphURI, classUUID).build();
        CIMClass classObject = executeQueryForSingleObject(classQuery, CIMObjectFactory::createCIMClass);
        //fetch stereotypes
        if (classObject != null) {
            var stereotypeQuery = CIMQueries.getStereotypesQuery(prefixMapping, classUUID, graphURI).build();
            classObject.setStereotypes(fetchCIMStereotypeList(stereotypeQuery));
        }
        return classObject;
    }

    /**
     * Fetches a List of {@link CIMClass CIMClasses} .
     *
     * @param query {@link Query} to fetch classes.
     *
     * @return List of {@link CIMClass CIMClasses}
     */
    public List<CIMClass> fetchCIMClassList(Query query) {
        //fetch classes
        try (var classExec = QueryExecutionFactory.create(query, dataset)) {
            var classQueryResultSet = classExec.execSelect();

            //fetch remaining data and build CIMClassObjects
            var classObjectList = new ArrayList<CIMClass>();
            while (classQueryResultSet.hasNext()) {
                var classObject = CIMObjectFactory.createCIMClass(classQueryResultSet.next());
                //fetch stereotypes
                var stereotypeQuery = CIMQueries.getStereotypesQuery(prefixMapping,
                                                                     classObject.getUuid().toString(),
                                                                     graphURI
                                                                    ).build();
                classObject.setStereotypes(fetchCIMStereotypeList(stereotypeQuery));
                classObjectList.add(classObject);
            }
            return classObjectList;
        }
    }

    /**
     * Fetches a List of {@link CIMAttribute CIMAttributes}.
     *
     * @param query {@link Query} to fetch attributes.
     *
     * @return List of {@link CIMAttribute CIMAttributes}.
     */
    public List<CIMAttribute> fetchCIMAttributeList(Query query) {
        return executeQueryForList(query, CIMObjectFactory::createCIMAttributeList);
    }

    /**
     * Fetches a List of {@link CIMAssociation CIMAssociations}.
     *
     * @param query {@link Query} to fetch associations.
     *
     * @return List of {@link CIMAssociation CIMAssociations}.
     */
    public List<CIMAssociation> fetchCIMAssociationList(Query query) {
        return executeQueryForList(query, CIMObjectFactory::createCIMAssociationList);
    }

    /**
     * Fetches a List of {@link CIMAssociation CIMAssociations}.
     *
     * @param query {@link Query} to fetch associations.
     *
     * @return List of {@link CIMAssociation CIMAssociations}.
     */
    public List<CIMAssociationPair> fetchCIMAssociationPairsList(Query query) {
        return executeQueryForList(query, CIMObjectFactory::createCIMAssociationPairList);
    }

    /**
     * Fetches a List of {@link CIMSStereotype CIMSStereotypes}.
     *
     * @param query {@link Query} to fetch stereotypes.
     *
     * @return List of {@link CIMSStereotype CIMSStereotypes}.
     */
    public List<CIMSStereotype> fetchCIMStereotypeList(Query query) {
        return executeQueryForList(query, CIMObjectFactory::createCIMStereotypeList);
    }

    /**
     * Fetches a List of {@link CIMEnumEntry CIMEnumEntries}.
     *
     * @param query {@link Query} to fetch enum entries.
     *
     * @return List of {@link CIMEnumEntry CIMEnumEntries}.
     */
    public List<CIMEnumEntry> fetchCIMEnumEntryList(Query query) {
        return executeQueryForList(query, CIMObjectFactory::createCIMEnumEntryList);
    }
}

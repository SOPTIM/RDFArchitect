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

package org.rdfarchitect.models.cim.data;

import org.apache.jena.graph.Graph;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.shared.PrefixMapping;
import org.rdfarchitect.database.inmemory.SessionDataStore;
import org.rdfarchitect.models.cim.data.dto.CIMAssociation;
import org.rdfarchitect.models.cim.data.dto.CIMAssociationPair;
import org.rdfarchitect.models.cim.data.dto.CIMAttribute;
import org.rdfarchitect.models.cim.data.dto.CIMClass;
import org.rdfarchitect.models.cim.data.dto.CIMEnumEntry;
import org.rdfarchitect.models.cim.data.dto.relations.CIMSStereotype;
import org.rdfarchitect.models.cim.queries.select.CIMQueries;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
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
     * Executes a SPARQL query and processes the complete result set. This method is used when
     * multiple results are expected and need to be processed together.
     *
     * @param query The SPARQL query to execute.
     * @param resultProcessor Function to process the complete result set.
     * @param <T> The type of object to return.
     * @return The processed result of the query.
     */
    private <T> T executeQueryForList(Query query, Function<ResultSet, T> resultProcessor) {
        try (QueryExecution exec = QueryExecutionFactory.create(query, dataset)) {
            ResultSet resultSet = exec.execSelect();
            return resultProcessor.apply(resultSet);
        }
    }

    /**
     * Executes a SPARQL query and processes only the first result. This method is used when a
     * single object is expected from the query.
     *
     * @param query The SPARQL query to execute.
     * @param resultProcessor Function to process a single query solution.
     * @param <T> The type of object to return.
     * @return The processed single result, or null if no results found.
     */
    private <T> T executeQueryForSingleObject(
            Query query, Function<QuerySolution, T> resultProcessor) {
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
     * @return {@link CIMClass}
     */
    public CIMClass fetchCIMClass(String classUUID) {
        // fetch class
        var classQuery = CIMQueries.getClassQuery(prefixMapping, graphURI, classUUID).build();
        CIMClass classObject =
                executeQueryForSingleObject(classQuery, CIMObjectFactory::createCIMClass);
        // fetch stereotypes
        if (classObject != null) {
            classObject.setGraphUri(graphURI);
            var stereotypeQuery =
                    CIMQueries.getStereotypesQuery(prefixMapping, classUUID, graphURI).build();
            classObject.setStereotypes(fetchCIMStereotypeList(stereotypeQuery));
        }
        return classObject;
    }

    /**
     * Fetches a List of {@link CIMClass CIMClasses} .
     *
     * @param query {@link Query} to fetch classes.
     * @return List of {@link CIMClass CIMClasses}
     */
    public List<CIMClass> fetchCIMClassList(Query query) {
        // fetch classes
        try (var classExec = QueryExecutionFactory.create(query, dataset)) {
            var classQueryResultSet = classExec.execSelect();

            // fetch remaining data and build CIMClassObjects
            var classObjectList = new ArrayList<CIMClass>();
            while (classQueryResultSet.hasNext()) {
                var classObject = CIMObjectFactory.createCIMClass(classQueryResultSet.next());
                classObject.setGraphUri(graphURI);
                // fetch stereotypes
                var stereotypeQuery =
                        CIMQueries.getStereotypesQuery(
                                        prefixMapping, classObject.getUuid().toString(), graphURI)
                                .build();
                classObject.setStereotypes(fetchCIMStereotypeList(stereotypeQuery));
                classObjectList.add(classObject);
            }
            return classObjectList;
        }
    }

    /**
     * Fetches a List of {@link CIMAttribute CIMAttributes}. Blank-node fixed/default values are
     * resolved by the SPARQL query itself (see {@link
     * org.rdfarchitect.models.cim.queries.select.CIMQueryBuilder#appendIsFixedQuery}), so the
     * factory only needs the {@link ResultSet}. The stereotypes of all fetched attributes are
     * resolved with a single additional query instead of one query per attribute.
     *
     * @param query {@link Query} to fetch attributes.
     * @return List of {@link CIMAttribute CIMAttributes}.
     */
    public List<CIMAttribute> fetchCIMAttributeList(Query query) {
        var attributes = executeQueryForList(query, CIMObjectFactory::createCIMAttributeList);
        var attributeUUIDs =
                attributes.stream()
                        .map(CIMAttribute::getUuid)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList();
        if (attributeUUIDs.isEmpty()) {
            return attributes;
        }
        var stereotypesByUUID = fetchCIMStereotypeListsByUUID(attributeUUIDs);
        for (var attribute : attributes) {
            attribute.setStereotypes(
                    new ArrayList<>(
                            stereotypesByUUID.getOrDefault(attribute.getUuid(), List.of())));
        }
        return attributes;
    }

    /**
     * Fetches the {@link CIMSStereotype CIMSStereotypes} of all subjects with one of the given
     * UUIDs in a single query, grouped by the UUID of the subject they belong to.
     *
     * @param subjectUUIDs The UUIDs of the subjects to fetch the stereotypes of, must not be empty.
     * @return Map from subject UUID to its {@link CIMSStereotype CIMSStereotypes}. Subjects without
     *     stereotypes are absent from the map.
     */
    private Map<UUID, List<CIMSStereotype>> fetchCIMStereotypeListsByUUID(List<UUID> subjectUUIDs) {
        var stereotypeQuery =
                CIMQueries.getStereotypesForUUIDsQuery(
                                prefixMapping,
                                subjectUUIDs.stream().map(UUID::toString).toList(),
                                graphURI)
                        .build();
        return executeQueryForList(
                stereotypeQuery, CIMObjectFactory::createCIMStereotypeListsByUUID);
    }

    /**
     * Fetches a List of {@link CIMAssociation CIMAssociations}.
     *
     * @param query {@link Query} to fetch associations.
     * @return List of {@link CIMAssociation CIMAssociations}.
     */
    public List<CIMAssociation> fetchCIMAssociationList(Query query) {
        return executeQueryForList(query, CIMObjectFactory::createCIMAssociationList);
    }

    /**
     * Fetches a List of {@link CIMAssociation CIMAssociations}.
     *
     * @param query {@link Query} to fetch associations.
     * @return List of {@link CIMAssociation CIMAssociations}.
     */
    public List<CIMAssociationPair> fetchCIMAssociationPairsList(Query query) {
        return executeQueryForList(query, CIMObjectFactory::createCIMAssociationPairList);
    }

    /**
     * Fetches a List of {@link CIMSStereotype CIMSStereotypes}.
     *
     * @param query {@link Query} to fetch stereotypes.
     * @return List of {@link CIMSStereotype CIMSStereotypes}.
     */
    public List<CIMSStereotype> fetchCIMStereotypeList(Query query) {
        return executeQueryForList(query, CIMObjectFactory::createCIMStereotypeList);
    }

    /**
     * Fetches a List of {@link CIMEnumEntry CIMEnumEntries}.
     *
     * @param query {@link Query} to fetch enum entries.
     * @return List of {@link CIMEnumEntry CIMEnumEntries}.
     */
    public List<CIMEnumEntry> fetchCIMEnumEntryList(Query query) {
        return executeQueryForList(query, CIMObjectFactory::createCIMEnumEntryList);
    }
}

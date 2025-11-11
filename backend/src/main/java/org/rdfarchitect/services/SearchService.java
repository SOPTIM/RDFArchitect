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

package org.rdfarchitect.services;

import lombok.RequiredArgsConstructor;
import org.apache.jena.query.QueryFactory;
import org.rdfarchitect.cim.queries.CIMQueryVars;
import org.rdfarchitect.cim.rdf.resources.CIMS;
import org.rdfarchitect.cim.search.SearchFilter;
import org.rdfarchitect.cim.search.SearchResultObjectFactory;
import org.rdfarchitect.cim.search.data.SearchResult;
import org.rdfarchitect.cim.search.data.SearchResults;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.database.inmemory.InMemorySparqlExecutioner;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class SearchService implements SearchUseCase {

    private final DatabasePort databasePort;

    private static final String GRAPH = "graph";
    private static final String FILTER_PACKAGE = "filterPackage";
    private static final String PACKAGE_CONSTRAINT = "packageConstraint";
    private static final String SPARQL_INTERNAL_QUERY = """
                      PREFIX  cims: <http://iec.ch/TC57/1999/rdf-schema-extensions-19990926#>
                      PREFIX  rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                      PREFIX  rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                      PREFIX  cim:  <http://iec.ch/TC57/2013/CIM-schema-cim16#>
              
                      SELECT  ?uri ?uuid ?label ?typeURI ?typeUUID ?typeLabel ?packageURI ?packageLabel ?packageUUID
                              ?domainURI ?domainLabel ?domainUUID ?stereotype
                      graph
                      WHERE
                      {
                        filterPackage
                        FILTER contains(lcase(str(?label)), lcase("searchQuery"))
                        ?uri <http://example.org#uuid> ?uuid ;
                             rdfs:label ?label ;
                             rdf:type ?typeURI .
                        OPTIONAL { ?typeURI rdfs:label ?typeLabel }
              
                        OPTIONAL {
                          ?typeURI <http://example.org#uuid> ?typeUUID.
                          ?typeURI cims:belongsToCategory ?packageURI.
                          ?packageURI rdfs:label ?packageLabel.
                          ?packageURI <http://example.org#uuid> ?packageUUID
                        }
              
                        OPTIONAL {
                          ?uri rdfs:domain ?domainURI.
                          ?domainURI rdfs:label ?domainLabel.
                          ?domainURI <http://example.org#uuid> ?domainUUID
                          OPTIONAL {
                            ?domainURI cims:belongsToCategory ?packageURI.
                            ?packageURI rdfs:label ?packageLabel.
                            ?packageURI <http://example.org#uuid> ?packageUUID
                          }
                        }
              
                        OPTIONAL { ?uri cims:stereotype ?stereotype }
              
                        OPTIONAL {
                          ?uri cims:belongsToCategory ?packageURI.
                          ?packageURI rdfs:label ?packageLabel.
                          ?packageURI <http://example.org#uuid> ?packageUUID
                        }
              
                          # Filter um externe Elemente zu entfernen
                        FILTER(
                          (
                              !EXISTS { ?uri cims:belongsToCategory ?anyPackage }
                              &&
                              !EXISTS { ?uri rdfs:domain ?anyDomain }
                          )
                          ||
                          EXISTS {
                              ?uri cims:belongsToCategory ?somePackage.
                              ?somePackage rdfs:label ?somePackageLabel.
                          }
                          ||
                          EXISTS {
                              ?uri rdfs:domain ?someDomain.
                              ?someDomain cims:belongsToCategory ?somePackage.
                              ?somePackage rdfs:label ?somePackageLabel.
                          }
                        )
              
                        packageConstraint
                        }
                      ORDER BY ?label
              """;

    private static final String SPARQL_EXTERNAL_QUERY = """
                      PREFIX  cims: <http://iec.ch/TC57/1999/rdf-schema-extensions-19990926#>
                      PREFIX  rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                      PREFIX  rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                      PREFIX  cim:  <http://iec.ch/TC57/2013/CIM-schema-cim16#>
              
                      SELECT DISTINCT ?uri ?uuid ?label ?typeURI ?typeUUID ?typeLabel ?packageURI
                              (IF(CONTAINS(STR(?packageURI), "Package_"),
                                  STRAFTER(STR(?packageURI), "Package_"),
                                  STRAFTER(STR(?packageURI), "#")) AS ?packageLabel)
                              ?packageUUID ?domainURI ?domainLabel ?domainUUID ?stereotype
                      graph
                      WHERE
                      {
                        filterPackage
                        {
                          # FALL Externe Packages
                          ?uri <http://example.org#uuid> ?uuid .
                          FILTER NOT EXISTS {
                              ?uri rdfs:label ?anyLabel ;
                                  rdf:type ?anyType .
                          }
              
                          BIND(?uri AS ?packageURI)
                          BIND(?uuid AS ?packageUUID)
                          BIND(<http://iec.ch/TC57/1999/rdf-schema-extensions-19990926#ClassCategory> AS ?typeURI)
                          BIND("ClassCategory" AS ?typeLabel)
                          BIND(IF(CONTAINS(STR(?uri), "Package_"),
                                  STRAFTER(STR(?uri), "Package_"),
                                  STRAFTER(STR(?uri), "#")) AS ?label)
              
                          FILTER contains(lcase(str(?label)), lcase("searchQuery"))
                        }
              
                        UNION
              
                        {
                          # FALL alle anderen externen Elemente
                          ?uri <http://example.org#uuid> ?uuid ;
                               rdfs:label ?label ;
                               rdf:type ?typeURI .
                          FILTER contains(lcase(str(?label)), lcase("searchQuery"))
              
                          OPTIONAL { ?typeURI rdfs:label ?typeLabel }
                          OPTIONAL { ?typeURI <http://example.org#uuid> ?typeUUID }
                          OPTIONAL { ?uri cims:stereotype ?stereotype }
              
                          {
                            # FALL externe Klasse
                            ?uri cims:belongsToCategory ?packageURI .
                            ?packageURI <http://example.org#uuid> ?packageUUID .
                            FILTER NOT EXISTS { ?packageURI rdfs:label ?anyPackageLabel }
                          }
              
                          UNION
              
                          {
                            # FALL externe Attribute oder Assoziationen
                            ?uri rdfs:domain ?domainURI .
                            ?domainURI rdfs:label ?domainLabel .
                            ?domainURI <http://example.org#uuid> ?domainUUID .
              
                            {
                              # SUB-FALL externes Attribut und Assoziation zwischen externen Klassen
                              ?domainURI cims:belongsToCategory ?packageURI .
                            }
                            UNION
                            {
                              # SUB-FALL Assoziation von nicht-externer Klasse zu externer Klasse
                              ?uri rdfs:range ?rangeURI .
                              ?rangeURI cims:belongsToCategory ?packageURI .
                            }
              
                            ?packageURI <http://example.org#uuid> ?packageUUID .
                            FILTER NOT EXISTS { ?packageURI rdfs:label ?anyPackageLabel }
                          }
                        }
                        packageConstraint
                      }
                      ORDER BY ?label
              """;

    @Override
    public SearchResults search(String query, SearchFilter filter) {
        List<SearchResult> internalSearchResults = new ArrayList<>();
        List<SearchResult> externalSearchResults = new ArrayList<>();

        if (filter.getDatasetName() != null) {
            searchDataset(filter.getDatasetName(), query, filter, internalSearchResults, externalSearchResults);
        } else {
            for (var datasetName : databasePort.listDatasets()) {
                searchDataset(datasetName, query, filter, internalSearchResults, externalSearchResults);
            }
        }

        return new SearchResults(internalSearchResults, externalSearchResults);
    }

    public void searchDataset(String datasetName, String query, SearchFilter filter, List<SearchResult> searchResults, List<SearchResult> externalSearchResults) {
        if (filter.getGraphUri() != null) {
            searchGraph(new GraphIdentifier(datasetName, filter.getGraphUri()), query, filter, searchResults, externalSearchResults);
            return;
        }

        for (String graphUri : databasePort.listGraphUris(datasetName)) {
            searchGraph(new GraphIdentifier(datasetName, graphUri), query, filter, searchResults, externalSearchResults);
        }
    }

    public void searchGraph(GraphIdentifier graphIdentifier, String query, SearchFilter filter, List<SearchResult> searchResults, List<SearchResult> externalSearchResults) {
        String specificInternalQuery;
        String specificExternalQuery;
        if (Objects.equals(graphIdentifier.getGraphUri(), "default")) {
            specificInternalQuery = SPARQL_INTERNAL_QUERY.replace(GRAPH, "");
            specificExternalQuery = SPARQL_EXTERNAL_QUERY.replace(GRAPH, "");
        } else {
            specificInternalQuery = SPARQL_INTERNAL_QUERY.replace(GRAPH, "FROM <" + graphIdentifier.getGraphUri() + ">");
            specificExternalQuery = SPARQL_EXTERNAL_QUERY.replace(GRAPH, "FROM <" + graphIdentifier.getGraphUri() + ">");
        }
        specificInternalQuery = specificInternalQuery.replace("searchQuery", query);
        specificExternalQuery = specificExternalQuery.replace("searchQuery", query);
        var internalQueryWithPackageConstraint = appendPackageConstraint(filter, specificInternalQuery);
        var externalQueryWithPackageConstraint = appendPackageConstraint(filter, specificExternalQuery);
        var internalQueryObject = QueryFactory.create(internalQueryWithPackageConstraint);
        var externalQueryObject = QueryFactory.create(externalQueryWithPackageConstraint);
        var internalResultSet = InMemorySparqlExecutioner.executeSingleQuery(databasePort.getGraph(graphIdentifier), internalQueryObject, graphIdentifier.getGraphUri());
        var externalResultSet = InMemorySparqlExecutioner.executeSingleQuery(databasePort.getGraph(graphIdentifier), externalQueryObject, graphIdentifier.getGraphUri());

        searchResults.addAll(SearchResultObjectFactory.createSearchResultObjectList(graphIdentifier, internalResultSet));
        externalSearchResults.addAll(SearchResultObjectFactory.createSearchResultObjectList(graphIdentifier, externalResultSet));
    }

    private String appendPackageConstraint(SearchFilter filter, String query) {
        if (filter.getPackageUUID() == null) {
            return query.replace(FILTER_PACKAGE, "").replace(PACKAGE_CONSTRAINT, "");
        }

        if (Objects.equals(filter.getPackageUUID(), "default")) {
            return query.replace(FILTER_PACKAGE, "")
                        .replace(PACKAGE_CONSTRAINT, "FILTER (" +
                                  "NOT EXISTS { " + CIMQueryVars.URI + " <" + CIMS.belongsToCategory + "> " + "?any }" +
                                  "NOT EXISTS { " + CIMQueryVars.TYPE_URI + " <" + CIMS.belongsToCategory + "> " + "?any }" +
                                  "NOT EXISTS { " + CIMQueryVars.DOMAIN_URI + " <" + CIMS.belongsToCategory + "> " + "?any })"
                                );
        }

        return query.replace(FILTER_PACKAGE, """
                              OPTIONAL {
                                ?filterPackage  <http://example.org#uuid>  "%s" .
                              }
                              """.formatted(filter.getPackageUUID()))
                    .replace(PACKAGE_CONSTRAINT, "FILTER (!BOUND(?filterPackage) || ?packageURI = ?filterPackage)");
    }
}

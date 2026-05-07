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

package org.rdfarchitect.models.search;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.models.cim.CIMQuerySolutionParser;
import org.rdfarchitect.models.cim.data.dto.relations.RDFSLabel;
import org.rdfarchitect.models.cim.data.dto.relations.uri.URI;
import org.rdfarchitect.models.cim.queries.CIMQueryVars;
import org.rdfarchitect.models.cim.umladapted.data.CIMClassUMLAdapted;
import org.rdfarchitect.models.search.data.ResultType;
import org.rdfarchitect.models.search.data.SearchResult;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Factory class that provides static methods for creating SearchResults from queries */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class SearchResultObjectFactory {

    /**
     * Creates a List of {@link SearchResult SearchResults} .
     *
     * @param graphIdentifier {@link GraphIdentifier} with datasetName and graphURI of the graph to
     *     be searched.
     * @param queryResultSet {@link ResultSet} with results bound to variables from {@link
     *     CIMQueryVars CIMQueryVars}.
     * @return List of {@link SearchResult SearchResults}
     */
    public static List<SearchResult> createSearchResultObjectList(
            GraphIdentifier graphIdentifier, ResultSet queryResultSet) {
        var resultList = new ArrayList<SearchResult>();
        while (queryResultSet.hasNext()) {
            resultList.add(createSearchResult(graphIdentifier, queryResultSet.next()));
        }
        return resultList;
    }

    /**
     * Creates a {@link SearchResult} from a given graph, prefixMapping, and querySolution.
     *
     * @param graphIdentifier {@link GraphIdentifier} with datasetName and graphURI of the graph to
     *     be searched.
     * @param querySolution {@link QuerySolution} with results bound to variables from {@link
     *     CIMQueryVars CIMQueryVars}.
     * @return {@link CIMClassUMLAdapted}
     */
    public static SearchResult createSearchResult(
            GraphIdentifier graphIdentifier, QuerySolution querySolution) {
        var parser = new CIMQuerySolutionParser(querySolution);

        ResultType type;
        UUID packageUUID = null;
        RDFSLabel packageLabel = null;
        URI parentClassUri = null;
        UUID parentClassUUID = null;

        var typeInfo = parser.getType(CIMQueryVars.TYPE_URI, CIMQueryVars.TYPE_LABEL);
        switch (typeInfo.getUri().getSuffix()) {
            case "Class" -> {
                type = ResultType.CLASS;
                var pack =
                        parser.getBelongsToCategory(
                                CIMQueryVars.PACKAGE_URI,
                                CIMQueryVars.PACKAGE_LABEL,
                                CIMQueryVars.PACKAGE_UUID);
                if (pack != null) {
                    packageUUID = pack.getUuid();
                    packageLabel = pack.getLabel();
                }
            }
            case "Property" -> {
                var stereotype = parser.getStereotype(CIMQueryVars.STEREOTYPE);
                var domain = parser.getDomain(CIMQueryVars.DOMAIN_URI, CIMQueryVars.DOMAIN_LABEL);
                var domainUUID = parser.getDomainUUID(CIMQueryVars.DOMAIN_UUID);
                var pack =
                        parser.getBelongsToCategory(
                                CIMQueryVars.PACKAGE_URI,
                                CIMQueryVars.PACKAGE_LABEL,
                                CIMQueryVars.PACKAGE_UUID);
                type =
                        (stereotype != null && stereotype.getStereotype().contains("attribute"))
                                ? ResultType.ATTRIBUTE
                                : ResultType.ASSOCIATION;
                parentClassUri = domain.getUri();
                parentClassUUID = domainUUID;
                if (pack != null) {
                    packageUUID = pack.getUuid();
                    packageLabel = pack.getLabel();
                }
            }
            case "ClassCategory" -> {
                type = ResultType.PACKAGE;
                packageUUID = parser.getUUID(CIMQueryVars.UUID);
            }
            default -> {
                var pack =
                        parser.getBelongsToCategory(
                                CIMQueryVars.PACKAGE_URI,
                                CIMQueryVars.PACKAGE_LABEL,
                                CIMQueryVars.PACKAGE_UUID);
                type = ResultType.ENUMTYPE;
                parentClassUri = parser.getURI(CIMQueryVars.TYPE_URI);
                parentClassUUID = parser.getUUID(CIMQueryVars.TYPE_UUID);
                if (pack != null) {
                    packageUUID = pack.getUuid();
                    packageLabel = pack.getLabel();
                }
            }
        }

        return SearchResult.builder()
                .datasetName(graphIdentifier.datasetName())
                .graphUri(graphIdentifier.graphUri())
                .uri(parser.getURI(CIMQueryVars.URI))
                .uuid(parser.getUUID(CIMQueryVars.UUID))
                .label(parser.getLabel(CIMQueryVars.LABEL))
                .type(type)
                .parentClassUri(parentClassUri)
                .parentClassUUID(parentClassUUID)
                .packageUUID(packageUUID)
                .packageLabel(packageLabel)
                .build();
    }
}

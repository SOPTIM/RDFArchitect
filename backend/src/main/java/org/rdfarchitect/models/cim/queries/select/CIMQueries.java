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

package org.rdfarchitect.models.cim.queries.select;

import lombok.experimental.UtilityClass;
import org.apache.jena.arq.querybuilder.SelectBuilder;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.rdfarchitect.models.cim.rdf.resources.CIMS;
import org.rdfarchitect.models.cim.rdf.resources.CIMStereotypes;

import static org.rdfarchitect.models.cim.queries.select.CIMQueryBuilder.Mode.*;

@UtilityClass
public class CIMQueries {

    /**
     * Get a {@link SelectBuilder} for a query that retrieves the class with the given URI.
     *
     * @param prefixMapping The {@link PrefixMapping} to use for the query.
     * @param classUUID     The UUID of the class to retrieve, set to null to retrieve all classes.
     * @param graphURI      The URI of the graph to query.
     *
     * @return A {@link SelectBuilder} for the query.
     */
    public SelectBuilder getClassQuery(PrefixMapping prefixMapping, String graphURI, String classUUID) {

        var baseQuery = new CIMBaseQueryBuilder()
                  .addPrefixes(prefixMapping)
                  .setOrder()
                  .setType(RDFS.Class)
                  .setGraph(graphURI)
                  .build();

        var builder = new CIMQueryBuilder(baseQuery, classUUID);

        return builder
                  .appendUUIDQuery(REQUIRED)
                  .appendLabelQuery(REQUIRED)
                  .appendSuperClassQuery(OPTIONAL)
                  .appendCommentQuery(OPTIONAL)
                  .appendPackageQuery(OPTIONAL)
                  .buildSelectBuilder();
    }

    /**
     * Get a {@link SelectBuilder} for a query that retrieves the attributes of a class.
     *
     * @param prefixMapping The {@link PrefixMapping} to use for the query.
     * @param classUUID     The UUID of the class to retrieve the attributes of.
     * @param graphURI      The URI of the graph to query.
     *
     * @return A {@link SelectBuilder} for the query.
     */
    public SelectBuilder getAttributesQuery(PrefixMapping prefixMapping, String classUUID, String graphURI) {
        var baseQuery = new CIMBaseQueryBuilder()
                  .addPrefixes(prefixMapping)
                  .setGraph(graphURI)
                  .setOrder()
                  .setType(RDF.Property)
                  .setDomain(classUUID)
                  .filterStereotypes(CIMStereotypes.attribute.getURI())
                  .build();
        var builder = new CIMQueryBuilder(baseQuery);

        return builder
                  .appendUUIDQuery(REQUIRED)
                  .appendLabelQuery(REQUIRED)
                  .appendMultiplicityQuery(REQUIRED)
                  .appendDomainQuery(REQUIRED)
                  .appendCommentQuery(OPTIONAL)
                  .appendIsFixedQuery(OPTIONAL)
                  .appendIsDefaultQuery(OPTIONAL)
                  .appendDataTypeQuery(OPTIONAL)
                  .appendRangeQuery(OPTIONAL)
                  .appendStereotypeQuery(OPTIONAL)
                  .buildSelectBuilder();
    }

    /**
     * Get a {@link SelectBuilder} for a query that retrieves the associations of a class.
     *
     * @param prefixMapping The {@link PrefixMapping} to use for the query.
     * @param classUUID     The UUID of the class to retrieve the associations of.
     * @param graphURI      The URI of the graph to query.
     *
     * @return A {@link SelectBuilder} for the query.
     */
    public SelectBuilder getAssociationPairsQuery(PrefixMapping prefixMapping, String classUUID, String graphURI) {
        var baseQuery = new CIMBaseQueryBuilder()
                  .setGraph(graphURI)
                  .addPrefixes(prefixMapping)
                  .setOrder()
                  .setType(RDF.Property)
                  .setDomain(classUUID)
                  .addWhereThisNotExists(CIMS.stereotype.getURI(), CIMStereotypes.attribute.getURI())
                  .build();

        var builder = new CIMQueryBuilder(baseQuery);

        return builder
                  .appendUUIDQuery(REQUIRED)
                  .appendLabelQuery(REQUIRED)
                  .appendRangeQuery(REQUIRED)
                  .appendAssociationUsedQuery(REQUIRED)
                  .appendInverseRoleNameQuery(REQUIRED)
                  .appendInverseUUIDQuery(REQUIRED)
                  .appendMultiplicityQuery(REQUIRED)
                  .appendDomainQuery(REQUIRED)
                  .appendCommentQuery(OPTIONAL)
                  //inverse
                  .appendInverseLabelQuery(REQUIRED)
                  .appendInverseMultiplicityQuery(REQUIRED)
                  .appendInverseAssociationUsedQuery(REQUIRED)
                  .appendInverseCommentQuery(OPTIONAL)
                  .buildSelectBuilder();
    }

    /**
     * Get a {@link SelectBuilder} for a query that retrieves the associations of a class.
     *
     * @param prefixMapping The {@link PrefixMapping} to use for the query.
     * @param graphURI      The URI of the graph to query.
     * @param classUUID     The UUID of the class to retrieve the associations of.
     *
     * @return A {@link SelectBuilder} for the query.
     */
    public SelectBuilder getAssociationsQuery(PrefixMapping prefixMapping, String graphURI, String classUUID) {
        var baseQuery = new CIMBaseQueryBuilder()
                  .setGraph(graphURI)
                  .addPrefixes(prefixMapping)
                  .setOrder()
                  .setType(RDF.Property)
                  .setDomain(classUUID)
                  .addWhereThisNotExists(CIMS.stereotype.getURI(), CIMStereotypes.attribute.getURI())
                  .build();

        var builder = new CIMQueryBuilder(baseQuery);

        return builder
                  .appendUUIDQuery(REQUIRED)
                  .appendLabelQuery(REQUIRED)
                  .appendRangeQuery(REQUIRED)
                  .appendAssociationUsedQuery(REQUIRED)
                  .appendInverseRoleNameQuery(REQUIRED)
                  .appendMultiplicityQuery(REQUIRED)
                  .appendDomainQuery(REQUIRED)
                  .appendCommentQuery(OPTIONAL)
                  .buildSelectBuilder();
    }

    /**
     * Get a {@link SelectBuilder} for a query that retrieves the stereotypes of a class.
     *
     * @param prefixMapping The {@link PrefixMapping} to use for the query.
     * @param classUUID     The UUID of the class to retrieve the stereotypes of.
     * @param graphURI      The URI of the graph to query.
     *
     * @return A {@link SelectBuilder} for the query.
     */
    public static SelectBuilder getStereotypesQuery(PrefixMapping prefixMapping, String classUUID, String graphURI) {
        var baseQuery = new CIMBaseQueryBuilder()
                  .setOrder()
                  .addPrefixes(prefixMapping)
                  .setGraph(graphURI)
                  .buildWithoutUriVar();

        var builder = new CIMQueryBuilder(baseQuery, classUUID);

        return builder
                  .appendStereotypeQuery(REQUIRED)
                  .buildSelectBuilder();
    }

    /**
     * Get a {@link SelectBuilder} for a query that retrieves the enum classes of a graph.
     *
     * @param prefixMapping The {@link PrefixMapping} to use for the query.
     * @param graphURI      The URI of the graph to query.
     * @param enumUUID      The UUID of the enum class to retrieve
     *
     * @return A {@link SelectBuilder} for the query.
     */
    public static SelectBuilder getEnumClassesQuery(PrefixMapping prefixMapping, String graphURI, String enumUUID) {
        var baseQuery = new CIMBaseQueryBuilder()
                  .addPrefixes(prefixMapping)
                  .setGraph(graphURI)
                  .setOrder()
                  .setType(RDFS.Class)
                  .filterStereotypes(CIMStereotypes.enumeration.getURI())
                  .build();
        var builder = new CIMQueryBuilder(baseQuery, enumUUID);

        return builder
                  .appendUUIDQuery(REQUIRED)
                  .appendLabelQuery(REQUIRED)
                  .appendCommentQuery(OPTIONAL)
                  .appendPackageQuery(OPTIONAL)
                  .appendSuperClassQuery(OPTIONAL)
                  .buildSelectBuilder();
    }

    public static SelectBuilder getEnumEntriesQuery(PrefixMapping prefixMapping, String graphURI, String enumUUID) {
        var baseQuery = new CIMBaseQueryBuilder()
                  .addPrefixes(prefixMapping)
                  .setGraph(graphURI)
                  .setOrder();
        if (enumUUID != null) {
            baseQuery.setEnumType(enumUUID);
        }
        var builder = new CIMQueryBuilder(baseQuery.build());

        return builder
                  .appendUUIDQuery(REQUIRED)
                  .appendTypeQuery(REQUIRED)
                  .appendLabelQuery(REQUIRED)
                  .appendCommentQuery(OPTIONAL)
                  .appendStereotypeQuery(OPTIONAL)
                  .buildSelectBuilder();
    }

    public static SelectBuilder getEnumEntryQuery(PrefixMapping prefixMapping, String graphURI, String enumEntryUUID) {
        var baseQuery = new CIMBaseQueryBuilder()
                  .addPrefixes(prefixMapping)
                  .setGraph(graphURI)
                  .setOrder()
                  .build();
        var builder = new CIMQueryBuilder(baseQuery, enumEntryUUID);
        return builder
                  .appendTypeQuery(REQUIRED)
                  .appendLabelQuery(REQUIRED)
                  .appendCommentQuery(OPTIONAL)
                  .appendStereotypeQuery(OPTIONAL)
                  .buildSelectBuilder();
    }
}

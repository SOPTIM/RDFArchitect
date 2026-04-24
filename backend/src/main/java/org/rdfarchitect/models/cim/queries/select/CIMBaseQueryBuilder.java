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

import org.apache.jena.arq.querybuilder.SelectBuilder;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.rdfarchitect.models.cim.queries.CIMQueryVars;
import org.rdfarchitect.models.cim.rdf.resources.CIMS;
import org.rdfarchitect.models.cim.rdf.resources.RDFA;
import org.rdfarchitect.rdf.RDFUtils;

public class CIMBaseQueryBuilder {

    SelectBuilder baseQuery;

    public CIMBaseQueryBuilder() {
        baseQuery = new SelectBuilder();
    }

    /**
     * Call to finish building the baseQuery. Calling this repeatedly might result in unexpected
     * behaviour.
     *
     * @return the {@link SelectBuilder} object.
     */
    public SelectBuilder build() {
        baseQuery.addVar(CIMQueryVars.URI);
        return baseQuery;
    }

    /**
     * Call to finish building the baseQuery without adding the searchUri as a variable. Calling
     * this repeatedly might result in unexpected behaviour.
     *
     * @return the {@link SelectBuilder} object.
     */
    public SelectBuilder buildWithoutUriVar() {
        return baseQuery;
    }

    /**
     * Adds a {@link PrefixMapping}.
     *
     * @param prefixes A {@link PrefixMapping} containing the prefixes to be added.
     * @return {@link CIMBaseQueryBuilder this}
     */
    public CIMBaseQueryBuilder addPrefixes(PrefixMapping prefixes) {
        baseQuery.addPrefixes(prefixes.getNsPrefixMap());
        return this;
    }

    /**
     * Sets the graph the query should be executed on.
     *
     * @param graphURI the GraphUri or "default"/null
     * @return {@link CIMBaseQueryBuilder this}
     */
    public CIMBaseQueryBuilder setGraph(String graphURI) {
        if (graphURI != null && !graphURI.equals("default")) {
            baseQuery.from(graphURI);
        }
        return this;
    }

    /**
     * Sets the default order (by uri).
     *
     * @return {@link CIMBaseQueryBuilder this}
     */
    public CIMBaseQueryBuilder setOrder() {
        baseQuery.addOrderBy(CIMQueryVars.URI);
        return this;
    }

    /**
     * Sets whether results should be distinct
     *
     * @return {@link CIMBaseQueryBuilder this}
     */
    public CIMBaseQueryBuilder setDistinct() {
        baseQuery.setDistinct(true);
        return this;
    }

    /**
     * Sets the type relation of the base uri
     *
     * @param type The object of the {@link RDF#type} relation.
     * @return {@link CIMBaseQueryBuilder this}
     */
    public CIMBaseQueryBuilder setType(Object type) {
        baseQuery.addWhere(CIMQueryVars.URI, RDF.type, type);
        return this;
    }

    /**
     * Sets the type relation of the base uri, in case the type is an enum.
     *
     * @param enumUUID The UUID of the enum.
     * @return {@link CIMBaseQueryBuilder this}
     */
    public CIMBaseQueryBuilder setEnumType(String enumUUID) {
        baseQuery.addWhere(CIMQueryVars.TYPE_URI, RDFA.uuid, enumUUID);
        baseQuery.addWhere(CIMQueryVars.URI, RDF.type, CIMQueryVars.TYPE_URI);
        return this;
    }

    /**
     * Sets the domain of the base uri
     *
     * @param domainUUID The UUID of the domain.
     * @return {@link CIMBaseQueryBuilder this}
     */
    public CIMBaseQueryBuilder setDomain(String domainUUID) {
        baseQuery.addWhere(CIMQueryVars.DOMAIN_URI, RDFA.uuid, domainUUID);
        return this;
    }

    /**
     * Adds a list of stereotypes the base uri is allowed to have. repeated calls can create
     * unexpected behaviour.
     *
     * @param stereotypes A list of stereotypes.
     * @return {@link CIMBaseQueryBuilder this}
     */
    public CIMBaseQueryBuilder filterStereotypes(String... stereotypes) {
        if (stereotypes.length == 0) {
            return this;
        }
        var sb =
                new StringBuilder(
                        CIMQueryVars.STEREOTYPE
                                + " = "
                                + RDFUtils.wrapURLorLiteral(stereotypes[0]));
        for (int i = 1; i < stereotypes.length; i++) {
            sb.append(" || ")
                    .append(CIMQueryVars.STEREOTYPE)
                    .append(" = ")
                    .append(RDFUtils.wrapURLorLiteral(stereotypes[i]));
        }
        baseQuery
                .addWhere(CIMQueryVars.URI, CIMS.stereotype, CIMQueryVars.STEREOTYPE)
                .addFilter(sb.toString());
        return this;
    }

    /**
     * Adds a filter to require a specific relation to the base uri
     *
     * @param predicate The predicate of the relation.
     * @param object The object of the relation.
     * @return {@link CIMBaseQueryBuilder this}
     */
    public CIMBaseQueryBuilder addWhereThis(Object predicate, Object object) {
        baseQuery.addWhere(CIMQueryVars.URI, predicate, object);
        return this;
    }

    /**
     * adds a filter to exclude a specific relation to the base uri
     *
     * @param predicate The predicate of the relation.
     * @param object The object of the relation.
     * @return {@link CIMBaseQueryBuilder this}
     */
    public CIMBaseQueryBuilder addWhereThisNotExists(String predicate, String object) {
        baseQuery.addFilter(
                "NOT EXISTS{ "
                        + CIMQueryVars.URI
                        + RDFUtils.wrapURLorLiteral(predicate)
                        + " "
                        + RDFUtils.wrapURLorLiteral(object)
                        + " }");
        return this;
    }

    /**
     * Sets the label relation of the base uri
     *
     * @param label The object of the {@link RDFS#label} relation.
     * @return {@link CIMBaseQueryBuilder this}
     */
    public CIMBaseQueryBuilder setLabel(Object label) {
        baseQuery.addWhere(CIMQueryVars.URI, RDFS.label, label);
        return this;
    }

    public CIMBaseQueryBuilder setURI(Object uri) {
        baseQuery.addWhere(uri, "?pre", "?obj");
        return this;
    }
}

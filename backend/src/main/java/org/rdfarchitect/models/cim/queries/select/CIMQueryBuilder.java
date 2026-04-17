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
import org.apache.jena.query.Query;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.rdfarchitect.models.cim.queries.CIMQueryVars;
import org.rdfarchitect.models.cim.rdf.resources.CIMS;
import org.rdfarchitect.models.cim.rdf.resources.RDFA;

public class CIMQueryBuilder {

    public enum Mode {
        REQUIRED,
        OPTIONAL
    }

    protected final SelectBuilder baseQuery;

    private SelectBuilder inverseSubquery;

    /**
     * Constructor for the {@link CIMQueryBuilder}, provide a {@link SelectBuilder baseQuery}, which assigns the subject uri to the {@code URI_VAR}.
     *
     * @param baseQuery {@link SelectBuilder}
     */
    public CIMQueryBuilder(SelectBuilder baseQuery) {
        this.baseQuery = baseQuery;
        this.inverseSubquery = null;
    }

    /**
     * Constructor for the {@link CIMQueryBuilder}, provide a {@link SelectBuilder baseQuery}.
     *
     * @param baseQuery   {@link SelectBuilder}
     * @param subjectUUID uuid the {@code URI_VAR} is assigned to
     */
    public CIMQueryBuilder(SelectBuilder baseQuery, String subjectUUID) {
        if (subjectUUID != null) {
            baseQuery.addWhere(CIMQueryVars.URI, RDFA.uuid, subjectUUID);
        }
        this.baseQuery = baseQuery;
        this.inverseSubquery = null;
    }

    /**
     * Builds the {@link SelectBuilder} to a {@link Query}
     *
     * @return {@link Query} containing all previously appended queries.
     */
    public Query build() {
        return buildSelectBuilder().build();
    }

    private void initInverseSubquery() {
        this.inverseSubquery = new SelectBuilder()
                  .addWhere(CIMQueryVars.URI, CIMS.inverseRoleName, CIMQueryVars.INVERSE_ROLE_NAME);
    }

    /**
     * Builds the {@link SelectBuilder}
     *
     * @return {@link SelectBuilder} containing all previously appended queries.
     */
    public SelectBuilder buildSelectBuilder() {
        if (inverseSubquery != null) {
            baseQuery.addWhere(inverseSubquery);
        }
        return baseQuery;
    }

    private CIMQueryBuilder appendSingleQuery(Object predicate, String objectVar, Mode mode) {
        return appendSingleQuery(CIMQueryVars.URI, predicate, objectVar, mode);
    }

    private CIMQueryBuilder appendInverseSingleQuery(Object predicate, String objectVar, Mode mode) {
        return appendSingleQuery(CIMQueryVars.INVERSE_ROLE_NAME, predicate, objectVar, mode);
    }

    private CIMQueryBuilder appendSingleQuery(String subjectVar, Object predicate, String objectVar, Mode mode) {
        baseQuery.addVar(objectVar);
        if (mode == Mode.REQUIRED) {
            baseQuery.addWhere(subjectVar, predicate, objectVar);
        } else {
            baseQuery.addOptional(subjectVar, predicate, objectVar);
        }
        return this;
    }

    private CIMQueryBuilder appendDoubleQueryWithUUID(Object primaryRelation, String primaryVariable, Object secondaryRelation, String secondaryVariable,
                                                      String secondaryUUIDVariable, Mode mode) {
        baseQuery.addVar(primaryVariable)
                 .addVar(secondaryVariable)
                 .addVar(secondaryUUIDVariable);
        if (mode == Mode.REQUIRED) {
            baseQuery
                      .addWhere(CIMQueryVars.URI, primaryRelation, primaryVariable)
                      .addWhere(primaryVariable, secondaryRelation, secondaryVariable)
                      .addWhere(primaryVariable, RDFA.uuid, secondaryUUIDVariable);
        } else {
            baseQuery
                      .addOptional(new SelectBuilder()
                                             .addWhere(CIMQueryVars.URI, primaryRelation, primaryVariable)
                                             .addOptional(primaryVariable, secondaryRelation, secondaryVariable)
                                             .addOptional(primaryVariable, RDFA.uuid, secondaryUUIDVariable)
                                  );
        }
        return this;
    }

    private CIMQueryBuilder appendDoubleQuery(Object primaryRelation, String primaryVariable, Object secondaryRelation, String secondaryVariable, Mode mode) {
        baseQuery.addVar(primaryVariable)
                 .addVar(secondaryVariable);
        if (mode == Mode.REQUIRED) {
            baseQuery
                      .addWhere(CIMQueryVars.URI, primaryRelation, primaryVariable)
                      .addOptional(primaryVariable, secondaryRelation, secondaryVariable);
        } else {
            baseQuery
                      .addOptional(new SelectBuilder()
                                             .addWhere(CIMQueryVars.URI, primaryRelation, primaryVariable)
                                             .addOptional(primaryVariable, secondaryRelation, secondaryVariable)
                                  );
        }
        return this;
    }

    /**
     *
     */
    public CIMQueryBuilder appendUUIDQuery(Mode mode) {
        return appendSingleQuery(RDFA.uuid, CIMQueryVars.UUID, mode);
    }

    /**
     * Appends query for labels as {@link CIMQueryVars LABEL}.
     *
     * @param mode Whether the result of this is mode.
     *
     * @return {@link CIMQueryBuilder this}
     */
    public CIMQueryBuilder appendLabelQuery(Mode mode) {
        return appendSingleQuery(RDFS.label, CIMQueryVars.LABEL, mode);
    }

    /**
     * Appends query for comments as {@link CIMQueryVars COMMENT}.
     *
     * @param mode Whether the result of this is mode.
     *
     * @return {@link CIMQueryBuilder this}
     */
    public CIMQueryBuilder appendCommentQuery(Mode mode) {
        return appendSingleQuery(RDFS.comment, CIMQueryVars.COMMENT, mode);
    }

    /**
     * Appends query for domains by querying for {@link CIMQueryVars DOMAIN_URI}, {@link CIMQueryVars DOMAIN_LABEL} and {@link CIMQueryVars DOMAIN_UUID}.
     *
     * @param mode Whether the result of this is mode.
     *
     * @return {@link CIMQueryBuilder this}
     */
    public CIMQueryBuilder appendDomainQuery(Mode mode) {
        return appendDoubleQueryWithUUID(RDFS.domain, CIMQueryVars.DOMAIN_URI, RDFS.label, CIMQueryVars.DOMAIN_LABEL, CIMQueryVars.DOMAIN_UUID, mode);
    }

    /**
     * Appends query for stereotypes as {@link CIMQueryVars STEREOTYPE}.
     *
     * @param mode Whether the result of this is mode.
     *
     * @return {@link CIMQueryBuilder this}
     */
    public CIMQueryBuilder appendStereotypeQuery(Mode mode) {
        return appendSingleQuery(CIMS.stereotype, CIMQueryVars.STEREOTYPE, mode);
    }

    /**
     * Appends query for superClasses by querying the superClassURI as {@link CIMQueryVars SUPER_CLASS_URI} and the superClassLabel as {@link CIMQueryVars SUPER_CLASS_LABEL}.
     *
     * @param mode Whether the result of this is mode.
     *
     * @return {@link CIMQueryBuilder this}
     */
    public CIMQueryBuilder appendSuperClassQuery(Mode mode) {
        return appendDoubleQuery(RDFS.subClassOf, CIMQueryVars.SUPER_CLASS_URI, RDFS.label, CIMQueryVars.SUPER_CLASS_LABEL, mode);
    }

    /**
     * Appends query for multiplicity as {@link CIMQueryVars MULTIPLICITY}.
     *
     * @param mode Whether the result of this is mode.
     *
     * @return {@link CIMQueryBuilder this}
     */
    public CIMQueryBuilder appendMultiplicityQuery(Mode mode) {
        return appendSingleQuery(CIMS.multiplicity, CIMQueryVars.MULTIPLICITY, mode);
    }

    /**
     * Appends query for isFixed as {@link CIMQueryVars IS_FIXED}.
     *
     * @param mode Whether the result of this is mode.
     *
     * @return {@link CIMQueryBuilder this}
     */
    public CIMQueryBuilder appendIsFixedQuery(Mode mode) {
        return appendSingleQuery(CIMS.isFixed, CIMQueryVars.IS_FIXED, mode);
    }

    /**
     * Appends query for isDefault as {@link CIMQueryVars IS_DEFAULT}.
     *
     * @param mode Whether the result of this is mode.
     *
     * @return {@link CIMQueryBuilder this}
     */
    public CIMQueryBuilder appendIsDefaultQuery(Mode mode) {
        return appendSingleQuery(CIMS.isDefault, CIMQueryVars.IS_DEFAULT, mode);
    }

    /**
     * Appends query for dataTypes by querying the dataTypeURI as {@link CIMQueryVars DATA_TYPE_URI} and the dataTypeLabel as {@link CIMQueryVars DATA_TYPE_LABEL}.
     *
     * @param mode Whether the result of this is mode.
     *
     * @return {@link CIMQueryBuilder this}
     */
    public CIMQueryBuilder appendDataTypeQuery(Mode mode) {
        return appendDoubleQuery(CIMS.datatype, CIMQueryVars.DATA_TYPE_URI, RDFS.label, CIMQueryVars.DATA_TYPE_LABEL, mode);
    }

    /**
     * Appends query for dataTypes by querying the dataTypeURI as {@link CIMQueryVars RANGE_URI} and the dataTypeLabel as {@link CIMQueryVars RANGE_LABEL}.
     *
     * @param mode Whether the result of this is mode.
     *
     * @return {@link CIMQueryBuilder this}
     */
    public CIMQueryBuilder appendRangeQuery(Mode mode) {
        return appendDoubleQuery(RDFS.range, CIMQueryVars.RANGE_URI, RDFS.label, CIMQueryVars.RANGE_LABEL, mode);
    }

    /**
     * Appends query for associationUsed as {@link CIMQueryVars ASSOCIATION_USED}.
     *
     * @param mode Whether the result of this is mode.
     *
     * @return {@link CIMQueryBuilder this}
     */
    public CIMQueryBuilder appendAssociationUsedQuery(Mode mode) {
        return appendSingleQuery(CIMS.associationUsed, CIMQueryVars.ASSOCIATION_USED, mode);
    }

    /**
     * Appends query for inverseRoleNames as {@link CIMQueryVars INVERSE_ROLE_NAME}.
     *
     * @param mode Whether the result of this is mode.
     *
     * @return {@link CIMQueryBuilder this}
     */
    public CIMQueryBuilder appendInverseRoleNameQuery(Mode mode) {
        if (this.inverseSubquery == null) {
            initInverseSubquery();
        }
        return appendSingleQuery(CIMS.inverseRoleName, CIMQueryVars.INVERSE_ROLE_NAME, mode);
    }

    /**
     * Appends query for types by querying the typeURI as {@link CIMQueryVars TYPE_URI} and the typeLabel as {@link CIMQueryVars TYPE_LABEL}.
     *
     * @param mode Whether the result of this is mode.
     *
     * @return {@link CIMQueryBuilder this}
     */
    public CIMQueryBuilder appendTypeQuery(Mode mode) {
        return appendDoubleQuery(RDF.type, CIMQueryVars.TYPE_URI, RDFS.label, CIMQueryVars.TYPE_LABEL, mode);
    }

    /**
     * Appends query for packages by querying for {@link CIMQueryVars PACKAGE_URI} and {@link CIMQueryVars PACKAGE_LABEL}.
     *
     * @param mode Whether the result of this is mode.
     *
     * @return {@link CIMQueryBuilder this}
     */
    public CIMQueryBuilder appendPackageQuery(Mode mode) {
        return appendDoubleQueryWithUUID(CIMS.belongsToCategory, CIMQueryVars.PACKAGE_URI, RDFS.label, CIMQueryVars.PACKAGE_LABEL, CIMQueryVars.PACKAGE_UUID, mode);
    }


    //inverse

    /**
     * Appends query for the uuid of objects connected via "cims:inverseRoleName" as {@link CIMQueryVars Inverse.UUID}.
     *
     * @param mode Whether the result of this is mode.
     *
     * @return {@link CIMQueryBuilder this}
     */
    public CIMQueryBuilder appendInverseUuidQuery(Mode mode) {
        return appendInverseSingleQuery(RDFA.uuid, CIMQueryVars.Inverse.UUID, mode);
    }

    /**
     * Appends query for the label of objects connected via "cims:inverseRoleName" as {@link CIMQueryVars Inverse.LABEL}.
     *
     * @param mode Whether the result of this is mode.
     *
     * @return {@link CIMQueryBuilder this}
     */
    public CIMQueryBuilder appendInverseLabelQuery(Mode mode) {
        return appendInverseSingleQuery(RDFS.label, CIMQueryVars.Inverse.LABEL, mode);
    }

    /**
     * Appends query for multiplicity of objects connected via "cims:inverseRoleName" as {@link CIMQueryVars Inverse.MULTIPLICITY}.
     *
     * @param mode Whether the result of this is mode.
     *
     * @return {@link CIMQueryBuilder this}
     */
    public CIMQueryBuilder appendInverseMultiplicityQuery(Mode mode) {
        return appendInverseSingleQuery(CIMS.multiplicity, CIMQueryVars.Inverse.MULTIPLICITY, mode);
    }

    /**
     * Appends query for associationUsed of objects connected via "cims:inverseRoleName" as {@link CIMQueryVars Inverse.ASSOCIATION_USED}.
     *
     * @param mode Whether the result of this is mode.
     *
     * @return {@link CIMQueryBuilder this}
     */
    public CIMQueryBuilder appendInverseAssociationUsedQuery(Mode mode) {
        return appendInverseSingleQuery(CIMS.associationUsed, CIMQueryVars.Inverse.ASSOCIATION_USED, mode);
    }

    /**
     * Appends query for comments of objects connected via "cims:inverseRoleName" as {@link CIMQueryVars Inverse.COMMENT}.
     *
     * @param mode Whether the result of this is mode.
     *
     * @return {@link CIMQueryBuilder this}
     */
    public CIMQueryBuilder appendInverseCommentQuery(Mode mode) {
        return appendInverseSingleQuery(RDFS.comment, CIMQueryVars.Inverse.COMMENT, mode);
    }
}

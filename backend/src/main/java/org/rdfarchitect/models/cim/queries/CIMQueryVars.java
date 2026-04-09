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

package org.rdfarchitect.models.cim.queries;

/**
 * Class containing variables for SPARQL queries that are used in the context of CIM.
 * This is to ensure they are the same over multiple files.
 * This prohibits typos in query and results.
 */
public class CIMQueryVars {

    public static final String URI = "?uri";
    public static final String UUID = "?uuid";
    public static final String LABEL = "?label";
    public static final String COMMENT = "?comment";
    public static final String DOMAIN_URI = "?domainURI";
    public static final String DOMAIN_UUID = "?domainUUID";
    public static final String DOMAIN_LABEL = "?domainLabel";
    public static final String STEREOTYPE = "?stereotype";
    public static final String SUPER_CLASS_URI = "?superClassURI";
    public static final String SUPER_CLASS_LABEL = "?superClassLabel";
    public static final String MULTIPLICITY = "?multiplicity";
    public static final String IS_FIXED = "?isFixed";
    public static final String IS_DEFAULT = "?isDefault";
    public static final String DATA_TYPE_URI = "?typeURI";
    public static final String DATA_TYPE_LABEL = "?typeLabel";
    public static final String RANGE_URI = "?rangeURI";
    public static final String RANGE_LABEL = "?rangeLabel";
    public static final String ASSOCIATION_USED = "?associationUsed";
    public static final String INVERSE_ROLE_NAME = "?inverseRoleName";
    public static final String TYPE_URI = "?typeURI";
    public static final String TYPE_UUID = "?typeUUID";
    public static final String TYPE_LABEL = "?typeLabel";
    public static final String PACKAGE_URI = "?packageURI";
    public static final String PACKAGE_UUID = "?packageUUID";
    public static final String PACKAGE_LABEL = "?packageLabel";

    private CIMQueryVars() {
    }

    public static class Inverse {

        public static final String URI = "?inverseUri";
        public static final String UUID = "?inverseUUID";
        public static final String LABEL = "?inverseLabel";
        public static final String COMMENT = "?inverseComment";
        public static final String MULTIPLICITY = "?inverseMultiplicity";
        public static final String ASSOCIATION_USED = "?inverseAssociationUsed";

        private Inverse() {
        }
    }
}

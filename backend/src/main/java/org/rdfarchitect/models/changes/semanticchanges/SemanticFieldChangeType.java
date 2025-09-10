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

package org.rdfarchitect.models.changes.semanticchanges;

/**
 * Enum representing the types of changes that can occur to CIM Resources.
 */
public enum SemanticFieldChangeType {
    LABEL_CHANGE,
    COMMENT_CHANGE,
    SUPERCLASS_CHANGE,
    BELONGS_TO_CATEGORY_CHANGE,
    DATATYPE_CHANGE,
    MADE_OPTIONAL,
    MADE_REQUIRED,
    MULTIPLICITY_CHANGE,
    STEREOTYPE_ADDED,
    STEREOTYPE_REMOVED,
    MADE_ABSTRACT,
    DOMAIN_CHANGE,
    DOMAIN_RENAME,
    TARGET_CHANGE,
    ASSOCIATION_USED_CHANGE,
    DEFAULT_VALUE_CHANGE,
    FIXED_VALUE_CHANGE,
}

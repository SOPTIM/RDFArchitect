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

package org.rdfarchitect.api.dto.delete;

public enum DeleteAction {

    /** Delete the affected resource entirely. Applicable to all resource types. */
    DELETE,

    /**
     * Keep the affected resource as-is, even though it references a deleted resource. E.g. a class
     * that extends a deleted class — keep the class but accept that the parent reference becomes
     * invalid.
     */
    KEEP,

    /**
     * Remove the {@code cims:belongsToCategory} triple from a class whose package is being deleted.
     */
    REMOVE_PACKAGE_REFERENCE,

    /**
     * Remove the {@code rdfs:subClassOf} triple from a class whose parent class is being deleted.
     */
    REMOVE_SUBCLASS_REFERENCE;
}

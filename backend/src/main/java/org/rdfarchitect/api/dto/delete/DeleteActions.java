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

public enum DeleteActions {

    /**
     * Delete the affected resource entirely.
     * Applicable to all resource types.
     */
    DELETE,

    /**
     * Keep the affected resource as-is, even though
     * it references a deleted resource.
     * E.g. a class that extends a deleted class — keep the class
     * but accept that the parent reference becomes invalid.
     */
    KEEP,

    /**
     * Remove the reference to the deleted resource without
     * deleting the affected resource itself.
     * E.g. a class extends a deleted class — remove the
     * inheritance relationship but keep the class.
     */
    REMOVE_REFERENCE,

    //nur falls delete von enum entries implementiert wird, aber eher unwahrscheinlich
    /**
     * Unset the default value of an attribute when the
     * enum entry used as its default value is deleted.
     */
    UNSET_DEFAULT_VALUE,

    /**
     * Unset the fixed value of an attribute when the
     * enum entry used as its fixed value is deleted.
     */
    UNSET_FIXED_VALUE
}

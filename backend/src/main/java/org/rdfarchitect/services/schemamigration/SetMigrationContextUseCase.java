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

package org.rdfarchitect.services.schemamigration;

import org.rdfarchitect.database.GraphIdentifier;
import org.springframework.web.multipart.MultipartFile;

/** Use case for setting the migration context. */
public interface SetMigrationContextUseCase {

    /**
     * Sets the migration context for the given graph identifier of an edited schema loaded in
     * memory and the uploaded old schema updatesSchema.
     *
     * @param originalSchema the graph identifier of the edited schema loaded in memory
     * @param updatesSchema the uploaded old schema
     * @param ignorePrefixes whether to ignore prefixes in comparison and rename detection
     */
    void setMigrationContext(
            MultipartFile originalSchema, GraphIdentifier updatesSchema, boolean ignorePrefixes);

    /**
     * Sets the migration context for the given graph identifier of an edited schema loaded in
     * memory and the graph identifier of the old schema loaded in memory.
     *
     * @param originalSchema the graph identifier of the edited schema loaded in memory
     * @param updatedSchema the graph identifier of the old schema loaded in memory
     * @param ignorePrefixes whether to ignore prefixes in comparison and rename detection
     */
    void setMigrationContext(
            GraphIdentifier originalSchema, GraphIdentifier updatedSchema, boolean ignorePrefixes);

    /**
     * Sets the migration context for a stored original schema and an uploaded updated schema.
     *
     * @param originalSchema the graph identifier of the original schema loaded in memory
     * @param updatedSchema the uploaded updated schema
     * @param ignorePrefixes whether to ignore prefixes in comparison and rename detection
     */
    void setMigrationContext(
            GraphIdentifier originalSchema, MultipartFile updatedSchema, boolean ignorePrefixes);

    /**
     * Sets the migration context for the two uploaded schema files.
     *
     * @param originalSchema the graph identifier of the edited schema loaded in memory
     * @param updatedSchema the graph identifier of the old schema loaded in memory
     * @param ignorePrefixes whether to ignore prefixes in comparison and rename detection
     */
    void setMigrationContext(
            MultipartFile originalSchema, MultipartFile updatedSchema, boolean ignorePrefixes);
}

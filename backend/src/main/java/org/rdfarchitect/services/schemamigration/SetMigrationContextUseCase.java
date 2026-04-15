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

/**
 * Use case for setting the migration context.
 */
public interface SetMigrationContextUseCase {

    /**
     * Sets the migration context for the given graph identifier of an edited schema loaded in memory and the uploaded old schema updatesSchema.
     *
     * @param originalSchema the graph identifier of the edited schema loaded in memory
     * @param updatesSchema  the uploaded old schema
     */
    void setMigrationContext(MultipartFile originalSchema, GraphIdentifier updatesSchema);

    /**
     * Sets the migration context for the given graph identifier of an edited schema loaded in memory and the graph identifier of the old schema loaded in memory.
     *
     * @param originalSchema the graph identifier of the edited schema loaded in memory
     * @param updatedSchema  the graph identifier of the old schema loaded in memory
     */
    void setMigrationContext(GraphIdentifier originalSchema, GraphIdentifier updatedSchema);

    /**
     * Sets the migration context for the two uploaded schema files.
     *
     * @param originalSchema the graph identifier of the edited schema loaded in memory
     * @param updatedSchema  the graph identifier of the old schema loaded in memory
     */
    void setMigrationContext(MultipartFile originalSchema, MultipartFile updatedSchema);
}

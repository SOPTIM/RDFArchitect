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

package org.rdfarchitect.services.schemamigration.renamings;

import org.rdfarchitect.api.dto.migration.ResourceRenameOverview;
import org.rdfarchitect.models.changes.semanticchanges.SemanticClassChange;

/**
 * Interface for fetching a view that displays the various class changes between two schemas
 */
public interface GetClassRenamingsUseCase {

    /**
     * Returns the aggregated information on class changes between two schemas, including added, deleted and renamed classes.
     *
     * @return A view comprised of several lists of Class Changes and Rename Candidates
     */
    ResourceRenameOverview<SemanticClassChange> getClassRenamings();
}

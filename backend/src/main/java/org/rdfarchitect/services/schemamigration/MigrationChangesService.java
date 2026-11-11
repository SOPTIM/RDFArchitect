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

import lombok.RequiredArgsConstructor;

import org.rdfarchitect.context.MigrationSessionStore;
import org.rdfarchitect.models.changes.semanticchanges.SemanticClassChange;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Serves the final review step of the migration wizard: the changes as they stand after the
 * default-value step, plus any edits the user makes while reviewing them.
 */
@Service
@RequiredArgsConstructor
public class MigrationChangesService implements MigrationChangesUseCase {

    private final MigrationSessionStore migrationSessionStore;

    @Override
    public List<SemanticClassChange> getMigrationChanges() {
        return migrationSessionStore.getContext().getDiffAfterDefaultValueConfirm();
    }

    @Override
    public void confirmMigrationChanges(List<SemanticClassChange> changes) {
        migrationSessionStore.getContext().setDiffAfterDefaultValueConfirm(changes);
    }
}

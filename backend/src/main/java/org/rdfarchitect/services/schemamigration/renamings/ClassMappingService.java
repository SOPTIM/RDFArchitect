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

import lombok.RequiredArgsConstructor;

import org.rdfarchitect.api.dto.migration.ClassMappingOverview;
import org.rdfarchitect.context.MigrationSessionStore;
import org.rdfarchitect.models.changes.ResourceMapping;
import org.rdfarchitect.models.changes.semanticchanges.SemanticClassChange;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Drives the class step of the migration wizard: suggests which deleted class became which added
 * one and merges the confirmed renames into a single change object each.
 */
@Service
@RequiredArgsConstructor
public class ClassMappingService implements GetClassMappingsUseCase, ClassRenamingsUseCase {

    private final MigrationSessionStore migrationSessionStore;

    @Override
    public ClassMappingOverview getClassMappings() {
        var context = migrationSessionStore.getContext();
        var changes = context.getSemanticDiff();

        var renames = context.getResourceMappings();
        if (renames == null) {
            renames = RenameDetector.detectClassRenames(changes);
        }

        return new ClassMappingOverview(changes, renames);
    }

    @Override
    public void confirmClassRenamings(List<ResourceMapping<SemanticClassChange>> renames) {
        var context = migrationSessionStore.getContext();
        context.setResourceMappings(renames);
        var classChanges = new ArrayList<>(context.getSemanticDiff());

        for (var rename : renames) {
            classChanges.remove(rename.getNewResources().getFirst());
            classChanges.remove(rename.getOldResources().getFirst());
            classChanges.add(RenameObjectBuilder.createRenameObject(rename));
        }
        RenameReclassifier.reclassifyChangesDueToRenames(classChanges, renames);

        context.setDiffAfterClassConfirm(classChanges);
    }
}

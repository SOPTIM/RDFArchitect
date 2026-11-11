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

import lombok.experimental.UtilityClass;

import org.rdfarchitect.models.changes.ResourceMapping;
import org.rdfarchitect.models.changes.semanticchanges.SemanticClassChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticFieldChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticFieldChangeType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Downgrades field changes that are merely a consequence of a confirmed class rename to their
 * rename counterpart, so the user is not asked about a datatype, target or superclass that only
 * changed its name.
 */
@UtilityClass
public class RenameReclassifier {

    /**
     * Reclassifies e.g. DATATYPE_CHANGE to DATATYPE_RENAME where the change is simply a
     * consequence of a class rename confirmed in the class step.
     *
     * @param classChanges the class changes to reclassify in place
     * @param classRenames the confirmed class renames, may be {@code null} if none were confirmed
     */
    public void reclassifyChangesDueToRenames(
            List<SemanticClassChange> classChanges,
            List<ResourceMapping<SemanticClassChange>> classRenames) {
        if (classRenames == null) {
            return;
        }

        var renameMap = renamedIrisByOldIri(classRenames);

        for (var classChange : classChanges) {
            reclassifyIfRename(
                    classChange.getChanges(),
                    SemanticFieldChangeType.SUPERCLASS_CHANGE,
                    SemanticFieldChangeType.SUPERCLASS_RENAME,
                    renameMap);

            classChange
                    .getAssociations()
                    .forEach(
                            a ->
                                    reclassifyIfRename(
                                            a.getChanges(),
                                            SemanticFieldChangeType.TARGET_CHANGE,
                                            SemanticFieldChangeType.TARGET_RENAME,
                                            renameMap));

            classChange
                    .getAttributes()
                    .forEach(
                            a ->
                                    reclassifyIfRename(
                                            a.getChanges(),
                                            SemanticFieldChangeType.DATATYPE_CHANGE,
                                            SemanticFieldChangeType.DATATYPE_RENAME,
                                            renameMap));
        }
    }

    private Map<String, List<String>> renamedIrisByOldIri(
            List<ResourceMapping<SemanticClassChange>> classRenames) {
        var renamedIris = new HashMap<String, List<String>>();
        for (var rename : classRenames) {
            var newIris =
                    rename.getNewResources().stream().map(SemanticClassChange::getIri).toList();
            for (var oldResource : rename.getOldResources()) {
                renamedIris
                        .computeIfAbsent(oldResource.getIri(), iri -> new ArrayList<>())
                        .addAll(newIris);
            }
        }
        return renamedIris;
    }

    private void reclassifyIfRename(
            List<SemanticFieldChange> changes,
            SemanticFieldChangeType changeType,
            SemanticFieldChangeType renameType,
            Map<String, List<String>> renameMap) {

        for (var change : changes) {
            if (change.getSemanticFieldChangeType() == changeType
                    && renameMap
                            .getOrDefault(change.getFrom(), new ArrayList<>())
                            .contains(change.getTo())) {
                change.setSemanticFieldChangeType(renameType);
            }
        }
    }
}

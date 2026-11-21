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

package org.rdfarchitect.services.archive.parts;

import com.fasterxml.jackson.core.type.TypeReference;

import org.rdfarchitect.database.WorkspaceTransaction;
import org.rdfarchitect.database.inmemory.diagrams.CustomDiagram;
import org.rdfarchitect.dl.data.dto.relations.MRID;
import org.rdfarchitect.services.archive.ArchivedDiagrams;
import org.rdfarchitect.services.archive.ArchivedWorkspaceDiagram;
import org.rdfarchitect.services.archive.WorkspaceArchiveFormat;

import java.util.UUID;

/**
 * The custom diagrams of the workspace, which show classes of several graphs, the cross-profile
 * diagram and the default package the workspace layout belongs to.
 */
public final class WorkspaceDiagramsPart
        extends DiagramsPart<WorkspaceTransaction, ArchivedWorkspaceDiagram> {

    public WorkspaceDiagramsPart() {
        super(new TypeReference<>() {});
    }

    @Override
    ArchivedDiagrams<ArchivedWorkspaceDiagram> archive(
            WorkspaceTransaction source, PartWriter out) {
        return new ArchivedDiagrams<>(
                source.crossProfileDiagramId(),
                source.layout().getDefaultPackageMRID().getUuid(),
                sorted(source.diagrams().values()).stream()
                        .map(
                                diagram ->
                                        ArchivedWorkspaceDiagram.of(
                                                diagram, out.folderByGraphUri()))
                        .toList());
    }

    @Override
    boolean hasIds(ArchivedDiagrams<ArchivedWorkspaceDiagram> archived, PartReader in) {
        if (archived.crossProfileDiagramId() == null) {
            in.problem(WorkspaceArchiveFormat.DIAGRAMS, "\"crossProfileDiagramId\" is missing.");
            return false;
        }
        return true;
    }

    @Override
    UUID idOf(ArchivedWorkspaceDiagram diagram) {
        return diagram.diagramId();
    }

    @Override
    CustomDiagram restoreDiagram(ArchivedWorkspaceDiagram diagram, String where, PartReader in) {
        var valid = true;
        for (var folder : diagram.classes().keySet()) {
            if (!in.graphUriByFolder().containsKey(folder)) {
                in.problem(
                        WorkspaceArchiveFormat.DIAGRAMS,
                        where + ": \"" + folder + "\" is not a graph of the workspace file.");
                valid = false;
            }
        }
        return valid ? diagram.toCustomDiagram(in.graphUriByFolder()) : null;
    }

    @Override
    public void restore(WorkspaceTransaction target, ArchivedDiagrams<CustomDiagram> diagrams) {
        target.restoreCrossProfileDiagramId(diagrams.crossProfileDiagramId());
        target.layout().restoreDefaultPackageMRID(new MRID(diagrams.defaultPackageMRID()));
        diagrams.customDiagrams()
                .forEach(diagram -> target.diagrams().put(diagram.getDiagramId(), diagram));
    }
}

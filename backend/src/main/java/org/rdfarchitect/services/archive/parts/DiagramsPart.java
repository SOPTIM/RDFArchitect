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

import org.rdfarchitect.database.inmemory.diagrams.CustomDiagram;
import org.rdfarchitect.services.archive.ArchivedDiagrams;
import org.rdfarchitect.services.archive.WorkspaceArchiveFormat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/**
 * The custom diagrams of a graph or of the workspace, {@code diagrams.json}, together with the ids
 * the layout refers to.
 *
 * @param <S> where the diagrams live
 * @param <D> how a single diagram is stored
 */
abstract class DiagramsPart<S, D> implements WorkspaceFilePart<S, ArchivedDiagrams<CustomDiagram>> {

    private final TypeReference<ArchivedDiagrams<D>> type;

    DiagramsPart(TypeReference<ArchivedDiagrams<D>> type) {
        this.type = type;
    }

    @Override
    public void write(S source, PartWriter out) {
        out.writeJson(WorkspaceArchiveFormat.DIAGRAMS, archive(source, out));
    }

    @Override
    public ArchivedDiagrams<CustomDiagram> read(PartReader in) {
        var archived = in.readJson(WorkspaceArchiveFormat.DIAGRAMS, type);
        if (archived == null || !hasIds(archived, in)) {
            return null;
        }
        var diagrams = new ArrayList<CustomDiagram>();
        var ids = new HashSet<UUID>();
        var valid = true;
        for (var diagram : archived.customDiagrams()) {
            var where = "diagram " + idOf(diagram);
            if (!ids.add(idOf(diagram))) {
                in.problem(WorkspaceArchiveFormat.DIAGRAMS, where + " is there twice.");
                valid = false;
                continue;
            }
            try {
                var restored = restoreDiagram(diagram, where, in);
                if (restored == null) {
                    valid = false;
                } else {
                    diagrams.add(restored);
                }
            } catch (IllegalArgumentException e) {
                in.problem(WorkspaceArchiveFormat.DIAGRAMS, where + ": " + e.getMessage());
                valid = false;
            }
        }
        return valid
                ? new ArchivedDiagrams<>(
                        archived.crossProfileDiagramId(), archived.defaultPackageMRID(), diagrams)
                : null;
    }

    abstract ArchivedDiagrams<D> archive(S source, PartWriter out);

    /** Whether the ids beyond those every diagrams file has are there. */
    boolean hasIds(ArchivedDiagrams<D> archived, PartReader in) {
        return true;
    }

    abstract UUID idOf(D diagram);

    /**
     * @return the diagram, {@code null} if it reported a problem
     */
    abstract CustomDiagram restoreDiagram(D diagram, String where, PartReader in);

    static List<CustomDiagram> sorted(Collection<CustomDiagram> diagrams) {
        return diagrams.stream().sorted(Comparator.comparing(CustomDiagram::getDiagramId)).toList();
    }
}

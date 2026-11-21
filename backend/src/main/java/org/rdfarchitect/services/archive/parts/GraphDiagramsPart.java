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
import org.rdfarchitect.dl.data.dto.relations.MRID;
import org.rdfarchitect.services.archive.ArchivedDiagrams;
import org.rdfarchitect.services.archive.ArchivedGraphDiagram;

import java.util.UUID;

/** The custom diagrams of a graph and the default package its layout belongs to. */
public final class GraphDiagramsPart extends DiagramsPart<GraphScope, ArchivedGraphDiagram> {

    public GraphDiagramsPart() {
        super(new TypeReference<>() {});
    }

    @Override
    ArchivedDiagrams<ArchivedGraphDiagram> archive(GraphScope source, PartWriter out) {
        var graph = source.graph();
        return new ArchivedDiagrams<>(
                null,
                graph.getDiagramLayout().getDefaultPackageMRID().getUuid(),
                sorted(graph.getCustomDiagrams().values()).stream()
                        .map(diagram -> ArchivedGraphDiagram.of(diagram, source.uri()))
                        .toList());
    }

    @Override
    UUID idOf(ArchivedGraphDiagram diagram) {
        return diagram.diagramId();
    }

    @Override
    CustomDiagram restoreDiagram(ArchivedGraphDiagram diagram, String where, PartReader in) {
        return diagram.toCustomDiagram(in.graphUri());
    }

    @Override
    public void restore(GraphScope target, ArchivedDiagrams<CustomDiagram> diagrams) {
        var graph = target.graph();
        graph.getDiagramLayout().restoreDefaultPackageMRID(new MRID(diagrams.defaultPackageMRID()));
        diagrams.customDiagrams()
                .forEach(diagram -> graph.getCustomDiagrams().put(diagram.getDiagramId(), diagram));
    }
}

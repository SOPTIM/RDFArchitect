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

import org.apache.jena.graph.Graph;
import org.apache.jena.rdf.model.ModelFactory;
import org.rdfarchitect.database.DiagramLayout;
import org.rdfarchitect.services.archive.LayoutIris;
import org.rdfarchitect.services.archive.WorkspaceArchiveFormat;

import java.util.function.Function;

/** The diagram layout of a graph or of the workspace, {@code layout.ttl}. */
public final class LayoutPart<S> implements WorkspaceFilePart<S, Graph> {

    private final Function<S, DiagramLayout> layoutOf;

    public LayoutPart(Function<S, DiagramLayout> layoutOf) {
        this.layoutOf = layoutOf;
    }

    @Override
    public void write(S source, PartWriter out) {
        var layout = layoutOf.apply(source).getDiagramLayoutModel().getGraph();
        out.writeRdf(WorkspaceArchiveFormat.LAYOUT, LayoutIris.toArchived(layout));
    }

    @Override
    public Graph read(PartReader in) {
        var layout = in.readRdf(WorkspaceArchiveFormat.LAYOUT);
        return layout == null ? null : LayoutIris.fromArchived(layout);
    }

    @Override
    public void restore(S target, Graph layout) {
        layoutOf.apply(target)
                .getDiagramLayoutModel()
                .add(ModelFactory.createModelForGraph(layout));
    }
}

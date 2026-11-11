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
import org.rdfarchitect.rdf.graph.GraphUtils;
import org.rdfarchitect.services.archive.WorkspaceArchiveFormat;

/** The custom SHACL of a graph, {@code shacl.ttl}. */
public final class ShaclPart implements WorkspaceFilePart<GraphScope, Graph> {

    @Override
    public void write(GraphScope source, PartWriter out) {
        out.writeRdf(WorkspaceArchiveFormat.SHACL, source.graph().getCustomSHACL());
    }

    @Override
    public Graph read(PartReader in) {
        return in.readRdf(WorkspaceArchiveFormat.SHACL);
    }

    @Override
    public void restore(GraphScope target, Graph shacl) {
        var shaclModel = ModelFactory.createModelForGraph(target.graph().getCustomSHACL());
        var normalized = GraphUtils.normalizeBlankNodes(shacl);
        shaclModel.add(ModelFactory.createModelForGraph(normalized));
        shaclModel.setNsPrefixes(normalized.getPrefixMapping());
    }
}

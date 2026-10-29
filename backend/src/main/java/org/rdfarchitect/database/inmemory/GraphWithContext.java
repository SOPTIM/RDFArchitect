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

package org.rdfarchitect.database.inmemory;

import org.apache.jena.graph.Graph;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.sparql.graph.GraphFactory;
import org.rdfarchitect.database.GraphContext;
import org.rdfarchitect.database.inmemory.diagrams.CustomDiagram;
import org.rdfarchitect.database.inmemory.diagrams.CustomDiagramCollection;
import org.rdfarchitect.models.cim.CIMModifyingUtils;
import org.rdfarchitect.rdf.graph.GraphUtils;
import org.rdfarchitect.rdf.graph.wrapper.DiagramLayoutDelta;
import org.rdfarchitect.rdf.graph.wrapper.RDFGraphDelta;
import org.rdfarchitect.rdf.graph.wrapper.WorkspaceTransactionContext;

import java.util.Map;
import java.util.UUID;

/**
 * The contents of one named graph: RDF schema, diagram layout, custom SHACL and custom diagrams.
 *
 * <p>Holds no transaction and no lock. The graph takes part in the transaction of the workspace
 * that owns it, which is also what decides when its participants commit, abort or step through
 * history. Instances are handed out only through a running {@link
 * org.rdfarchitect.database.WorkspaceTransaction}.
 */
public class GraphWithContext implements GraphContext {

    private final RDFGraphDelta rdfGraph;
    private final DiagramLayoutDelta diagramLayout;
    private final RDFGraphDelta customSHACL;
    private final CustomDiagramCollection customDiagrams;

    /**
     * Builds the graph from {@code base}. Must be called inside a write transaction of the owning
     * workspace, because filling the RDF delta is itself a write.
     *
     * @param base the initial contents
     * @param txnContext the transaction context of the owning workspace
     */
    public GraphWithContext(Graph base, WorkspaceTransactionContext txnContext) {
        GraphUtils.enhanceWithUUIDs(base);
        CIMModifyingUtils.replaceCommentDatatype(base);
        this.rdfGraph = new RDFGraphDelta(GraphFactory.createDefaultGraph(), txnContext);
        var rdfModel = ModelFactory.createModelForGraph(rdfGraph);
        rdfModel.setNsPrefixes(base.getPrefixMapping());
        rdfModel.add(ModelFactory.createModelForGraph(base));
        this.diagramLayout = new DiagramLayoutDelta(txnContext);
        this.customSHACL = new RDFGraphDelta(GraphFactory.createDefaultGraph(), txnContext);
        this.customDiagrams = new CustomDiagramCollection(txnContext);
    }

    @Override
    public RDFGraphDelta getRdfGraph() {
        return rdfGraph;
    }

    @Override
    public DiagramLayoutDelta getDiagramLayout() {
        return diagramLayout;
    }

    @Override
    public RDFGraphDelta getCustomSHACL() {
        return customSHACL;
    }

    @Override
    public Map<UUID, CustomDiagram> getCustomDiagrams() {
        return customDiagrams.get();
    }

    /**
     * The collection behind {@link #getCustomDiagrams()}, for the workspace to enrol and commit.
     */
    CustomDiagramCollection customDiagrams() {
        return customDiagrams;
    }
}

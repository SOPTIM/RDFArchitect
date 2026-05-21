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

package org.rdfarchitect.rdf.graph.wrapper;

import lombok.Getter;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.sparql.graph.GraphFactory;
import org.apache.jena.vocabulary.RDF;
import org.rdfarchitect.config.GraphCompressionConfig;
import org.rdfarchitect.dl.data.dto.relations.MRID;
import org.rdfarchitect.dl.rdf.resources.CIM;
import org.rdfarchitect.rdf.graph.DeltaCompressible;

import java.util.UUID;

/**
 * Transactional diagram-layout store backed by an {@link RDFGraphDelta}. Has no lock of its own —
 * transaction lifecycle is managed exclusively by the owning coordinator.
 */
public class DiagramLayoutDelta implements TransactionParticipant, Rewindable {

    @Getter private final MRID defaultPackageMRID;
    private final RDFGraphDelta inner;

    public DiagramLayoutDelta(TransactionContext txnContext) {
        this.defaultPackageMRID = new MRID(UUID.randomUUID());
        var emptyBase = GraphFactory.createDefaultGraph();
        var prefixModel = ModelFactory.createModelForGraph(emptyBase);
        prefixModel.setNsPrefix(CIM.PREFIX, CIM.NAMESPACE);
        prefixModel.setNsPrefix("rdf", RDF.uri);
        int maxVersions = GraphCompressionConfig.getMaxVersions();
        int compressCount = GraphCompressionConfig.getCompressCount();
        this.inner = new RDFGraphDelta(emptyBase, maxVersions, compressCount, txnContext);
    }

    /**
     * Returns a live {@link Model} view of the current diagram-layout state. Modifications to the
     * returned model are written into the active delta and will be committed or aborted together
     * with the enclosing transaction.
     */
    public Model getDiagramLayoutModel() {
        return ModelFactory.createModelForGraph(inner);
    }

    /**
     * Returns a live {@link Model} view of the last <em>committed</em> diagram-layout state,
     * bypassing the transaction layer. Reads and writes succeed without an active transaction.
     * Writes go directly into the committed head delta and are not tracked as a separate undo entry
     * — they survive undo/redo of semantic changes. Use this for infrastructure operations (layout
     * initialisation, auto-positions) that must not pollute the undo history.
     */
    public Model getDiagramLayoutModelDirect() {
        return ModelFactory.createModelForGraph(inner.getLastDelta());
    }

    // -------------------------------------------------------------------------
    // TransactionParticipant
    // -------------------------------------------------------------------------

    @Override
    public void commit() {
        inner.commit();
    }

    @Override
    public void abort() {
        inner.abort();
    }

    @Override
    public boolean hasChanges() {
        return inner.hasChanges();
    }

    // -------------------------------------------------------------------------
    // Rewindable
    // -------------------------------------------------------------------------

    @Override
    public void undo() {
        inner.undo();
    }

    @Override
    public void redo() {
        inner.redo();
    }

    @Override
    public boolean canUndo() {
        return inner.canUndo();
    }

    @Override
    public boolean canRedo() {
        return inner.canRedo();
    }

    @Override
    public void restore(UUID versionId) {
        inner.restore(versionId);
    }

    @Override
    public DeltaCompressible getLastDelta() {
        return inner.getLastDelta();
    }
}

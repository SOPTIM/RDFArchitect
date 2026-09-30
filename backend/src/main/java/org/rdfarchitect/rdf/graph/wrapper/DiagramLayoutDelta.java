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
import org.rdfarchitect.dl.data.dto.relations.MRID;
import org.rdfarchitect.dl.rdf.resources.CIM;
import org.rdfarchitect.models.changelog.ChangeLogParticipant;
import org.rdfarchitect.rdf.graph.DeltaCompressible;

import java.util.UUID;

/**
 * Transactional diagram-layout store backed by an {@link RDFGraphDelta}, used both for a graph's
 * layout and for the workspace's own. Has no lock of its own — the owning workspace drives the
 * transaction.
 */
public class DiagramLayoutDelta
        implements TransactionParticipant, DeltaSource, ChangeLogParticipant {

    @Getter private final MRID defaultPackageMRID;
    private final RDFGraphDelta inner;

    public DiagramLayoutDelta(WorkspaceTransactionContext txnContext) {
        this.defaultPackageMRID = new MRID(UUID.randomUUID());
        var emptyBase = GraphFactory.createDefaultGraph();
        var prefixModel = ModelFactory.createModelForGraph(emptyBase);
        prefixModel.setNsPrefix(CIM.PREFIX, CIM.NAMESPACE);
        prefixModel.setNsPrefix("rdf", RDF.uri);
        this.inner = new RDFGraphDelta(emptyBase, txnContext, this);
    }

    /**
     * Returns a live {@link Model} view of the current diagram-layout state. Modifications to the
     * returned model are written into the active delta and will be committed or aborted together
     * with the enclosing transaction.
     */
    public Model getDiagramLayoutModel() {
        return ModelFactory.createModelForGraph(inner);
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
    // ChangeLogParticipant and DeltaSource
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
    public DeltaCompressible getLastDelta() {
        return inner.getLastDelta();
    }

    @Override
    public void discardOldestVersion() {
        inner.discardOldestVersion();
    }

    @Override
    public void discardRedoHistory() {
        inner.discardRedoHistory();
    }
}

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

package org.rdfarchitect.rdf.graph;

import lombok.Getter;

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.GraphMemFactory;
import org.apache.jena.graph.GraphUtil;
import org.apache.jena.graph.Triple;
import org.apache.jena.graph.compose.CompositionBase;
import org.apache.jena.graph.impl.SimpleEventManager;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.sparql.graph.GraphFactory;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Graph operation for wrapping a base graph and leaving it unchanged while recording all the
 * attempted updates for later access.
 *
 * <p>The behavior of this class is not well-defined if triples are added to or removed from the
 * base graph, the additions graph, or the deletions graph while this graph is in use.
 */
public class DeltaCompressible extends CompositionBase {

    /** -- GETTER -- Answer the base graph. */
    @Getter private Graph base;

    /** -- GETTER -- Answer the graph of all triples added. */
    @Getter private Graph additions;

    /** -- GETTER -- Answer the graph of all triples removed. */
    @Getter private Graph deletions;

    /**
     * Records prefix changes as a delta over the base graph's prefix mapping, mirroring the way
     * {@link #additions} and {@link #deletions} record triple changes.
     */
    private DeltaPrefixMapping prefixMapping;

    @Getter private UUID versionId = UUID.randomUUID();

    /**
     * -- GETTER -- Answer whether this delta has been compressed: its content is a base of its own
     * then, and what it added and removed is no longer known, so it cannot be {@link #rebaseOnto
     * rebased}.
     */
    @Getter private boolean compressed;

    /**
     * Takes over the identity of the version this delta replaces.
     *
     * <p>Only for folding two versions into one: the result is still the version the changelog
     * recorded, with more content in it, so it has to keep answering to the same id.
     *
     * @param replaced the id of the version being folded into this one
     */
    public void adoptVersionId(UUID replaced) {
        this.versionId = replaced;
    }

    public DeltaCompressible(@NotNull Graph base) {
        super();
        this.base = base;
        this.additions = GraphMemFactory.createDefaultGraph();
        this.deletions = GraphMemFactory.createDefaultGraph();
        this.prefixMapping = new DeltaPrefixMapping(base.getPrefixMapping());
    }

    public void compress() {
        var newBase = GraphFactory.createDefaultGraph();
        GraphUtil.add(newBase, this.find());
        // Fold the prefix delta into the new base, then start a fresh (empty) prefix delta.
        newBase.getPrefixMapping().setNsPrefixes(getPrefixMapping().getNsPrefixMap());
        base = newBase;
        additions = GraphMemFactory.createDefaultGraph();
        deletions = GraphMemFactory.createDefaultGraph();
        prefixMapping = new DeltaPrefixMapping(base.getPrefixMapping());
        compressed = true;
    }

    /**
     * Puts this delta on top of a different base while keeping what it changes: the same triples
     * and prefixes are added and removed, measured against the new base.
     *
     * <p>The additions and deletions graphs stay the same objects, so whoever holds on to them —
     * the changelog keeps references for showing what a change did — still sees this delta.
     *
     * <p>The triples are replayed, not reconciled: an addition the new base already holds and a
     * deletion it no longer holds drop out, and nothing else is compared. That is only sound when
     * the new base differs from the old one in triples this delta does not touch. Where both write
     * about the same subject, say two positions for one diagram object, the result holds both.
     *
     * <p>A {@link #isCompressed() compressed} delta records no additions or deletions, so it would
     * come out of this equal to the new base; it must not be rebased.
     *
     * @param newBase the graph this delta now applies to
     */
    public void rebaseOnto(@NotNull Graph newBase) {
        var added = additions.find().toList();
        var deleted = deletions.find().toList();
        var addedPrefixes = prefixMapping.getAddedPrefixes();
        var deletedPrefixes = prefixMapping.getDeletedPrefixes();

        base = newBase;
        additions.clear();
        deletions.clear();
        prefixMapping = new DeltaPrefixMapping(newBase.getPrefixMapping());

        deleted.forEach(this::performDelete);
        added.forEach(this::performAdd);
        deletedPrefixes.keySet().forEach(prefixMapping::removeNsPrefix);
        prefixMapping.setNsPrefixes(addedPrefixes);
    }

    /** Add the triple to the graph, ie add it to the additions, remove it from the removals. */
    @Override
    public void performAdd(Triple t) {
        if (!base.contains(t)) {
            additions.add(t);
        }
        deletions.delete(t);
    }

    /** Remove the triple, ie, remove it from the adds, add it to the removals. */
    @Override
    public void performDelete(Triple t) {
        additions.delete(t);
        if (base.contains(t)) {
            deletions.add(t);
        }
    }

    /**
     * Find all the base triples matching tm, exclude the ones that are deleted, add the ones that
     * have been added.
     */
    @Override
    protected ExtendedIterator<Triple> graphBaseFind(Triple t) {
        ExtendedIterator<Triple> iterator =
                base.find(t).filterDrop(deletions::contains).andThen(additions.find(t));
        return SimpleEventManager.notifyingRemove(this, iterator);
    }

    /**
     * Returns the delta-aware prefix mapping. Writes are recorded as additions/deletions over the
     * base prefix mapping; reads fold the delta over the base.
     */
    @Override
    public PrefixMapping getPrefixMapping() {
        return prefixMapping;
    }

    /**
     * @return {@code true} if any prefix has been added or removed relative to the base
     */
    public boolean hasPrefixChanges() {
        return prefixMapping.hasChanges();
    }

    @Override
    public void close() {
        super.close();
        if (!base.isClosed()) {
            base.close();
        }
        additions.close();
        deletions.close();
    }

    @Override
    protected int graphBaseSize() {
        return base.size() + additions.size() - deletions.size();
    }
}

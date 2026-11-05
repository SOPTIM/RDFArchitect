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

package org.rdfarchitect.database;

import lombok.Getter;

import org.apache.jena.query.ReadWrite;
import org.apache.jena.sparql.graph.GraphFactory;
import org.rdfarchitect.exception.graph.GraphTransactionException;
import org.rdfarchitect.models.changelog.ValueChange;
import org.rdfarchitect.rdf.graph.wrapper.RDFGraphDelta;
import org.rdfarchitect.rdf.graph.wrapper.SnapshotParticipant;
import org.rdfarchitect.rdf.graph.wrapper.WorkspaceTransactionContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * One set of SHACL shapes belonging to a graph — typically an official constraints file that was
 * imported, or a set of shapes authored in RDFArchitect.
 *
 * <p>A graph holds any number of these. They are kept apart rather than merged into one shapes
 * graph so that each can be replaced, exported, validated and reported on individually — merging
 * would make it impossible to say which file a constraint came from, or to hand an imported file
 * back unchanged.
 *
 * <h2>Why the raw text is authoritative</h2>
 *
 * <p>{@link #getRawText()} is the content as the user last saw it; {@link #getGraph()} is what
 * parsing that text produced. Official ENTSO-E constraint files carry comments and a deliberate
 * ordering that users expect to get back byte-for-byte, and reporting a validation finding at a
 * line and column is only possible against the original text — a Jena round-trip destroys both.
 *
 * <h2>History</h2>
 *
 * <p>A document is two participants of the workspace's transaction: its triples, and the text with
 * the name, position and enabled flag as one {@link State} value. An undo therefore brings back the
 * text that belonged to the triples it restores, comments and all, and renaming or switching a
 * document off is undone like any other change.
 */
public final class ShapesDocument {

    /** Where a document came from, which decides how carefully its formatting is preserved. */
    public enum Origin {
        /** Uploaded from a file — treat its text as something the user wants back unchanged. */
        IMPORTED,
        /** Authored in RDFArchitect. */
        AUTHORED
    }

    /**
     * Everything about a document that is not its triples, versioned as one value.
     *
     * @param name display name, unique within a graph
     * @param sourceFileName file the document was uploaded from, or {@code null}
     * @param enabled whether the shapes take part in validation and combined export
     * @param order position in the graph's document list, and the merge order for export
     * @param rawText verbatim source text, or {@code null} when only the triples are known; kept
     *     deflated, since every version in the history holds one
     */
    public record State(
            String name, String sourceFileName, boolean enabled, int order, StoredText rawText) {}

    /**
     * A participant that belongs to a document.
     *
     * <p>A changelog entry holds on to the participants it names, including those of a document
     * that has been deleted since. This is how they can still be told apart: the workspace has to
     * say which graph such a participant belongs to in order to step or restore it.
     */
    public interface Part {

        /** Returns whatever the document was created for, which identifies the graph holding it. */
        Object holder();
    }

    /** A version's text, inflated, and which stored text it came from. */
    private record Inflated(StoredText source, String text) {}

    @Getter private final UUID id;

    @Getter private final Origin origin;

    private final Object holder;

    /** Parsed shapes. */
    private final Shapes shapes;

    private final Metadata metadata;

    /**
     * The text of the version last read or written. Reads far outnumber versions — validation, the
     * editor and the class dialogs all ask for it — so the current text is inflated once rather
     * than on every read. Only this one copy is kept whole; the history holds deflated text.
     */
    private volatile Inflated inflated;

    /**
     * Creates a document taking part in the transactions of {@code txnContext}.
     *
     * @param id the document's id
     * @param origin where it came from
     * @param initial its metadata and text
     * @param txnContext the transaction context of the owning workspace
     * @param holder what holds the document, handed back by its participants' {@link Part#holder()}
     */
    public ShapesDocument(
            UUID id,
            Origin origin,
            State initial,
            WorkspaceTransactionContext txnContext,
            Object holder) {
        this.id = id;
        this.origin = origin;
        this.holder = holder;
        this.shapes = new Shapes(txnContext);
        this.metadata = new Metadata(txnContext, initial);
    }

    /** Returns the parsed shapes. */
    public RDFGraphDelta getGraph() {
        return shapes;
    }

    public String getName() {
        return metadata.current().name();
    }

    public String getSourceFileName() {
        return metadata.current().sourceFileName();
    }

    public boolean isEnabled() {
        return metadata.current().enabled();
    }

    public int getOrder() {
        return metadata.current().order();
    }

    /** Verbatim source text; see the class comment on why this is authoritative. */
    public String getRawText() {
        var stored = metadata.current().rawText();
        if (stored == null) {
            return null;
        }
        var cached = inflated;
        if (cached != null && cached.source() == stored) {
            return cached.text();
        }
        var text = stored.text();
        inflated = new Inflated(stored, text);
        return text;
    }

    public void setName(String name) {
        var s = metadata.current();
        metadata.set(new State(name, s.sourceFileName(), s.enabled(), s.order(), s.rawText()));
    }

    public void setSourceFileName(String sourceFileName) {
        var s = metadata.current();
        metadata.set(new State(s.name(), sourceFileName, s.enabled(), s.order(), s.rawText()));
    }

    public void setEnabled(boolean enabled) {
        var s = metadata.current();
        metadata.set(new State(s.name(), s.sourceFileName(), enabled, s.order(), s.rawText()));
    }

    public void setOrder(int order) {
        var s = metadata.current();
        metadata.set(new State(s.name(), s.sourceFileName(), s.enabled(), order, s.rawText()));
    }

    public void setRawText(String rawText) {
        var s = metadata.current();
        var stored = StoredText.of(rawText);
        metadata.set(new State(s.name(), s.sourceFileName(), s.enabled(), s.order(), stored));
        if (stored != null) {
            inflated = new Inflated(stored, rawText);
        }
    }

    /**
     * Identifies what the document holds, for telling whether anything derived from it is still
     * good. Two reads with the same answer saw the same triples, text and metadata, and an undo
     * returns the answer of the state it goes back to.
     */
    public String contentVersion() {
        var s = metadata.current();
        return String.join(
                "|",
                id.toString(),
                String.valueOf(shapes.versionIdAt(0)),
                s.name(),
                String.valueOf(s.sourceFileName()),
                String.valueOf(s.enabled()),
                String.valueOf(s.order()),
                s.rawText() == null ? "" : s.rawText().size() + ":" + s.rawText().hashCode());
    }

    private final class Shapes extends RDFGraphDelta implements Part {

        Shapes(WorkspaceTransactionContext txnContext) {
            super(GraphFactory.createDefaultGraph(), txnContext);
        }

        @Override
        public Object holder() {
            return holder;
        }
    }

    private final class Metadata extends SnapshotParticipant<State> implements Part {

        private final WorkspaceTransactionContext txnContext;
        private volatile State state;

        Metadata(WorkspaceTransactionContext txnContext, State initial) {
            super(txnContext);
            this.txnContext = txnContext;
            this.state = Objects.requireNonNull(initial);
        }

        State current() {
            return state;
        }

        void set(State next) {
            beginChange();
            // Announcing a change returns quietly in a read transaction, so that a reference can
            // be handed to a reader. This is not a reference but the change itself, and it would
            // go unrecorded.
            if (txnContext.transactionMode() != ReadWrite.WRITE) {
                throw new GraphTransactionException("Cannot write in a read transaction.");
            }
            state = next;
        }

        @Override
        public Object holder() {
            return holder;
        }

        @Override
        protected State snapshot() {
            return state;
        }

        @Override
        protected void restore(State previous) {
            state = previous;
        }

        /**
         * Says what changed under the document's name, because moving one document renumbers the
         * others and a single change can reach several of them. The text is given by size, as the
         * difference between two files is no value to show in a table.
         */
        @Override
        protected List<ValueChange> describeValueChanges(State before, State after) {
            var label = after.name();
            var changes =
                    new ArrayList<>(
                            ValueChange.between(values(label, before), values(label, after)));
            if (!Objects.equals(before.rawText(), after.rawText())) {
                changes.add(new ValueChange(label + ": text", sizeOf(before), sizeOf(after)));
            }
            return changes;
        }

        private static Map<String, String> values(String label, State state) {
            var values = new HashMap<String, String>();
            values.put(label + ": name", state.name());
            values.put(label + ": source file", state.sourceFileName());
            values.put(label + ": enabled", String.valueOf(state.enabled()));
            values.put(label + ": position", String.valueOf(state.order()));
            return values;
        }

        private static String sizeOf(State state) {
            return state.rawText() == null ? null : state.rawText().size() + " bytes";
        }
    }
}

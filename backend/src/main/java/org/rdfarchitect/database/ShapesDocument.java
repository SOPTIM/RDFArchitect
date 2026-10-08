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

import org.rdfarchitect.config.GraphCompressionConfig;
import org.rdfarchitect.rdf.graph.wrapper.RDFGraphDelta;
import org.rdfarchitect.rdf.graph.wrapper.VersionedValue;

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
 * <p>Both halves are versioned by the owning context: the graph as a participant of its own, and
 * the text together with the name, position and enabled flag as one {@link State} value. An undo
 * therefore brings back the text that belonged to the triples it restores, comments and all, and
 * renaming or switching a document off is undone like any other change.
 */
public class ShapesDocument {

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

    /** A version's text, inflated, and which stored text it came from. */
    private record Inflated(StoredText source, String text) {}

    @Getter private final UUID id;

    @Getter private final Origin origin;

    /** Parsed shapes. */
    @Getter private final RDFGraphDelta graph;

    private final VersionedValue<State> state;

    /**
     * The text of the version last read or written. Reads far outnumber versions — validation, the
     * editor and the class dialogs all ask for it — so the current text is inflated once rather
     * than on every read. Only this one copy is kept whole; the history holds deflated text.
     */
    private volatile Inflated inflated;

    /** A document outside any context, whose metadata is not versioned. */
    public ShapesDocument(UUID id, String name, Origin origin, RDFGraphDelta graph) {
        this(
                id,
                origin,
                graph,
                new VersionedValue<>(
                        new State(name, null, true, 0, null),
                        GraphCompressionConfig.getMaxVersions(),
                        GraphCompressionConfig.getCompressCount()));
    }

    /**
     * A document whose metadata is kept in {@code state}, which the owning context commits and
     * rewinds alongside {@code graph}.
     */
    public ShapesDocument(
            UUID id, Origin origin, RDFGraphDelta graph, VersionedValue<State> state) {
        this.id = id;
        this.origin = origin;
        this.graph = graph;
        this.state = state;
    }

    public String getName() {
        return state.get().name();
    }

    public String getSourceFileName() {
        return state.get().sourceFileName();
    }

    public boolean isEnabled() {
        return state.get().enabled();
    }

    public int getOrder() {
        return state.get().order();
    }

    /** Verbatim source text; see the class comment on why this is authoritative. */
    public String getRawText() {
        var stored = state.get().rawText();
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
        var s = state.get();
        state.set(new State(name, s.sourceFileName(), s.enabled(), s.order(), s.rawText()));
    }

    public void setSourceFileName(String sourceFileName) {
        var s = state.get();
        state.set(new State(s.name(), sourceFileName, s.enabled(), s.order(), s.rawText()));
    }

    public void setEnabled(boolean enabled) {
        var s = state.get();
        state.set(new State(s.name(), s.sourceFileName(), enabled, s.order(), s.rawText()));
    }

    public void setOrder(int order) {
        var s = state.get();
        state.set(new State(s.name(), s.sourceFileName(), s.enabled(), order, s.rawText()));
    }

    public void setRawText(String rawText) {
        var s = state.get();
        var stored = StoredText.of(rawText);
        state.set(new State(s.name(), s.sourceFileName(), s.enabled(), s.order(), stored));
        if (stored != null) {
            inflated = new Inflated(stored, rawText);
        }
    }
}

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

package org.rdfarchitect.models.changelog;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.exception.graph.GraphVersionControlException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

class WorkspaceChangeLogTest {

    private static final int MAX_ENTRIES = 5;

    private List<String> calls;
    private RecordingParticipant graphA;
    private RecordingParticipant graphB;
    private RecordingParticipant layout;
    private WorkspaceChangeLog log;

    @BeforeEach
    void setUp() {
        calls = new ArrayList<>();
        graphA = new RecordingParticipant("graphA", calls);
        graphB = new RecordingParticipant("graphB", calls);
        layout = new RecordingParticipant("layout", calls);
        log = new WorkspaceChangeLog("imported graphs", MAX_ENTRIES);
    }

    // -------------------------------------------------------------------------
    // State after loading
    // -------------------------------------------------------------------------

    @Test
    void freshLog_holdsTheLoadedStateAndCannotBeUndone() {
        assertThat(log.canUndo()).isFalse();
        assertThat(log.canRedo()).isFalse();
        assertThat(log.undoHistory())
                .singleElement()
                .extracting(WorkspaceChangeLogEntry::message)
                .isEqualTo("imported graphs");
    }

    // -------------------------------------------------------------------------
    // Undo and redo
    // -------------------------------------------------------------------------

    @Test
    void push_enablesUndo() {
        log.push(commit("renamed class", rdfOf(graphA, "urn:a")));

        assertThat(log.canUndo()).isTrue();
    }

    @Test
    void undo_stepsEveryParticipantOfTheCommitBack() {
        log.push(commit("copied class", rdfOf(graphA, "urn:a"), rdfOf(graphB, "urn:b")));
        calls.clear();

        log.undo();

        assertThat(calls).containsExactly("graphB.undo", "graphA.undo");
    }

    @Test
    void undo_movesTheEntryOntoTheRedoStack() {
        log.push(commit("copied class", rdfOf(graphA, "urn:a")));

        var undone = log.undo();

        assertThat(log.canUndo()).isFalse();
        assertThat(log.canRedo()).isTrue();
        assertThat(log.redoHistory()).containsExactly(undone);
    }

    @Test
    void redo_stepsEveryParticipantForwardAgain() {
        log.push(commit("copied class", rdfOf(graphA, "urn:a"), rdfOf(graphB, "urn:b")));
        log.undo();
        calls.clear();

        log.redo();

        assertThat(calls).containsExactly("graphA.redo", "graphB.redo");
        assertThat(log.canUndo()).isTrue();
        assertThat(log.canRedo()).isFalse();
    }

    @Test
    void undo_whenOnlyTheLoadedStateRemains_throwsException() {
        assertThatThrownBy(() -> log.undo())
                .isInstanceOf(GraphVersionControlException.class)
                .hasMessageContaining("no history");
    }

    @Test
    void redo_withoutAnUndoneCommit_throwsException() {
        assertThatThrownBy(() -> log.redo())
                .isInstanceOf(GraphVersionControlException.class)
                .hasMessageContaining("no future history");
    }

    @Test
    void undo_walksRepeatedParticipantsBackwards() {
        log.push(commit("moved twice", dlOf(layout), dlOf(layout)));
        calls.clear();

        log.undo();

        assertThat(calls).containsExactly("layout.undo", "layout.undo");
    }

    // -------------------------------------------------------------------------
    // Invariant: a commit spanning several graphs is one step
    // -------------------------------------------------------------------------

    @Test
    void entrySpanningTwoGraphs_isASingleUndoStep() {
        log.push(commit("deleted two graphs", rdfOf(graphA, "urn:a"), rdfOf(graphB, "urn:b")));

        log.undo();

        assertThat(log.canUndo()).isFalse();
        assertThat(calls).contains("graphA.undo", "graphB.undo");
    }

    @Test
    void entry_namesTheGraphsItTouched() {
        var entry =
                commit(
                        "copied class",
                        rdfOf(graphA, "urn:a"),
                        rdfOf(graphB, "urn:b"),
                        dlOf(layout));

        assertThat(entry.affectedGraphUris()).containsExactlyInAnyOrder("urn:a", "urn:b");
    }

    // -------------------------------------------------------------------------
    // Invariant: abandoning the redo branch cleans up its participants
    // -------------------------------------------------------------------------

    @Test
    void push_afterUndo_tellsTheAbandonedParticipantsToForgetTheirRedoHistory() {
        log.push(commit("moved class", dlOf(layout)));
        log.undo();
        calls.clear();

        log.push(commit("renamed class", rdfOf(graphB, "urn:b")));

        assertThat(calls).contains("layout.discardRedoHistory");
        assertThat(log.canRedo()).isFalse();
    }

    @Test
    void push_withoutARedoBranch_discardsNothing() {
        log.push(commit("renamed class", rdfOf(graphA, "urn:a")));
        calls.clear();

        log.push(commit("renamed again", rdfOf(graphA, "urn:a")));

        assertThat(calls).isEmpty();
    }

    // -------------------------------------------------------------------------
    // Invariant: the log decides when history falls off the end
    // -------------------------------------------------------------------------

    @Test
    void push_upToTheRetentionBound_onlyDropsTheLoadedState() {
        for (int i = 0; i < MAX_ENTRIES; i++) {
            log.push(commit("change " + i, rdfOf(graphA, "urn:a")));
        }

        assertThat(calls).doesNotContain("graphA.discardOldestVersion");
        assertThat(log.undoHistory()).hasSize(MAX_ENTRIES);
    }

    @Test
    void push_beyondTheRetentionBound_foldsTheOldestEntryIntoTheParticipantsBase() {
        for (int i = 0; i <= MAX_ENTRIES; i++) {
            log.push(commit("change " + i, rdfOf(graphA, "urn:a")));
        }

        assertThat(calls).filteredOn("graphA.discardOldestVersion"::equals).hasSize(1);
        assertThat(log.undoHistory()).hasSize(MAX_ENTRIES);
    }

    @Test
    void push_beyondTheRetentionBound_keepsUndoAvailableForWhatRemains() {
        for (int i = 0; i < MAX_ENTRIES * 2; i++) {
            log.push(commit("change " + i, rdfOf(graphA, "urn:a")));
        }

        for (int i = 0; i < MAX_ENTRIES - 1; i++) {
            log.undo();
        }

        assertThat(log.canUndo()).isFalse();
        assertThat(log.undoHistory()).hasSize(1);
    }

    // -------------------------------------------------------------------------
    // Restoring and forgetting
    // -------------------------------------------------------------------------

    @Test
    void restoreTo_undoesEverythingRecordedAfterTheGivenChange() {
        var target = commit("first", rdfOf(graphA, "urn:a"));
        log.push(target);
        log.push(commit("second", rdfOf(graphA, "urn:a")));
        log.push(commit("third", rdfOf(graphB, "urn:b")));
        calls.clear();

        log.restoreTo(target.changeId());

        assertThat(calls).containsExactly("graphB.undo", "graphA.undo");
        assertThat(log.undoHistory().getFirst()).isEqualTo(target);
    }

    @Test
    void restoreTo_unknownChange_throwsException() {
        assertThatThrownBy(() -> log.restoreTo(UUID.randomUUID()))
                .isInstanceOf(GraphVersionControlException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void forgetHistory_keepsTheCurrentStateAsTheStartingPoint() {
        log.push(commit("loaded a graph", rdfOf(graphA, "urn:a")));
        calls.clear();

        log.forgetHistory("loaded workspace");

        assertThat(log.canUndo()).isFalse();
        assertThat(log.canRedo()).isFalse();
        assertThat(calls).containsExactly("graphA.discardOldestVersion");
        assertThat(log.undoHistory())
                .singleElement()
                .extracting(WorkspaceChangeLogEntry::message)
                .isEqualTo("loaded workspace");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static WorkspaceChangeLogEntry commit(String message, ParticipantVersion... versions) {
        return WorkspaceChangeLogEntry.of(message, List.of(versions));
    }

    private static ParticipantVersion rdfOf(ChangeLogParticipant participant, String graphUri) {
        return ParticipantVersion.of(
                ParticipantId.ofGraph(ParticipantId.Kind.RDF, graphUri),
                participant,
                UUID.randomUUID());
    }

    private static ParticipantVersion dlOf(ChangeLogParticipant participant) {
        return ParticipantVersion.of(
                ParticipantId.ofWorkspace(ParticipantId.Kind.DL), participant, UUID.randomUUID());
    }

    private static final class RecordingParticipant implements ChangeLogParticipant {

        private final String name;
        private final List<String> calls;

        private RecordingParticipant(String name, List<String> calls) {
            this.name = name;
            this.calls = calls;
        }

        @Override
        public void undo() {
            calls.add(name + ".undo");
        }

        @Override
        public void redo() {
            calls.add(name + ".redo");
        }

        @Override
        public void discardOldestVersion() {
            calls.add(name + ".discardOldestVersion");
        }

        @Override
        public void discardRedoHistory() {
            calls.add(name + ".discardRedoHistory");
        }

        @Override
        public void foldLastVersionIntoPrevious() {
            // not exercised here
        }
    }
}

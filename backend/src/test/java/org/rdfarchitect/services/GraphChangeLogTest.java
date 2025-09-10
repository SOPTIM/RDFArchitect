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

package org.rdfarchitect.services;

import org.apache.jena.graph.Graph;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.models.changelog.ChangeLogEntry;
import org.rdfarchitect.models.changelog.GraphChangeLog;
import org.rdfarchitect.rdf.graph.DeltaCompressible;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class GraphChangeLogTest {

    private GraphChangeLog changeLog;
    private ChangeLogEntry entry1;
    private ChangeLogEntry entry2;
    private ChangeLogEntry entry3;
    private UUID version1;

    @BeforeEach
    void setUp() {
        changeLog = new GraphChangeLog();

        version1 = UUID.randomUUID();
        UUID version2 = UUID.randomUUID();
        UUID version3 = UUID.randomUUID();

        entry1 = createMockEntry("entry1", version1);
        entry2 = createMockEntry("entry2", version2);
        entry3 = createMockEntry("entry3", version3);
    }

    private ChangeLogEntry createMockEntry(String message, UUID versionId) {
        DeltaCompressible delta = mock(DeltaCompressible.class);
        when(delta.getVersionId()).thenReturn(versionId);
        when(delta.getAdditions()).thenReturn(mock(Graph.class));
        when(delta.getDeletions()).thenReturn(mock(Graph.class));
        return new ChangeLogEntry(message, delta);
    }

    @Test
    void addEntry_addsEntry() {
        changeLog.addEntry(entry1);
        assertThat(changeLog.getEntries()).hasSize(1);
        assertThat(changeLog.getEntries().get(0)).isEqualTo(entry1);
    }

    @Test
    void undoChanges_existingChange_undoesChange() {
        changeLog.addEntry(entry1);
        changeLog.addEntry(entry2);
        changeLog.undoChange();

        assertThat(changeLog.getEntries()).hasSize(1);
        assertThat(changeLog.getEntries().get(0)).isEqualTo(entry1);
    }

    @Test
    void undoChanges_noChanges_doesNothing() {
        changeLog.undoChange();
        assertThat(changeLog.getEntries()).isEmpty();
    }

    @Test
    void redoChange_undoneChange_redoesChange() {
        changeLog.addEntry(entry1);
        changeLog.addEntry(entry2);
        changeLog.undoChange();
        changeLog.redoChange();

        assertThat(changeLog.getEntries()).hasSize(2);
        assertThat(changeLog.getEntries().get(1)).isEqualTo(entry2);
    }

    @Test
    void redoChange_noUndoneChanges_doesNothing() {
        changeLog.addEntry(entry1);
        changeLog.redoChange();

        assertThat(changeLog.getEntries()).hasSize(1);
        assertThat(changeLog.getEntries().get(0)).isEqualTo(entry1);
    }

    @Test
    void restore_validVersion_restoresVersion() {
        changeLog.addEntry(entry1);
        changeLog.addEntry(entry2);
        changeLog.addEntry(entry3);

        changeLog.restore(version1);

        assertThat(changeLog.getEntries()).hasSize(1);
        assertThat(changeLog.getEntries().get(0).getChangeId()).isEqualTo(entry1.getChangeId());
    }

    @Test
    void restore_invalidVersion_doesNothing() {
        changeLog.addEntry(entry1);
        UUID invalidVersion = UUID.randomUUID();

        changeLog.restore(invalidVersion);

        assertThat(changeLog.getEntries()).hasSize(1);
    }
}

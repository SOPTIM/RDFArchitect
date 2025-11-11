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

package org.rdfarchitect.cim.changelog;

import lombok.Getter;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

public class GraphChangeLog {

    @Getter
    private final List<ChangeLogEntry> entries = new ArrayList<>();
    private final Deque<ChangeLogEntry> undoneDeque = new ArrayDeque<>();

    public void addEntry(ChangeLogEntry entry) {
        entries.add(entry);
    }

    public void undoChange() {
        if (!entries.isEmpty()) {
            var undoneEntry = entries.remove(entries.size() - 1);
            undoneDeque.push(undoneEntry);
        }
    }

    public void redoChange() {
        if (!undoneDeque.isEmpty()) {
            var redoneEntry = undoneDeque.pop();
            entries.add(redoneEntry);
        }
    }

    public void restore(UUID versionId) {
        int index = -1;
        for (int i = 0; i < entries.size(); i++) {
            if (versionId.equals(entries.get(i).getChangeId())) {
                index = i;
                break;
            }
        }

        if (index != -1) {
            undoneDeque.clear();
            for (int i = entries.size() - 1; i > index; i--) {
                entries.remove(entries.size() - 1);
            }
        }
    }
}

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

package org.rdfarchitect.api.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class ChangeLogEntryDTO {
    private String changeId;
    private String timestamp;
    private String message;
    private List<ContextDeltaDTO> contextDeltas;

    /** Graphs and diagrams that undoing this change would remove; empty when nothing disappears. */
    private List<String> removedOnUndo;

    /** The graphs this change touched, so the editor can offer to go where it landed. */
    private List<String> affectedGraphUris;

    /**
     * The graphs a restore held to one graph would take this change back in. Narrower than {@link
     * #getAffectedGraphUris()}: creating, deleting and renaming a graph is recorded against the set
     * of graphs, which a restore held to a graph leaves alone, so a question about such a restore
     * must not promise to undo it.
     */
    private List<String> restorableGraphUris;

    /**
     * Whether an undo has stepped over this change, so that it lies ahead of the workspace rather
     * than behind it. A redo brings it back.
     */
    private boolean undone;

    /**
     * The kinds of data this change touched — {@code rdf}, {@code shacl}, {@code dl} and the like —
     * named as {@link ContextDeltaDTO#getContextName()} names them. Answered even for a change that
     * carries no delta, such as deleting a graph, which a filter would otherwise lose.
     */
    private List<String> affectedKinds;
}

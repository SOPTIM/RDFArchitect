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

import org.rdfarchitect.rdf.graph.DeltaCompressible;

import java.util.UUID;

public interface Rewindable {

    /**
     * Undo the last change to this Object. Is its own transaction.
     */
    void undo();

    /**
     * Redo a previously undone change. Is its own transaction.
     */
    void redo();

    /**
     * Check if there is a change to undo. Is its own transaction.
     *
     * @return true if there is a change to undo.
     */
    boolean canUndo();

    /**
     * Check if there is a change to redo. Is its own transaction.
     *
     * @return true if there is a change to redo.
     */
    boolean canRedo();

    /**
     * Restore the state of this object to a specific version.
     */
    void restore(UUID versionId);

    /**
     * Get the last delta that was applied to this object.
     *
     * @return the last delta that was applied to this object.
     */
    DeltaCompressible getLastDelta();
}

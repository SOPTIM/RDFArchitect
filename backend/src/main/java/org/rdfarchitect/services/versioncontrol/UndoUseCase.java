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

package org.rdfarchitect.services.versioncontrol;

import org.rdfarchitect.api.dto.HistoryStepDTO;

import java.util.UUID;

public interface UndoUseCase {

    /**
     * Rolls back the most recent change anywhere in the workspace, across every graph it touched.
     *
     * @param workspaceName the workspace to operate on
     * @return the change that was undone
     */
    HistoryStepDTO undo(String workspaceName);

    /**
     * Rolls back the most recent change, provided it is still the one the undo preview showed.
     *
     * @param workspaceName the workspace to operate on
     * @param changeId the change the undo preview showed
     * @return the change that was undone
     * @throws org.rdfarchitect.exception.graph.UndoTargetChangedException if the most recent change
     *     is a different one; nothing is undone then
     */
    HistoryStepDTO undoIfLatest(String workspaceName, UUID changeId);
}

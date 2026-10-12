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

import org.rdfarchitect.api.dto.ChangeLogEntryDTO;

import java.util.List;

public interface ChangeLogUseCase {

    /**
     * Lists the recorded changes of a workspace, newest first.
     *
     * @param workspaceName the workspace to read
     * @return the change history
     */
    List<ChangeLogEntryDTO> listChanges(String workspaceName);

    /**
     * Returns the change the next undo would take back, so that the editor can ask before an undo
     * that makes something disappear.
     *
     * @param workspaceName the workspace to inspect
     * @return the pending change, or {@code null} if there is nothing to undo
     */
    ChangeLogEntryDTO pendingUndo(String workspaceName);
}

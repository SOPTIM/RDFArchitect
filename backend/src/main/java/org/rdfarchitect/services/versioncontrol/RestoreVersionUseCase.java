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
import org.rdfarchitect.models.changelog.RevertScope;

import java.util.UUID;

public interface RestoreVersionUseCase {

    /**
     * Puts the workspace back the way a specific version left it, recording that as a new change.
     *
     * @param workspaceName the workspace to operate on
     * @param versionId the change to restore to
     * @param scope how much of the workspace to put back
     * @return what was restored and what is left to undo or redo; the change is {@code null} if
     *     nothing within the scope had changed since
     */
    HistoryStepDTO restoreVersion(String workspaceName, UUID versionId, RevertScope scope);
}

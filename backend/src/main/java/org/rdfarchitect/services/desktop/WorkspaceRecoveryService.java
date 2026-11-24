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

package org.rdfarchitect.services.desktop;

import org.rdfarchitect.api.dto.desktop.RecoveredWorkspaceDTO;
import org.rdfarchitect.desktop.WorkspaceRecovery;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * The unsaved workspaces the desktop app kept when it last stopped, see {@link WorkspaceRecovery}.
 */
@Service
@Profile("desktop")
public class WorkspaceRecoveryService
        implements ListRecoveredWorkspacesUseCase,
                GetRecoveredWorkspaceUseCase,
                DiscardRecoveredWorkspaceUseCase,
                MarkRestoredFromCopyUseCase {

    private final WorkspaceRecovery workspaceRecovery;

    public WorkspaceRecoveryService(WorkspaceRecovery workspaceRecovery) {
        this.workspaceRecovery = workspaceRecovery;
    }

    @Override
    public List<RecoveredWorkspaceDTO> listRecoveredWorkspaces() {
        return workspaceRecovery.previousRun().stream()
                .map(
                        kept ->
                                new RecoveredWorkspaceDTO(
                                        kept.id(), kept.workspaceName(), kept.savedAt()))
                .toList();
    }

    @Override
    public Optional<byte[]> getRecoveredWorkspace(String id) {
        return workspaceRecovery.read(id);
    }

    @Override
    public boolean discardRecoveredWorkspace(String id) {
        return workspaceRecovery.discard(id);
    }

    @Override
    public Result markRestoredFromCopy(String workspaceName, String id) {
        return switch (workspaceRecovery.removeRestored(id, workspaceName)) {
            case REMOVED -> Result.REMOVED;
            case UNKNOWN -> Result.UNKNOWN_COPY;
            case STILL_NEEDED -> Result.STILL_NEEDED;
        };
    }
}

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

import org.rdfarchitect.api.dto.desktop.DesktopInfoDTO;
import org.rdfarchitect.api.dto.desktop.DesktopStatusDTO;
import org.rdfarchitect.config.AppVersionResolver;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.desktop.ClientPresence;
import org.rdfarchitect.desktop.SavedWorkspaces;
import org.rdfarchitect.desktop.WorkspaceFileLocations;
import org.rdfarchitect.desktop.WorkspaceRecovery;
import org.rdfarchitect.exception.database.ResourceNotFoundException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The windows of the desktop app: which are open, and what each needs to know about its session.
 */
@Service
@Profile("desktop")
public class DesktopWindowService
        implements GetDesktopInfoUseCase, ReportWindowOpenUseCase, ReportWindowClosedUseCase {

    /** Tells a window whether the backend it talks to is still the one it started with. */
    private final String instanceId = UUID.randomUUID().toString();

    private final ClientPresence clientPresence;
    private final WorkspaceRecovery workspaceRecovery;
    private final SavedWorkspaces savedWorkspaces;
    private final WorkspaceFileLocations fileLocations;
    private final DatabasePort databasePort;
    private final String version;

    public DesktopWindowService(
            ClientPresence clientPresence,
            WorkspaceRecovery workspaceRecovery,
            SavedWorkspaces savedWorkspaces,
            WorkspaceFileLocations fileLocations,
            DatabasePort databasePort,
            AppVersionResolver versionResolver) {
        this.clientPresence = clientPresence;
        this.workspaceRecovery = workspaceRecovery;
        this.savedWorkspaces = savedWorkspaces;
        this.fileLocations = fileLocations;
        this.databasePort = databasePort;
        this.version = versionResolver.resolveVersion();
    }

    @Override
    public DesktopInfoDTO getDesktopInfo() {
        return new DesktopInfoDTO(DesktopInfoDTO.APPLICATION, version);
    }

    @Override
    public DesktopStatusDTO reportWindowOpen(String windowId) {
        clientPresence.heartbeat(windowId);
        workspaceRecovery.noteSession();
        return new DesktopStatusDTO(
                instanceId,
                savedWorkspaces.unsavedWorkspaces(),
                fileLocations.workspacesWithFile(),
                workspaceIds());
    }

    @Override
    public void reportWindowClosed(String windowId) {
        clientPresence.goodbye(windowId);
    }

    private Map<String, String> workspaceIds() {
        var ids = new LinkedHashMap<String, String>();
        for (var workspaceName : databasePort.listDatasets().stream().sorted().toList()) {
            try {
                ids.put(workspaceName, databasePort.workspaceId(workspaceName).toString());
            } catch (ResourceNotFoundException _) {
                // Deleted since it was listed.
            }
        }
        return ids;
    }
}

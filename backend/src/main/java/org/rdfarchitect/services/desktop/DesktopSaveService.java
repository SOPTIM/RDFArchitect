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

import org.rdfarchitect.desktop.DesktopWorkspaceFiles;
import org.rdfarchitect.desktop.OpenRequests;
import org.rdfarchitect.desktop.SavedWorkspaces;
import org.rdfarchitect.desktop.WorkspaceFileLocations;
import org.rdfarchitect.desktop.WorkspaceRevision;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Saving workspaces to files in the desktop app, and recording which version each file holds.
 *
 * <p>The browser writes a file of the user's choosing itself and reports it ({@link
 * #markWorkspaceSaved}); a file the operating system handed over is written here ({@link
 * #saveWorkspaceToFile}).
 */
@Service
@Profile("desktop")
public class DesktopSaveService
        implements GetWorkspaceFileUseCase,
                SaveWorkspaceToFileUseCase,
                MarkWorkspaceSavedUseCase,
                MarkOpenedFromRequestUseCase {

    private static final Logger logger = LoggerFactory.getLogger(DesktopSaveService.class);

    private final DesktopWorkspaceFiles workspaceFiles;
    private final SavedWorkspaces savedWorkspaces;
    private final WorkspaceFileLocations fileLocations;
    private final OpenRequests openRequests;

    /**
     * Held while a save to a file and what it records happen, so that two saves, from two windows,
     * record the version that the file ends up holding.
     */
    private final Object saveLock = new Object();

    public DesktopSaveService(
            DesktopWorkspaceFiles workspaceFiles,
            SavedWorkspaces savedWorkspaces,
            WorkspaceFileLocations fileLocations,
            OpenRequests openRequests) {
        this.workspaceFiles = workspaceFiles;
        this.savedWorkspaces = savedWorkspaces;
        this.fileLocations = fileLocations;
        this.openRequests = openRequests;
    }

    @Override
    public WorkspaceFileDownload getWorkspaceFile(String workspaceName) {
        var file = workspaceFiles.write(workspaceName);
        return new WorkspaceFileDownload(
                file.content(),
                file.fileName(),
                file.revision() != null ? file.revision().version() : null,
                file.workspaceId());
    }

    @Override
    public boolean saveWorkspaceToFile(String workspaceName) {
        var path = fileLocations.locationOf(workspaceName);
        if (path.isEmpty()) {
            return false;
        }
        synchronized (saveLock) {
            try {
                var saved = workspaceFiles.writeTo(workspaceName, path.get());
                if (saved != null) {
                    savedWorkspaces.markSaved(workspaceName, saved);
                }
            } catch (IOException e) {
                throw new UncheckedIOException("Could not write " + path.get(), e);
            }
        }
        logger.info("Saved workspace \"{}\" to {}.", workspaceName, path.get());
        return true;
    }

    @Override
    public boolean markWorkspaceSaved(String workspaceName, String version) {
        var revision = version != null ? WorkspaceRevision.ofVersion(version) : null;
        synchronized (saveLock) {
            fileLocations.forget(workspaceName);
            if (revision != null) {
                savedWorkspaces.markSaved(workspaceName, revision);
                return true;
            }
            return savedWorkspaces.markSavedNow(workspaceName);
        }
    }

    @Override
    public Result markOpenedFromRequest(String workspaceName, String requestId) {
        var request = openRequests.get(requestId);
        if (request.isEmpty()) {
            return Result.UNKNOWN_REQUEST;
        }
        synchronized (saveLock) {
            if (!savedWorkspaces.markSavedNow(workspaceName)) {
                return Result.BUSY;
            }
            fileLocations.remember(workspaceName, request.get().path());
        }
        openRequests.remove(requestId);
        return Result.RECORDED;
    }
}

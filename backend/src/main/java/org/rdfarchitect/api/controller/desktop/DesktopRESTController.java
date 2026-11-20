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

package org.rdfarchitect.api.controller.desktop;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;

import org.rdfarchitect.api.dto.desktop.DesktopInfoDTO;
import org.rdfarchitect.api.dto.desktop.DesktopStatusDTO;
import org.rdfarchitect.api.dto.desktop.OpenRequestDTO;
import org.rdfarchitect.api.dto.desktop.SavedVersionDTO;
import org.rdfarchitect.config.AppVersionResolver;
import org.rdfarchitect.desktop.ClientPresence;
import org.rdfarchitect.desktop.DesktopWorkspaceFiles;
import org.rdfarchitect.desktop.OpenRequests;
import org.rdfarchitect.desktop.SavedWorkspaces;
import org.rdfarchitect.desktop.WorkspaceFileLocations;
import org.rdfarchitect.desktop.WorkspaceRecovery;
import org.rdfarchitect.desktop.WorkspaceRecovery.RecoveredWorkspace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * The part of the API only the desktop app has: windows reporting that they are open, and saving
 * workspaces to files. Not registered outside the {@code desktop} profile.
 */
@RestController
@RequestMapping("api/desktop")
@Profile("desktop")
public class DesktopRESTController {

    /** Names the version of the workspace a downloaded workspace file holds. */
    public static final String WORKSPACE_VERSION_HEADER = "X-Workspace-Version";

    private static final Logger logger = LoggerFactory.getLogger(DesktopRESTController.class);

    private final ClientPresence clientPresence;
    private final SavedWorkspaces savedWorkspaces;
    private final DesktopWorkspaceFiles workspaceFiles;
    private final WorkspaceRecovery workspaceRecovery;
    private final OpenRequests openRequests;
    private final WorkspaceFileLocations fileLocations;
    private final String version;

    public DesktopRESTController(
            ClientPresence clientPresence,
            SavedWorkspaces savedWorkspaces,
            DesktopWorkspaceFiles workspaceFiles,
            WorkspaceRecovery workspaceRecovery,
            OpenRequests openRequests,
            WorkspaceFileLocations fileLocations,
            AppVersionResolver versionResolver) {
        this.clientPresence = clientPresence;
        this.savedWorkspaces = savedWorkspaces;
        this.workspaceFiles = workspaceFiles;
        this.workspaceRecovery = workspaceRecovery;
        this.openRequests = openRequests;
        this.fileLocations = fileLocations;
        this.version = versionResolver.resolveVersion();
    }

    @Operation(
            summary = "Desktop app",
            description = "Identifies the running desktop app.",
            tags = {"desktop"})
    @GetMapping
    public DesktopInfoDTO info() {
        return new DesktopInfoDTO(DesktopInfoDTO.APPLICATION, version);
    }

    @Operation(
            summary = "Window heartbeat",
            description =
                    "Tells the desktop app that a window is still open, and answers which workspaces"
                            + " of the session hold unsaved changes. The app shuts down once no"
                            + " window has sent one for a while.",
            tags = {"desktop"})
    @PostMapping("/windows/{windowId}/heartbeat")
    public DesktopStatusDTO heartbeat(
            @Parameter(description = "An id the window chose for itself.") @PathVariable
                    String windowId) {
        clientPresence.heartbeat(windowId);
        workspaceRecovery.noteSession();
        return new DesktopStatusDTO(
                savedWorkspaces.unsavedWorkspaces(), fileLocations.workspacesWithFile());
    }

    @Operation(
            summary = "Window closed",
            description = "Tells the desktop app that a window has closed or is reloading.",
            tags = {"desktop"})
    @PostMapping("/windows/{windowId}/goodbye")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void goodbye(@PathVariable String windowId) {
        clientPresence.goodbye(windowId);
    }

    @Operation(
            summary = "Workspace file",
            description =
                    "The workspace as a workspace file, as File → Export → Workspace writes it. The"
                            + " X-Workspace-Version header names the version it holds, to be passed"
                            + " to PUT .../saved once the file is written.",
            tags = {"desktop"})
    @GetMapping("/workspaces/{workspaceName}/file")
    public ResponseEntity<byte[]> workspaceFile(@PathVariable String workspaceName) {
        logger.info("Writing workspace \"{}\" to a file.", workspaceName);
        var file = workspaceFiles.write(workspaceName);
        var headers = new HttpHeaders();
        headers.setContentDisposition(
                ContentDisposition.attachment()
                        .filename(file.fileName(), StandardCharsets.UTF_8)
                        .build());
        if (file.version() != null) {
            headers.set(WORKSPACE_VERSION_HEADER, file.version().toString());
        }
        headers.setAccessControlExposeHeaders(
                List.of(HttpHeaders.CONTENT_DISPOSITION, WORKSPACE_VERSION_HEADER));
        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(file.content());
    }

    @Operation(
            summary = "Open a workspace file",
            description =
                    "Registers a workspace file on this computer to be opened, as the launcher does"
                            + " for a double-clicked .rdfa file. A window opened at"
                            + " /mainpage?open=<id> then opens it.",
            tags = {"desktop"})
    @PostMapping("/open-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public OpenRequestDTO requestOpen(@RequestBody OpenRequestDTO request) {
        if (request.getPath() == null || request.getPath().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No file to open.");
        }
        try {
            var registered = openRequests.add(Path.of(request.getPath()));
            return new OpenRequestDTO(
                    registered.id(), registered.fileName(), registered.path().toString());
        } catch (IllegalArgumentException e) {
            // InvalidPathException is one as well.
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @Operation(
            summary = "Workspace file to open",
            description = "The content of a workspace file registered to be opened.",
            tags = {"desktop"})
    @GetMapping("/open-requests/{id}")
    public ResponseEntity<byte[]> openRequestContent(@PathVariable String id) {
        var request =
                openRequests
                        .get(id)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        byte[] content;
        try {
            content = Files.readAllBytes(request.path());
        } catch (IOException e) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Cannot read " + request.fileName());
        }
        var headers = new HttpHeaders();
        headers.setContentDisposition(
                ContentDisposition.attachment()
                        .filename(request.fileName(), StandardCharsets.UTF_8)
                        .build());
        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(content);
    }

    @Operation(
            summary = "Workspace opened from a registered file",
            description =
                    "Records that the workspace was opened from the file of an open request: it is"
                            + " saved, and Save writes to that file.",
            tags = {"desktop"})
    @PutMapping("/workspaces/{workspaceName}/opened-from/{requestId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void openedFrom(@PathVariable String workspaceName, @PathVariable String requestId) {
        var request =
                openRequests
                        .remove(requestId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!savedWorkspaces.markSavedNow(workspaceName)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "The workspace is being changed right now.");
        }
        fileLocations.remember(
                workspaceName,
                request.path(),
                savedWorkspaces.savedVersion(workspaceName).orElseThrow());
    }

    @Operation(
            summary = "Save a workspace to its file",
            description =
                    "Writes the workspace to the file it was opened from on this computer and records"
                            + " it as saved. 409 if it has no such file.",
            tags = {"desktop"})
    @PostMapping("/workspaces/{workspaceName}/file")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void saveToFile(@PathVariable String workspaceName) {
        var path =
                fileLocations
                        .locationOf(workspaceName)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.CONFLICT,
                                                "The workspace has no file to save to."));
        try {
            var saved = workspaceFiles.writeTo(workspaceName, path);
            if (saved != null) {
                savedWorkspaces.markSaved(workspaceName, saved);
                fileLocations.remember(workspaceName, path, saved);
            }
            logger.info("Saved workspace \"{}\" to {}.", workspaceName, path);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write " + path, e);
        }
    }

    @Operation(
            summary = "Workspaces kept from the previous run",
            description =
                    "The unsaved workspaces the desktop app kept when it last stopped, newest first.",
            tags = {"desktop"})
    @GetMapping("/recovery")
    public List<RecoveredWorkspace> recoveredWorkspaces() {
        return workspaceRecovery.previousRun();
    }

    @Operation(
            summary = "Workspace kept from the previous run",
            description = "The workspace file of a workspace kept from the previous run.",
            tags = {"desktop"})
    @GetMapping("/recovery/{id}")
    public ResponseEntity<byte[]> recoveredWorkspace(@PathVariable String id) {
        return workspaceRecovery
                .read(id)
                .map(
                        content ->
                                ResponseEntity.ok()
                                        .contentType(MediaType.parseMediaType("application/zip"))
                                        .body(content))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @Operation(
            summary = "Discard a workspace kept from the previous run",
            description = "Removes the copy, once it is restored or not wanted.",
            tags = {"desktop"})
    @DeleteMapping("/recovery/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void discardRecoveredWorkspace(@PathVariable String id) {
        if (!workspaceRecovery.discard(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    @Operation(
            summary = "Workspace saved",
            description =
                    "Records that the workspace was saved to a file, at the version given or, without"
                            + " one, as it stands now.",
            tags = {"desktop"})
    @PutMapping("/workspaces/{workspaceName}/saved")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void saved(
            @PathVariable String workspaceName,
            @RequestBody(required = false) SavedVersionDTO savedVersion) {
        // The browser wrote or read a file of its own choosing, which is the workspace's file now.
        fileLocations.forget(workspaceName);
        if (savedVersion != null && savedVersion.getVersion() != null) {
            savedWorkspaces.markSaved(workspaceName, savedVersion.getVersion());
            return;
        }
        if (!savedWorkspaces.markSavedNow(workspaceName)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "The workspace is being changed right now.");
        }
    }
}

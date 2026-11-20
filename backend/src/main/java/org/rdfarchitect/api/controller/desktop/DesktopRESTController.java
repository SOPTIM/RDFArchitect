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
import org.rdfarchitect.api.dto.desktop.SavedVersionDTO;
import org.rdfarchitect.config.AppVersionResolver;
import org.rdfarchitect.desktop.ClientPresence;
import org.rdfarchitect.desktop.DesktopWorkspaceFiles;
import org.rdfarchitect.desktop.SavedWorkspaces;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
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
    private final String version;

    public DesktopRESTController(
            ClientPresence clientPresence,
            SavedWorkspaces savedWorkspaces,
            DesktopWorkspaceFiles workspaceFiles,
            AppVersionResolver versionResolver) {
        this.clientPresence = clientPresence;
        this.savedWorkspaces = savedWorkspaces;
        this.workspaceFiles = workspaceFiles;
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
        return new DesktopStatusDTO(savedWorkspaces.unsavedWorkspaces());
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

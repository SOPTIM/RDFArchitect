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
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import lombok.RequiredArgsConstructor;

import org.rdfarchitect.api.dto.desktop.DesktopInfoDTO;
import org.rdfarchitect.api.dto.desktop.DesktopStatusDTO;
import org.rdfarchitect.api.dto.desktop.OpenRequestDTO;
import org.rdfarchitect.api.dto.desktop.RecoveredWorkspaceDTO;
import org.rdfarchitect.api.dto.desktop.SavedVersionDTO;
import org.rdfarchitect.services.desktop.DiscardRecoveredWorkspaceUseCase;
import org.rdfarchitect.services.desktop.GetDesktopInfoUseCase;
import org.rdfarchitect.services.desktop.GetOpenRequestFileUseCase;
import org.rdfarchitect.services.desktop.GetRecoveredWorkspaceUseCase;
import org.rdfarchitect.services.desktop.GetWorkspaceFileUseCase;
import org.rdfarchitect.services.desktop.ListRecoveredWorkspacesUseCase;
import org.rdfarchitect.services.desktop.MarkOpenedFromRequestUseCase;
import org.rdfarchitect.services.desktop.MarkWorkspaceSavedUseCase;
import org.rdfarchitect.services.desktop.ReportWindowClosedUseCase;
import org.rdfarchitect.services.desktop.ReportWindowOpenUseCase;
import org.rdfarchitect.services.desktop.RequestOpenFileUseCase;
import org.rdfarchitect.services.desktop.SaveWorkspaceToFileUseCase;
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

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * The part of the API only the desktop app has: windows reporting that they are open, saving
 * workspaces to files, opening files the operating system hands over, and restoring workspaces kept
 * from the previous run. Not registered outside the {@code desktop} profile.
 */
@RestController
@RequestMapping("api/desktop")
@Profile("desktop")
@RequiredArgsConstructor
public class DesktopRESTController {

    /** Names the version of the workspace a downloaded workspace file holds. */
    public static final String WORKSPACE_VERSION_HEADER = "X-Workspace-Version";

    /** Names the workspace a downloaded workspace file holds, by an id that survives renames. */
    public static final String WORKSPACE_ID_HEADER = "X-Workspace-Id";

    private static final String TAG = "desktop";
    private static final MediaType WORKSPACE_FILE = MediaType.parseMediaType("application/zip");

    private static final Logger logger = LoggerFactory.getLogger(DesktopRESTController.class);

    private final GetDesktopInfoUseCase getDesktopInfoUseCase;
    private final ReportWindowOpenUseCase reportWindowOpenUseCase;
    private final ReportWindowClosedUseCase reportWindowClosedUseCase;
    private final GetWorkspaceFileUseCase getWorkspaceFileUseCase;
    private final SaveWorkspaceToFileUseCase saveWorkspaceToFileUseCase;
    private final MarkWorkspaceSavedUseCase markWorkspaceSavedUseCase;
    private final RequestOpenFileUseCase requestOpenFileUseCase;
    private final GetOpenRequestFileUseCase getOpenRequestFileUseCase;
    private final MarkOpenedFromRequestUseCase markOpenedFromRequestUseCase;
    private final ListRecoveredWorkspacesUseCase listRecoveredWorkspacesUseCase;
    private final GetRecoveredWorkspaceUseCase getRecoveredWorkspaceUseCase;
    private final DiscardRecoveredWorkspaceUseCase discardRecoveredWorkspaceUseCase;

    @Operation(
            summary = "Desktop app",
            description = "Identifies the running desktop app. Only in the desktop app.",
            tags = {TAG})
    @GetMapping
    public DesktopInfoDTO getDesktopInfo() {
        return getDesktopInfoUseCase.getDesktopInfo();
    }

    @Operation(
            summary = "Window heartbeat",
            description =
                    "Tells the desktop app that a window is still open, and answers which workspaces"
                            + " of the session hold unsaved changes. The app shuts down once no"
                            + " window has sent one for a while. Only in the desktop app.",
            tags = {TAG})
    @PostMapping("/windows/{windowId}/heartbeat")
    public DesktopStatusDTO reportWindowOpen(
            @Parameter(description = "An id the window chose for itself.") @PathVariable
                    String windowId) {
        logger.debug("Received a heartbeat from window {}.", windowId);
        return reportWindowOpenUseCase.reportWindowOpen(windowId);
    }

    @Operation(
            summary = "Window closed",
            description =
                    "Tells the desktop app that a window has closed or is reloading. Only in the"
                            + " desktop app.",
            tags = {TAG})
    @PostMapping("/windows/{windowId}/goodbye")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reportWindowClosed(@PathVariable String windowId) {
        logger.debug("Window {} closed.", windowId);
        reportWindowClosedUseCase.reportWindowClosed(windowId);
    }

    @Operation(
            summary = "Workspace file",
            description =
                    "The workspace as a workspace file, as File → Export → Workspace writes it. The"
                            + " X-Workspace-Version header names the version it holds, to be passed"
                            + " to PUT .../saved once the file is written, and X-Workspace-Id the"
                            + " workspace. Only in the desktop app.",
            tags = {TAG},
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        content = @Content(mediaType = "application/zip"))
            })
    @GetMapping("/workspaces/{workspaceName}/file")
    public ResponseEntity<byte[]> getWorkspaceFile(@PathVariable String workspaceName) {
        logger.info("Received a request for the workspace file of \"{}\".", workspaceName);
        var file = getWorkspaceFileUseCase.getWorkspaceFile(workspaceName);
        var headers = new HttpHeaders();
        headers.setContentDisposition(
                ContentDisposition.attachment()
                        .filename(file.fileName(), StandardCharsets.UTF_8)
                        .build());
        if (file.version() != null) {
            headers.set(WORKSPACE_VERSION_HEADER, file.version());
        }
        headers.set(WORKSPACE_ID_HEADER, file.workspaceId().toString());
        headers.setAccessControlExposeHeaders(
                List.of(
                        HttpHeaders.CONTENT_DISPOSITION,
                        WORKSPACE_VERSION_HEADER,
                        WORKSPACE_ID_HEADER));
        logger.info("Sending the workspace file of \"{}\".", workspaceName);
        return ResponseEntity.ok()
                .headers(headers)
                .contentType(WORKSPACE_FILE)
                .body(file.content());
    }

    @Operation(
            summary = "Save a workspace to its file",
            description =
                    "Writes the workspace to the file it was opened from on this computer and records"
                            + " it as saved. 409 if it has no such file. Only in the desktop app.",
            tags = {TAG},
            responses = {
                @ApiResponse(responseCode = "204", description = "Saved"),
                @ApiResponse(responseCode = "409", description = "The workspace has no file")
            })
    @PostMapping("/workspaces/{workspaceName}/file")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void saveWorkspaceToFile(@PathVariable String workspaceName) {
        logger.info("Received a request to save \"{}\" to its file.", workspaceName);
        if (!saveWorkspaceToFileUseCase.saveWorkspaceToFile(workspaceName)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "The workspace has no file to save to.");
        }
    }

    @Operation(
            summary = "Workspace saved",
            description =
                    "Records that the workspace was saved to a file, at the version given or, without"
                            + " one, as it stands now. 409 while it is being changed and no version"
                            + " is given. Only in the desktop app.",
            tags = {TAG},
            responses = {
                @ApiResponse(responseCode = "204", description = "Recorded"),
                @ApiResponse(responseCode = "400", description = "Not a version"),
                @ApiResponse(
                        responseCode = "409",
                        description = "The workspace is being changed right now")
            })
    @PutMapping("/workspaces/{workspaceName}/saved")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markWorkspaceSaved(
            @PathVariable String workspaceName,
            @RequestBody(required = false) SavedVersionDTO savedVersion) {
        logger.info("Received a report that \"{}\" was saved.", workspaceName);
        var version = savedVersion != null ? savedVersion.getVersion() : null;
        boolean recorded;
        try {
            recorded = markWorkspaceSavedUseCase.markWorkspaceSaved(workspaceName, version);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
        if (!recorded) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "The workspace is being changed right now.");
        }
    }

    @Operation(
            summary = "Open a workspace file",
            description =
                    "Registers a workspace file on this computer to be opened, as the launcher does"
                            + " for a double-clicked .rdfa file. A window opened at"
                            + " /mainpage?open=<id> then opens it. Only in the desktop app.",
            tags = {TAG},
            responses = {
                @ApiResponse(responseCode = "201", description = "Registered"),
                @ApiResponse(responseCode = "400", description = "No readable workspace file")
            })
    @PostMapping("/open-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public OpenRequestDTO requestOpenFile(@RequestBody OpenRequestDTO request) {
        logger.info("Received a request to open a workspace file.");
        if (request.getPath() == null || request.getPath().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No file to open.");
        }
        try {
            return requestOpenFileUseCase.requestOpenFile(request.getPath());
        } catch (IllegalArgumentException e) {
            // InvalidPathException is one as well.
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @Operation(
            summary = "Workspace file to open",
            description =
                    "The content of a workspace file registered to be opened. Only in the desktop"
                            + " app.",
            tags = {TAG},
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        content = @Content(mediaType = "application/zip")),
                @ApiResponse(responseCode = "404", description = "No such request or file")
            })
    @GetMapping("/open-requests/{requestId}")
    public ResponseEntity<byte[]> getOpenRequestFile(@PathVariable String requestId) {
        var file =
                getOpenRequestFileUseCase
                        .getOpenRequestFile(requestId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var headers = new HttpHeaders();
        headers.setContentDisposition(
                ContentDisposition.attachment()
                        .filename(file.fileName(), StandardCharsets.UTF_8)
                        .build());
        return ResponseEntity.ok()
                .headers(headers)
                .contentType(WORKSPACE_FILE)
                .body(file.content());
    }

    @Operation(
            summary = "Workspace opened from a registered file",
            description =
                    "Records that the workspace was opened from the file of an open request: it is"
                            + " saved, and Save writes to that file. The request is removed once"
                            + " this is recorded. 409 while the workspace is being changed; the"
                            + " request stays for another try. Only in the desktop app.",
            tags = {TAG},
            responses = {
                @ApiResponse(responseCode = "204", description = "Recorded"),
                @ApiResponse(responseCode = "404", description = "No such request"),
                @ApiResponse(
                        responseCode = "409",
                        description = "The workspace is being changed right now")
            })
    @PutMapping("/workspaces/{workspaceName}/opened-from/{requestId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markOpenedFromRequest(
            @PathVariable String workspaceName, @PathVariable String requestId) {
        logger.info("Received a report that \"{}\" was opened from a file.", workspaceName);
        switch (markOpenedFromRequestUseCase.markOpenedFromRequest(workspaceName, requestId)) {
            case RECORDED -> {
                // Nothing more to answer.
            }
            case UNKNOWN_REQUEST -> throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            case BUSY ->
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT, "The workspace is being changed right now.");
        }
    }

    @Operation(
            summary = "Workspaces kept from the previous run",
            description =
                    "The unsaved workspaces the desktop app kept when it last stopped, newest first."
                            + " Only in the desktop app.",
            tags = {TAG})
    @GetMapping("/recovery")
    public List<RecoveredWorkspaceDTO> listRecoveredWorkspaces() {
        return listRecoveredWorkspacesUseCase.listRecoveredWorkspaces();
    }

    @Operation(
            summary = "Workspace kept from the previous run",
            description =
                    "The workspace file of a workspace kept from the previous run. Only in the"
                            + " desktop app.",
            tags = {TAG},
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        content = @Content(mediaType = "application/zip")),
                @ApiResponse(responseCode = "404", description = "No such copy")
            })
    @GetMapping("/recovery/{id}")
    public ResponseEntity<byte[]> getRecoveredWorkspace(@PathVariable String id) {
        return getRecoveredWorkspaceUseCase
                .getRecoveredWorkspace(id)
                .map(content -> ResponseEntity.ok().contentType(WORKSPACE_FILE).body(content))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @Operation(
            summary = "Discard a workspace kept from the previous run",
            description =
                    "Removes the copy, once it is restored or not wanted. Only in the desktop app.",
            tags = {TAG},
            responses = {
                @ApiResponse(responseCode = "204", description = "Removed"),
                @ApiResponse(responseCode = "404", description = "No such copy")
            })
    @DeleteMapping("/recovery/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void discardRecoveredWorkspace(@PathVariable String id) {
        logger.info("Received a request to discard the kept workspace {}.", id);
        if (!discardRecoveredWorkspaceUseCase.discardRecoveredWorkspace(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }
}

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

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.api.dto.desktop.DesktopStatusDTO;
import org.rdfarchitect.api.dto.desktop.OpenRequestDTO;
import org.rdfarchitect.api.dto.desktop.RecoveredWorkspaceDTO;
import org.rdfarchitect.services.desktop.DiscardRecoveredWorkspaceUseCase;
import org.rdfarchitect.services.desktop.GetDesktopInfoUseCase;
import org.rdfarchitect.services.desktop.GetOpenRequestFileUseCase;
import org.rdfarchitect.services.desktop.GetRecoveredWorkspaceUseCase;
import org.rdfarchitect.services.desktop.GetWorkspaceFileUseCase;
import org.rdfarchitect.services.desktop.ListRecoveredWorkspacesUseCase;
import org.rdfarchitect.services.desktop.MarkOpenedFromRequestUseCase;
import org.rdfarchitect.services.desktop.MarkOpenedFromRequestUseCase.Result;
import org.rdfarchitect.services.desktop.MarkRestoredFromCopyUseCase;
import org.rdfarchitect.services.desktop.MarkWorkspaceSavedUseCase;
import org.rdfarchitect.services.desktop.OpenRequestFile;
import org.rdfarchitect.services.desktop.ReportWindowClosedUseCase;
import org.rdfarchitect.services.desktop.ReportWindowOpenUseCase;
import org.rdfarchitect.services.desktop.RequestOpenFileUseCase;
import org.rdfarchitect.services.desktop.SaveWorkspaceToFileUseCase;
import org.rdfarchitect.services.desktop.WorkspaceFileDownload;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

class DesktopRESTControllerTest {

    private final ReportWindowOpenUseCase reportWindowOpen = mock(ReportWindowOpenUseCase.class);
    private final GetWorkspaceFileUseCase getWorkspaceFile = mock(GetWorkspaceFileUseCase.class);
    private final SaveWorkspaceToFileUseCase saveWorkspaceToFile =
            mock(SaveWorkspaceToFileUseCase.class);
    private final MarkWorkspaceSavedUseCase markWorkspaceSaved =
            mock(MarkWorkspaceSavedUseCase.class);
    private final RequestOpenFileUseCase requestOpenFile = mock(RequestOpenFileUseCase.class);
    private final GetOpenRequestFileUseCase getOpenRequestFile =
            mock(GetOpenRequestFileUseCase.class);
    private final MarkOpenedFromRequestUseCase markOpenedFromRequest =
            mock(MarkOpenedFromRequestUseCase.class);
    private final ListRecoveredWorkspacesUseCase listRecovered =
            mock(ListRecoveredWorkspacesUseCase.class);
    private final GetRecoveredWorkspaceUseCase getRecovered =
            mock(GetRecoveredWorkspaceUseCase.class);
    private final DiscardRecoveredWorkspaceUseCase discardRecovered =
            mock(DiscardRecoveredWorkspaceUseCase.class);
    private final MarkRestoredFromCopyUseCase markRestoredFromCopy =
            mock(MarkRestoredFromCopyUseCase.class);

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc =
                MockMvcBuilders.standaloneSetup(
                                new DesktopRESTController(
                                        mock(GetDesktopInfoUseCase.class),
                                        reportWindowOpen,
                                        mock(ReportWindowClosedUseCase.class),
                                        getWorkspaceFile,
                                        saveWorkspaceToFile,
                                        markWorkspaceSaved,
                                        requestOpenFile,
                                        getOpenRequestFile,
                                        markOpenedFromRequest,
                                        listRecovered,
                                        getRecovered,
                                        discardRecovered,
                                        markRestoredFromCopy))
                        .build();
    }

    @Test
    void reportWindowOpen_session_answersItsStatus() throws Exception {
        when(reportWindowOpen.reportWindowOpen("w"))
                .thenReturn(
                        new DesktopStatusDTO(
                                "run", List.of("Draft"), List.of(), Map.of("Draft", "id-1")));

        mockMvc.perform(post("/api/desktop/windows/w/heartbeat"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instanceId").value("run"))
                .andExpect(jsonPath("$.unsavedWorkspaces[0]").value("Draft"))
                .andExpect(jsonPath("$.workspaceIds.Draft").value("id-1"));
    }

    @Test
    void getWorkspaceFile_workspace_namesItsVersionAndId() throws Exception {
        var id = UUID.randomUUID();
        when(getWorkspaceFile.getWorkspaceFile("Draft One"))
                .thenReturn(
                        new WorkspaceFileDownload(
                                new byte[] {'P', 'K'}, "Draft One.rdfa", "v:editable", id));

        mockMvc.perform(get("/api/desktop/workspaces/Draft One/file"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/zip"))
                .andExpect(
                        header().string(
                                        DesktopRESTController.WORKSPACE_VERSION_HEADER,
                                        "v:editable"))
                .andExpect(
                        header().string(DesktopRESTController.WORKSPACE_ID_HEADER, id.toString()))
                .andExpect(
                        header().string("Content-Disposition", containsString("Draft One.rdfa")));
    }

    @Test
    void saveWorkspaceToFile_noFile_answersConflict() throws Exception {
        when(saveWorkspaceToFile.saveWorkspaceToFile("Draft")).thenReturn(false);

        mockMvc.perform(post("/api/desktop/workspaces/Draft/file"))
                .andExpect(status().isConflict());
    }

    @Test
    void saveWorkspaceToFile_file_answersNoContent() throws Exception {
        when(saveWorkspaceToFile.saveWorkspaceToFile("Draft")).thenReturn(true);

        mockMvc.perform(post("/api/desktop/workspaces/Draft/file"))
                .andExpect(status().isNoContent());
    }

    @Test
    void markWorkspaceSaved_version_passesItOn() throws Exception {
        when(markWorkspaceSaved.markWorkspaceSaved("Draft", "v:editable")).thenReturn(true);

        mockMvc.perform(
                        put("/api/desktop/workspaces/Draft/saved")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"version\":\"v:editable\"}"))
                .andExpect(status().isNoContent());

        verify(markWorkspaceSaved).markWorkspaceSaved("Draft", "v:editable");
    }

    @Test
    void markWorkspaceSaved_noBody_savesTheCurrentVersion() throws Exception {
        when(markWorkspaceSaved.markWorkspaceSaved("Draft", null)).thenReturn(true);

        mockMvc.perform(put("/api/desktop/workspaces/Draft/saved"))
                .andExpect(status().isNoContent());

        verify(markWorkspaceSaved).markWorkspaceSaved("Draft", null);
    }

    @Test
    void markWorkspaceSaved_workspaceBeingWritten_answersConflict() throws Exception {
        when(markWorkspaceSaved.markWorkspaceSaved("Draft", null)).thenReturn(false);

        mockMvc.perform(put("/api/desktop/workspaces/Draft/saved"))
                .andExpect(status().isConflict());
    }

    @Test
    void markWorkspaceSaved_notAVersion_answersBadRequest() throws Exception {
        when(markWorkspaceSaved.markWorkspaceSaved("Draft", "latest"))
                .thenThrow(new IllegalArgumentException("Not a workspace version: latest"));

        mockMvc.perform(
                        put("/api/desktop/workspaces/Draft/saved")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"version\":\"latest\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void requestOpenFile_path_answersTheRequest() throws Exception {
        when(requestOpenFile.requestOpenFile("/data/Draft.rdfa"))
                .thenReturn(new OpenRequestDTO("r1", "Draft.rdfa", "/data/Draft.rdfa"));

        mockMvc.perform(
                        post("/api/desktop/open-requests")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"path\":\"/data/Draft.rdfa\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("r1"));
    }

    @Test
    void requestOpenFile_noPathOrNoWorkspaceFile_answersBadRequest() throws Exception {
        when(requestOpenFile.requestOpenFile(any()))
                .thenThrow(new IllegalArgumentException("Not a workspace file"));

        mockMvc.perform(
                        post("/api/desktop/open-requests")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(
                        post("/api/desktop/open-requests")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"path\":\"/data/notes.txt\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getOpenRequestFile_knownOrUnknown_answersFileOrNotFound() throws Exception {
        when(getOpenRequestFile.getOpenRequestFile("r1"))
                .thenReturn(Optional.of(new OpenRequestFile("Draft.rdfa", new byte[] {'P'})));
        when(getOpenRequestFile.getOpenRequestFile("missing")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/desktop/open-requests/r1"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("Draft.rdfa")));
        mockMvc.perform(get("/api/desktop/open-requests/missing")).andExpect(status().isNotFound());
    }

    @Test
    void markOpenedFromRequest_eachResult_answersItsStatus() throws Exception {
        when(markOpenedFromRequest.markOpenedFromRequest("Draft", "done"))
                .thenReturn(Result.RECORDED);
        when(markOpenedFromRequest.markOpenedFromRequest("Draft", "missing"))
                .thenReturn(Result.UNKNOWN_REQUEST);
        when(markOpenedFromRequest.markOpenedFromRequest("Draft", "busy")).thenReturn(Result.BUSY);

        mockMvc.perform(put("/api/desktop/workspaces/Draft/opened-from/done"))
                .andExpect(status().isNoContent());
        mockMvc.perform(put("/api/desktop/workspaces/Draft/opened-from/missing"))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/desktop/workspaces/Draft/opened-from/busy"))
                .andExpect(status().isConflict());
    }

    @Test
    void listRecoveredWorkspaces_keptWorkspaces_listsThem() throws Exception {
        when(listRecovered.listRecoveredWorkspaces())
                .thenReturn(
                        List.of(
                                new RecoveredWorkspaceDTO(
                                        "Draft-1.rdfa",
                                        "Draft",
                                        Instant.parse("2026-11-24T10:00:00Z"))));

        mockMvc.perform(get("/api/desktop/recovery"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("Draft-1.rdfa"))
                .andExpect(jsonPath("$[0].workspaceName").value("Draft"));
    }

    @Test
    void getRecoveredWorkspace_knownOrUnknown_answersFileOrNotFound() throws Exception {
        when(getRecovered.getRecoveredWorkspace("Draft-1.rdfa"))
                .thenReturn(Optional.of(new byte[] {'P', 'K'}));
        when(getRecovered.getRecoveredWorkspace("missing.rdfa")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/desktop/recovery/Draft-1.rdfa"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/zip"));
        mockMvc.perform(get("/api/desktop/recovery/missing.rdfa")).andExpect(status().isNotFound());
    }

    @Test
    void discardRecoveredWorkspace_knownOrUnknown_answersNoContentOrNotFound() throws Exception {
        when(discardRecovered.discardRecoveredWorkspace("Draft-1.rdfa")).thenReturn(true);
        when(discardRecovered.discardRecoveredWorkspace("missing.rdfa")).thenReturn(false);

        mockMvc.perform(delete("/api/desktop/recovery/Draft-1.rdfa"))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/desktop/recovery/missing.rdfa"))
                .andExpect(status().isNotFound());
    }

    @Test
    void markRestoredFromCopy_eachResult_answersItsStatus() throws Exception {
        when(markRestoredFromCopy.markRestoredFromCopy("Draft", "done.rdfa"))
                .thenReturn(MarkRestoredFromCopyUseCase.Result.REMOVED);
        when(markRestoredFromCopy.markRestoredFromCopy("Draft", "missing.rdfa"))
                .thenReturn(MarkRestoredFromCopyUseCase.Result.UNKNOWN_COPY);
        when(markRestoredFromCopy.markRestoredFromCopy("Draft", "busy.rdfa"))
                .thenReturn(MarkRestoredFromCopyUseCase.Result.STILL_NEEDED);

        mockMvc.perform(put("/api/desktop/workspaces/Draft/restored-from/done.rdfa"))
                .andExpect(status().isNoContent());
        mockMvc.perform(put("/api/desktop/workspaces/Draft/restored-from/missing.rdfa"))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/desktop/workspaces/Draft/restored-from/busy.rdfa"))
                .andExpect(status().isConflict());
    }
}

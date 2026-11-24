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

package org.rdfarchitect.api.dto.desktop;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/** What a window of the desktop app needs to know about the session, sent with each heartbeat. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DesktopStatusDTO {

    /**
     * Names this run of the desktop app. A window that gets another one talks to a backend that was
     * started anew, without the workspaces it showed.
     */
    private String instanceId;

    /** The workspaces holding changes that no file has, sorted by name. */
    private List<String> unsavedWorkspaces;

    /**
     * The workspaces whose file the backend writes itself on Save, because it was handed over by
     * the operating system rather than chosen in the browser. Sorted by name.
     */
    private List<String> workspacesWithFile;

    /**
     * The id of each workspace by its name. An id stays through renames and is never given to
     * another workspace, so a window can tell a workspace from one created anew under its name.
     */
    private Map<String, String> workspaceIds;
}

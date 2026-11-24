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

import org.rdfarchitect.api.dto.desktop.DesktopStatusDTO;

/** Records that a window of the desktop app is still open. */
public interface ReportWindowOpenUseCase {

    /**
     * Records that the window is still open and its session's workspaces are to be kept, and
     * returns what the window needs to know about the session.
     *
     * @param windowId an id the window chose for itself
     * @return the run, the unsaved workspaces and the workspaces with a file
     */
    DesktopStatusDTO reportWindowOpen(String windowId);
}

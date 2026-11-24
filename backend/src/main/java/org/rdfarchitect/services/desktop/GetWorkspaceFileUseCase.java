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

/** Writes a workspace as a workspace file for the desktop app's Save. */
public interface GetWorkspaceFileUseCase {

    /**
     * Writes the workspace as it stands as a workspace file, together with the version it holds.
     *
     * @param workspaceName the workspace, in the current session
     * @return the file
     * @throws org.rdfarchitect.exception.database.ResourceNotFoundException if there is no such
     *     workspace
     */
    WorkspaceFileDownload getWorkspaceFile(String workspaceName);
}

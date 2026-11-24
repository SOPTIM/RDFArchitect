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

/** Records that the browser saved a workspace to a file or opened it from one. */
public interface MarkWorkspaceSavedUseCase {

    /**
     * Records that the workspace was written to a file of the browser's choosing, or read from one,
     * which is the workspace's file from now on.
     *
     * @param workspaceName the workspace, in the current session
     * @param version the version the file holds, as {@link GetWorkspaceFileUseCase} named it, or
     *     {@code null} for the version the workspace stands on now
     * @return whether it was recorded; not while the workspace is being written to and no version
     *     was given
     * @throws IllegalArgumentException if the version is not one {@link GetWorkspaceFileUseCase}
     *     names
     * @throws org.rdfarchitect.exception.database.ResourceNotFoundException if there is no such
     *     workspace and no version was given
     */
    boolean markWorkspaceSaved(String workspaceName, String version);
}

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

/** Saves a workspace to the file on this computer the backend knows for it. */
public interface SaveWorkspaceToFileUseCase {

    /**
     * Writes the workspace to the file it was opened from, one the operating system handed over,
     * and records it as saved.
     *
     * @param workspaceName the workspace, in the current session
     * @return whether the workspace has such a file
     * @throws java.io.UncheckedIOException if the file cannot be written
     */
    boolean saveWorkspaceToFile(String workspaceName);
}

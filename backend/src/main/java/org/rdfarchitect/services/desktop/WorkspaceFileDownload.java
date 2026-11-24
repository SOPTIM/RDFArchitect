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

import java.util.UUID;

/**
 * A workspace as a workspace file.
 *
 * @param content the workspace file
 * @param fileName the name to suggest for it
 * @param version the version of the workspace it holds, to pass to {@link
 *     MarkWorkspaceSavedUseCase} once it is written; {@code null} if it could not be read
 * @param workspaceId the id of the workspace it holds, which stays through renames
 */
public record WorkspaceFileDownload(
        byte[] content, String fileName, String version, UUID workspaceId) {}

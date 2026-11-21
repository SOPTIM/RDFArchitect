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

package org.rdfarchitect.services.archive.importing;

import org.rdfarchitect.services.update.graph.ImportProgressListener;

import java.io.InputStream;
import java.util.List;

public interface ImportWorkspaceUseCase {

    /**
     * @param cancelled whether the import was cancelled before it was committed
     */
    record WorkspaceImportResult(
            List<String> importedGraphUris, List<String> notices, boolean cancelled) {}

    WorkspaceImportResult importWorkspace(
            String workspaceName, InputStream archive, ImportProgressListener listener);
}

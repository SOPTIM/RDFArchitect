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

import org.rdfarchitect.database.WorkspaceTransaction;
import org.rdfarchitect.services.archive.parts.GraphScope;
import org.rdfarchitect.services.archive.parts.ReadPart;

import java.util.List;

/** A workspace file that was read and found valid as a whole, ready to be restored. */
public record WorkspaceArchive(
        boolean readOnly,
        List<ArchivedGraph> graphs,
        List<ReadPart<WorkspaceTransaction, ?>> workspaceParts,
        List<String> notices) {

    public record ArchivedGraph(String uri, List<ReadPart<GraphScope, ?>> parts) {}
}

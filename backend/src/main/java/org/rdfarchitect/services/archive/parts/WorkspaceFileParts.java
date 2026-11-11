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

package org.rdfarchitect.services.archive.parts;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import org.rdfarchitect.database.WorkspaceTransaction;

import java.util.List;

/** The parts of the workspace file, each list in the order the import restores it. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class WorkspaceFileParts {

    /** The schema comes first, since restoring it creates the graph the other parts go into. */
    public static final List<WorkspaceFilePart<GraphScope, ?>> OF_GRAPH =
            List.of(
                    new SchemaPart(),
                    new ShaclPart(),
                    new LayoutPart<>(scope -> scope.graph().getDiagramLayout()),
                    new GraphDiagramsPart());

    /**
     * Restored after every graph. The namespaces come last, because creating a graph adds the
     * prefixes of its schema file.
     */
    public static final List<WorkspaceFilePart<WorkspaceTransaction, ?>> OF_WORKSPACE =
            List.of(
                    new LayoutPart<>(WorkspaceTransaction::layout),
                    new WorkspaceDiagramsPart(),
                    new ColorsPart(),
                    new NamespacesPart());
}
